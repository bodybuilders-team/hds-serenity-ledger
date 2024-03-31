package pt.ulisboa.tecnico.hdsledger.shared.communication;

import com.google.gson.Gson;
import pt.ulisboa.tecnico.hdsledger.shared.CollapsingSet;
import pt.ulisboa.tecnico.hdsledger.shared.SerializationUtils;
import pt.ulisboa.tecnico.hdsledger.shared.communication.Message.Type;
import pt.ulisboa.tecnico.hdsledger.shared.communication.consensus_message.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.shared.communication.ledger_message.LedgerTransferRequest;
import pt.ulisboa.tecnico.hdsledger.shared.communication.ledger_message.SignedLedgerRequest;
import pt.ulisboa.tecnico.hdsledger.shared.config.ClientProcessConfig;
import pt.ulisboa.tecnico.hdsledger.shared.config.NodeProcessConfig;
import pt.ulisboa.tecnico.hdsledger.shared.config.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.shared.crypto.CryptoUtils;
import pt.ulisboa.tecnico.hdsledger.shared.exception.ErrorMessage;
import pt.ulisboa.tecnico.hdsledger.shared.exception.HDSSException;
import pt.ulisboa.tecnico.hdsledger.shared.logger.ProcessLogger;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An authenticated perfect link implementation.
 * Provides reliable delivery, no duplication and authenticity.
 */
public class AuthenticatedPerfectLink {

    private static final boolean ENABLE_ACK_LOGGING = false;
    // Time to wait for an ACK before resending the message
    private static final long BASE_SLEEP_TIME = 1000;
    // UDP Socket
    private final DatagramSocket socket;
    // Map of all nodes in the network
    private final Map<String, ProcessConfig> nodes = new ConcurrentHashMap<>();
    // Reference to the node itself
    private final ProcessConfig config;
    // Set of received messages from specific node (prevent duplicates)
    private final Map<String, CollapsingSet> receivedMessages = new ConcurrentHashMap<>();
    // Set of received ACKs from specific node
    private final CollapsingSet receivedAcks = new CollapsingSet();
    // Message counter
    private final AtomicInteger messageCounter = new AtomicInteger(0);
    // Send messages to self by pushing to queue instead of through the network
    private final Queue<SignedMessage> localhostQueue = new ConcurrentLinkedQueue<>();
    private final KeyPair keyPair;
    private final ProcessLogger logger;

    public AuthenticatedPerfectLink(ProcessConfig self, int port, ProcessConfig[] nodes, boolean activateLogs) {

        this.keyPair = CryptoUtils.readKeyPair(self.getPrivateKeyPath(), self.getPublicKeyPath());
        this.config = self;
        this.logger = new ProcessLogger(AuthenticatedPerfectLink.class.getName(), self.getId());
        if (!activateLogs)
            this.logger.disableLogging();

        Arrays.stream(nodes).forEach(node -> {
            String id = node.getId();
            this.nodes.put(id, node);
            receivedMessages.put(id, new CollapsingSet());
        });

        try {
            this.socket = new DatagramSocket(port, InetAddress.getByName(config.getHostname()));
        } catch (UnknownHostException | SocketException e) {
            throw new HDSSException(ErrorMessage.CANNOT_OPEN_SOCKET);
        }
    }

    /**
     * Acknowledges all messages with the given message IDs
     *
     * @param messageIds The message IDs to acknowledge
     */
    public void ackAll(List<Integer> messageIds) {
        receivedAcks.addAll(messageIds);
    }

    /**
     * Broadcasts a message to all nodes in the network
     *
     * @param data The message to be broadcast
     */
    public void broadcast(Message data) {
        if (data.getType() != Type.ACK)
            data.setMessageId(-1);

        logger.info(MessageFormat.format("Broadcasting {0}", data));

        if (this.config.getBehavior() == ProcessConfig.ProcessBehavior.CORRUPT_BROADCAST) {
            // Send different messages to different nodes (Alter the message)
            nodes.forEach((destId, dest) -> {
                if (data.getType() == Type.TRANSFER) {
                    SignedLedgerRequest signedLedgerRequest = (SignedLedgerRequest) data;
                    LedgerTransferRequest ledgerTransferRequest = (LedgerTransferRequest) signedLedgerRequest.getLedgerRequest();
                    ledgerTransferRequest.setAmount(ledgerTransferRequest.getAmount() + Math.random() * 100);
                }
                send(destId, data);
            });
        } else if (this.config.getBehavior() == ProcessConfig.ProcessBehavior.CORRUPT_LEADER
                && data.getType() == Type.PRE_PREPARE && (((ConsensusMessage) data).getRound() == 1)) {
            ConsensusMessage prePrepareMessage = (ConsensusMessage) data;
            // Send different messages to different nodes (Alter the message)
            nodes.forEach((destId, dest) -> {
                final var block = prePrepareMessage.getValue();
                if (!block.getRequests().isEmpty())
                    block.setRequests(block.getRequests().subList(0, block.getRequests().size() - 1));
                prePrepareMessage.setValue(block);

                send(destId, prePrepareMessage);
            });
        } else
            nodes.forEach((destId, dest) -> send(destId, data));
    }

    /**
     * Sends an already signed message to a specific node with no guarantee of delivery.
     *
     * @param nodeId        The node identifier
     * @param signedMessage The signed message to be sent
     */
    public void sendSignedMessage(String nodeId, SignedMessage signedMessage) {
        final SignedMessage localSignedMessage = signedMessage.deepCopy();

        try {
            ProcessConfig node = nodes.get(nodeId);
            if (node == null)
                throw new HDSSException(ErrorMessage.NO_SUCH_NODE);

            // If the message is not ACK, it will be resent
            InetAddress destAddress = InetAddress.getByName(node.getHostname());

            // If we're a client, we should send messages to the client socket of the blockchain server
            // otherwise, we are a server and should send messages to the server socket of the client or to the server socket of the blockchain server
            int destPort = config instanceof ClientProcessConfig
                    ? ((NodeProcessConfig) node).getClientPort()
                    : node.getPort();

            // Send message to local queue instead of using network if destination in self
            if (nodeId.equals(this.config.getId())) {
                this.localhostQueue.add(localSignedMessage);

                logger.info(MessageFormat.format("Sent {0} to \u001B[33mself (locally)\u001B[37m successfully", localSignedMessage.getMessage()));

                return;
            }

            byte[] dataToSend = SerializationUtils.getGson().toJson(localSignedMessage).getBytes();

            unreliableSend(destAddress, destPort, dataToSend);

            logger.info(MessageFormat.format("Sending {0} to {1}:{2}", localSignedMessage.getMessage(), destAddress, String.valueOf(destPort)));
        } catch (UnknownHostException e) {
            logger.error(MessageFormat.format("Error sending signed message {0} to {1}: {2}", localSignedMessage.getMessage(), nodeId, e.getMessage()));
            e.printStackTrace();
        }
    }

    /**
     * Sends a message to a specific node with guarantee of delivery
     *
     * @param nodeId  The node identifier
     * @param message The message to be sent
     */
    public void send(String nodeId, Message message) {
        final Message localMessage = message.deepCopy();
        // Spawn a new thread to send the message
        // To avoid blocking while waiting for ACK
        new Thread(() -> {
            try {
                ProcessConfig node = nodes.get(nodeId);
                if (node == null)
                    throw new HDSSException(ErrorMessage.NO_SUCH_NODE);

                // If the message is not ACK, it will be resent
                InetAddress destAddress = InetAddress.getByName(node.getHostname());

                // If we're a client, we should send messages to the client socket of the blockchain server
                // otherwise, we are a server and should send messages to the server socket of the client or to the server socket of the blockchain server
                int destPort = config instanceof ClientProcessConfig
                        ? ((NodeProcessConfig) node).getClientPort()
                        : node.getPort();

                if (localMessage.getType() != Type.ACK)
                    localMessage.setMessageId(messageCounter.getAndIncrement());
                int messageId = localMessage.getMessageId();

                int count = 1;
                long sleepTime = BASE_SLEEP_TIME;

                byte[] signature = CryptoUtils.sign(localMessage, keyPair.getPrivate());
                SignedMessage signedMessage = new SignedMessage(localMessage, signature);

                byte[] dataToSend = SerializationUtils.getGson().toJson(signedMessage).getBytes();

                // Send message to local queue instead of using network if destination in self
                if (nodeId.equals(this.config.getId())) {
                    this.localhostQueue.add(signedMessage);

                    logger.info(MessageFormat.format("Sent {0} to \u001B[33mself (locally)\u001B[37m successfully", localMessage));

                    return;
                }

                for (; ; ) {
                    logger.info(MessageFormat.format("Sending {0} to {1}:{2} - \u001B[36mAttempt #{3}\u001B[37m", localMessage, destAddress, String.valueOf(destPort), count++));

                    unreliableSend(destAddress, destPort, dataToSend);

                    // Wait (using exponential back-off), then look for ACK
                    Thread.sleep(sleepTime);

                    // Receive method will set receivedAcks when sees corresponding ACK
                    if (receivedAcks.contains(messageId))
                        break;

                    sleepTime <<= 1;
                }

                logger.info(MessageFormat.format("Message {0} received by {1}:{2} successfully", localMessage, destAddress, String.valueOf(destPort)));
            } catch (InterruptedException | UnknownHostException e) {
                logger.error(MessageFormat.format("Error sending message {0} to {1}: {2}", message, nodeId, e.getMessage()));
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * Sends a message to a specific node without guarantee of delivery.
     *
     * @param hostname The hostname of the destination node
     * @param port     The port of the destination node
     * @param data     The data to be sent
     */
    public void unreliableSend(InetAddress hostname, int port, byte[] data) {
        new Thread(() -> {
            try {
                socket.send(new DatagramPacket(data, data.length, hostname, port));
            } catch (IOException e) {
                throw new HDSSException(ErrorMessage.SOCKET_SENDING_ERROR);
            }
        }).start();
    }

    /**
     * Receives a message from any node in the network (blocking).
     *
     * @return The received message
     */
    public SignedMessage receive() throws IOException {
        SignedMessage signedMessage;
        Message message;
        boolean local = false;
        DatagramPacket response = null;
        Gson gson = SerializationUtils.getGson();

        if (!this.localhostQueue.isEmpty()) {
            signedMessage = this.localhostQueue.poll();
            message = signedMessage.getMessage();
            local = true;
            this.receivedAcks.add(message.getMessageId());
        } else {
            byte[] buf = new byte[65536];
            response = new DatagramPacket(buf, buf.length);
            socket.receive(response);

            byte[] buffer = Arrays.copyOfRange(response.getData(), 0, response.getLength());
            signedMessage = SerializationUtils.deserialize(buffer, SignedMessage.class);
            message = signedMessage.getMessage();
        }

        String senderId = message.getSenderId();
        int messageId = message.getMessageId();

        if (!nodes.containsKey(senderId))
            throw new HDSSException(ErrorMessage.NO_SUCH_NODE);

        // Validate signature
        if (signedMessage != null) {
            PublicKey publicKey = CryptoUtils.getPublicKey(nodes.get(senderId).getPublicKeyPath());

            var data = SerializationUtils.serializeToBytes(signedMessage.getMessage());

            boolean validSignature = CryptoUtils.verify(data, signedMessage.getSignature(), publicKey);
            if (!validSignature) {
                if (response == null)
                    logger.error(MessageFormat.format("Invalid signature for message {0} from \u001B[33mself (locally)\u001B[37m", message));
                else
                    logger.error(MessageFormat.format("Invalid signature for message {0} from {1}:{2}", message, response.getAddress(), String.valueOf(response.getPort())));
                throw new HDSSException(ErrorMessage.INVALID_SIGNATURE_ERROR);
            }
        }

        if (message.getType() != Type.ACK || ENABLE_ACK_LOGGING) {
            if (response == null)
                logger.info(MessageFormat.format("Received {0} from \u001B[33mself (locally)\u001B[37m", message));
            else
                logger.info(MessageFormat.format("Received {0} from {1}:{2}", message, response.getAddress(), String.valueOf(response.getPort())));
        }

        // Handle ACKS, since it's possible to receive multiple acks from the same message
        if (message.getType().equals(Type.ACK)) {
            receivedAcks.add(messageId);
            return signedMessage;
        }

        // Message already received (add returns false if already exists) => Discard
        boolean isRepeated = !receivedMessages.get(message.getSenderId()).add(messageId);
        if (isRepeated)
            message.setType(Type.IGNORE);

        switch (message.getType()) {
            case PREPARE, COMMIT -> {
                ConsensusMessage consensusMessage = (ConsensusMessage) message;
                if (consensusMessage.getReplyTo() != null && consensusMessage.getReplyTo().equals(config.getId()))
                    receivedAcks.add(consensusMessage.getReplyToMessageId());
            }
            default -> {
                // Do nothing
            }
        }

        // Send ACK to the sender if the message was not local and is not ignored

        if (!local) {
            InetAddress address = InetAddress.getByName(response.getAddress().getHostAddress());
            int port = response.getPort();

            Message responseMessage = new Message(this.config.getId(), Type.ACK);
            responseMessage.setMessageId(messageId);

            // ACK is sent without needing for another ACK because
            // we're assuming an eventually synchronous network
            // Even if a node receives the message multiple times,
            // it will discard duplicates

            if (ENABLE_ACK_LOGGING)
                logger.info(MessageFormat.format("Sending {0} to {1}:{2}", responseMessage, address, String.valueOf(port)));

            byte[] signature = CryptoUtils.sign(responseMessage, keyPair.getPrivate());
            SignedMessage signedResponseMessage = new SignedMessage(responseMessage, signature);
            byte[] dataToSend = gson.toJson(signedResponseMessage).getBytes();

            unreliableSend(address, port, dataToSend);
        }

        return signedMessage;
    }
}