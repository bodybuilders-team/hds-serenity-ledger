package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;
import pt.ulisboa.tecnico.hdsledger.shared.CollapsingSet;
import pt.ulisboa.tecnico.hdsledger.shared.ErrorMessage;
import pt.ulisboa.tecnico.hdsledger.shared.HDSSException;
import pt.ulisboa.tecnico.hdsledger.shared.ProcessLogger;
import pt.ulisboa.tecnico.hdsledger.shared.SerializationUtils;
import pt.ulisboa.tecnico.hdsledger.shared.communication.Message;
import pt.ulisboa.tecnico.hdsledger.shared.communication.Message.Type;
import pt.ulisboa.tecnico.hdsledger.shared.communication.SignedMessage;
import pt.ulisboa.tecnico.hdsledger.shared.communication.SignedPacket;
import pt.ulisboa.tecnico.hdsledger.shared.communication.consensus_message.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.shared.config.ClientProcessConfig;
import pt.ulisboa.tecnico.hdsledger.shared.config.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.shared.config.ServerProcessConfig;
import pt.ulisboa.tecnico.hdsledger.shared.crypto.CryptoUtils;
import pt.ulisboa.tecnico.hdsledger.shared.models.Block;

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
    private final long BASE_SLEEP_TIME;
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

    public AuthenticatedPerfectLink(
            ProcessConfig self, int port, ProcessConfig[] nodes, boolean activateLogs
    ) {
        this(self, port, nodes, activateLogs, 200);
    }

    public AuthenticatedPerfectLink(ProcessConfig self, int port, ProcessConfig[] nodes,
                                    boolean activateLogs, int baseSleepTime) {

        this.keyPair = CryptoUtils.readKeyPair(self.getPrivateKeyPath(), self.getPublicKeyPath());
        this.config = self;
        this.BASE_SLEEP_TIME = baseSleepTime;
        this.logger = new ProcessLogger(AuthenticatedPerfectLink.class.getName(), self.getId());
        if (!activateLogs) {
            this.logger.disableLogging();
        }

        Arrays.stream(nodes).forEach(node -> {
            String id = node.getId();
            this.nodes.put(id, node);
            receivedMessages.put(id, new CollapsingSet());
        });

        try {
            this.socket = new DatagramSocket(port, InetAddress.getByName(config.getHostname()));
        } catch (UnknownHostException | SocketException e) {
            throw new HDSSException(ErrorMessage.CannotOpenSocket);
        }
    }

    public void ackAll(List<Integer> messageIds) {
        receivedAcks.addAll(messageIds);
    }

    /**
     * Broadcasts a message to all nodes in the network
     *
     * @param data The message to be broadcast
     */
    public void broadcast(Message data) {
        Gson gson = SerializationUtils.getGson();

        logger.info(MessageFormat.format("Broadcasting {0}", data));

        if (this.config.getBehavior() == ProcessConfig.ProcessBehavior.CORRUPT_BROADCAST) {
            // Send different messages to different nodes (Alter the message)
            nodes.forEach((destId, dest) -> {
                data.setMessageId((int) (Math.random() * nodes.size()));
                send(destId, data);
            });
        } else if (this.config.getBehavior() == ProcessConfig.ProcessBehavior.CORRUPT_LEADER
                && data.getType() == Type.PRE_PREPARE && (((ConsensusMessage) data).getRound() == 1)) {
            ConsensusMessage prePrepareMessage = (ConsensusMessage) data;
            // Send different messages to different nodes (Alter the message)
            nodes.forEach((destId, dest) -> {
                final var block = (Block) prePrepareMessage.getValue();

                block.setConsensusInstance((int) (Math.random() * nodes.size()));

                prePrepareMessage.setValue(block);

                send(destId, prePrepareMessage);
            });
        } else
            nodes.forEach((destId, dest) -> send(destId, data));
    }

    /**
     * Sends a message to a specific node with guarantee of delivery
     *
     * @param nodeId  The node identifier
     * @param message The message to be sent
     */
    public void send(String nodeId, Message message) {
        // Spawn a new thread to send the message
        // To avoid blocking while waiting for ACK
        new Thread(() -> {
            try {
                ProcessConfig node = nodes.get(nodeId);
                if (node == null)
                    throw new HDSSException(ErrorMessage.NoSuchNode);

                if (message.getType() != Type.ACK)
                    message.setMessageId(messageCounter.getAndIncrement());

                // If the message is not ACK, it will be resent
                InetAddress destAddress = InetAddress.getByName(node.getHostname());

                // If we're a client, we should send messages to the client socket of the blockchain server
                // otherwise, we are a server and should send messages to the server socket of the client or to the server socket of the blockchain server
                int destPort = config instanceof ClientProcessConfig
                        ? ((ServerProcessConfig) node).getClientPort()
                        : node.getPort();

                int count = 1;
                int messageId = message.getMessageId();
                long sleepTime = BASE_SLEEP_TIME;

                byte[] signature = CryptoUtils.sign(message, keyPair.getPrivate());
                SignedMessage signedMessage = new SignedMessage(message, signature);
                byte[] dataToSend = SerializationUtils.getGson().toJson(signedMessage).getBytes();

                // Send message to local queue instead of using network if destination in self
                if (nodeId.equals(this.config.getId())) {
                    this.localhostQueue.add(signedMessage);

                    logger.info(MessageFormat.format("Sent {0} to \u001B[33mself (locally)\u001B[37m with message ID {1} successfully", message, messageId));

                    return;
                }

                for (; ; ) {
                    logger.info(MessageFormat.format("Sending {0} to {1}:{2} with message ID {3} - \u001B[36mAttempt #{4}\u001B[37m", message, destAddress, String.valueOf(destPort), messageId, count++));

                    unreliableSend(destAddress, destPort, dataToSend);

                    // Wait (using exponential back-off), then look for ACK
                    Thread.sleep(sleepTime);

                    // Receive method will set receivedAcks when sees corresponding ACK
                    if (receivedAcks.contains(messageId))
                        break;

                    sleepTime <<= 1;
                }

                logger.info(MessageFormat.format("Message {0} received by {1}:{2} successfully", message, destAddress, String.valueOf(destPort)));
            } catch (InterruptedException | UnknownHostException e) {
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
                throw new HDSSException(ErrorMessage.SocketSendingError);
            }
        }).start();
    }

    /**
     * Receives a message from any node in the network (blocking).
     *
     * @return The received message
     */
    public SignedMessage receive() throws IOException {
        SignedMessage signedMessage = null;
        Message message;
        SignedPacket signedPacket = null;
        String serializedMessage = "";
        boolean local = false;
        DatagramPacket response = null;
        Gson gson = SerializationUtils.getGson();

        if (!this.localhostQueue.isEmpty()) {
            signedMessage = this.localhostQueue.poll();
            message = signedMessage.getMessage();
            local = true;
            this.receivedAcks.add(signedMessage.getMessage().getMessageId());
        } else {
            byte[] buf = new byte[65536];
            response = new DatagramPacket(buf, buf.length);
            socket.receive(response);

            byte[] buffer = Arrays.copyOfRange(response.getData(), 0, response.getLength());
            signedPacket = SerializationUtils.deserialize(buffer, SignedPacket.class);
            serializedMessage = new String(signedPacket.getData());
            message = gson.fromJson(serializedMessage, Message.class);
        }

        String senderId = message.getSenderId();
        int messageId = message.getMessageId();

        if (!nodes.containsKey(senderId))
            throw new HDSSException(ErrorMessage.NoSuchNode);

        // Check if the message is a client request using the type.isClientResponse()...
        final var messageClass = message.getType().getClassType();

        // Validate signature
        if (signedPacket != null) {
            PublicKey publicKey = CryptoUtils.getPublicKey(nodes.get(senderId).getPublicKeyPath());

            boolean validSignature = CryptoUtils.verify(signedPacket.getData(), signedPacket.getSignature(), publicKey);
            if (!validSignature) {
                throw new HDSSException(ErrorMessage.InvalidSignatureError);
            }
        }

        // It's not an ACK -> Deserialize for the correct type
        if (!local && message.getType().equals(Type.ACK)) {
            message = gson.fromJson(serializedMessage, messageClass);
        }

        if (message.getType() != Type.ACK || ENABLE_ACK_LOGGING) {
            if (response == null)
                logger.info(MessageFormat.format("Received {0} from \u001B[33mself (locally)\u001B[37m with message ID {1}",
                        message, messageId));

            else
                logger.info(MessageFormat.format("Received {0} from {1}:{2} with message ID {3}",
                        message, response.getAddress(), String.valueOf(response.getPort()), messageId));
        }

        // Handle ACKS, since it's possible to receive multiple acks from the same message
        if (message.getType().equals(Type.ACK)) {
            receivedAcks.add(messageId);
            return signedMessage;
        }

        Type originalType = message.getType();

        // Message already received (add returns false if already exists) => Discard
        boolean isRepeated = !receivedMessages.get(message.getSenderId()).add(messageId);
        if (isRepeated) {
            message.setType(Type.IGNORE);
        }

        switch (message.getType()) {
            case PRE_PREPARE -> {
                return signedMessage;
            }
            case IGNORE -> {
                if (!originalType.equals(Type.COMMIT) && !originalType.equals(Type.ROUND_CHANGE)) {
                    logger.info(MessageFormat.format("\u001B[31mIGNORING\u001B[37m message with ID {0} from node {1}",
                            message.getMessageId(), message.getSenderId()));
                    return signedMessage;
                }
            }
            case PREPARE -> {
                ConsensusMessage consensusMessage = (ConsensusMessage) message;
                if (consensusMessage.getReplyTo() != null && consensusMessage.getReplyTo().equals(config.getId()))
                    receivedAcks.add(consensusMessage.getReplyToMessageId());

                return signedMessage;
            }
            case COMMIT -> {
                ConsensusMessage consensusMessageDto = (ConsensusMessage) message;
                if (consensusMessageDto.getReplyTo() != null && consensusMessageDto.getReplyTo().equals(config.getId()))
                    receivedAcks.add(consensusMessageDto.getReplyToMessageId());
            }
            default -> {
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
                logger.info(MessageFormat.format(
                        "Sending {0} to {1}:{2} with message ID {3}",
                        responseMessage, address, String.valueOf(port), messageId));

            byte[] signature = CryptoUtils.sign(responseMessage, keyPair.getPrivate());
            SignedMessage signedResponseMessage = new SignedMessage(responseMessage, signature);
            byte[] dataToSend = gson.toJson(signedResponseMessage).getBytes();

            unreliableSend(address, port, dataToSend);
        }

        return signedMessage;
    }
}