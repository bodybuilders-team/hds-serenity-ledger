package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;
import pt.ulisboa.tecnico.hdsledger.communication.Message.Type;
import pt.ulisboa.tecnico.hdsledger.crypto.CryptoUtils;
import pt.ulisboa.tecnico.hdsledger.utilities.CollapsingSet;
import pt.ulisboa.tecnico.hdsledger.utilities.ErrorMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.HDSSException;
import pt.ulisboa.tecnico.hdsledger.utilities.NodeLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.config.ProcessConfig;

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
import java.util.logging.LogManager;

/**
 * An authenticated perfect link implementation.
 * Provides reliable delivery, no duplication and authenticity.
 */
public class AuthenticatedPerfectLink {

    private final NodeLogger LOGGER;

    // Time to wait for an ACK before resending the message
    private final long BASE_SLEEP_TIME;
    private final boolean sendToClientSocket;
    // UDP Socket
    private final DatagramSocket socket;
    // Map of all nodes in the network
    private final Map<String, ProcessConfig> nodes = new ConcurrentHashMap<>();
    // Reference to the node itself
    private final ProcessConfig config;
    // Class to deserialize messages to
    private final Class<? extends Message> messageClass;
    // Set of received messages from specific node (prevent duplicates)
    private final Map<String, CollapsingSet> receivedMessages = new ConcurrentHashMap<>();
    // Set of received ACKs from specific node
    private final CollapsingSet receivedAcks = new CollapsingSet();
    // Message counter
    private final AtomicInteger messageCounter = new AtomicInteger(0);
    // Send messages to self by pushing to queue instead of through the network
    private final Queue<Message> localhostQueue = new ConcurrentLinkedQueue<>();
    private final KeyPair keyPair;

    public AuthenticatedPerfectLink(
            ProcessConfig self, int port, ProcessConfig[] nodes,
            Class<? extends Message> messageClass
    ) {
        this(self, port, nodes, messageClass, false, 200, false);
    }

    public AuthenticatedPerfectLink(ProcessConfig self, int port, ProcessConfig[] nodes, Class<? extends Message> messageClass,
                                    boolean activateLogs, int baseSleepTime, boolean sendToClientSocket) {

        this.keyPair = CryptoUtils.readKeyPair(self.getPrivateKeyPath(), self.getPublicKeyPath());
        this.config = self;
        this.messageClass = messageClass;
        this.BASE_SLEEP_TIME = baseSleepTime;
        this.sendToClientSocket = sendToClientSocket;
        this.LOGGER = new NodeLogger(AuthenticatedPerfectLink.class.getName(), self.getId());

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
        if (!activateLogs) {
            LogManager.getLogManager().reset();
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
        Gson gson = new Gson();

        if (this.config.getBehavior() == ProcessConfig.ProcessBehavior.CORRUPT_BROADCAST) {
            // Send different messages to different nodes (Alter the message)
            nodes.forEach((destId, dest) -> {
                Message message = gson.fromJson(gson.toJson(data), data.getClass());
                message.setMessageId((int) (Math.random() * nodes.size()));
                send(destId, message);
            });
        } else if (this.config.getBehavior() == ProcessConfig.ProcessBehavior.CORRUPT_LEADER
                && data.getType() == Type.PRE_PREPARE && (((ConsensusMessage) data).getRound() == 1)) {
            ConsensusMessage prePrepareMessage = (ConsensusMessage) data;
            // Send different messages to different nodes (Alter the message)
            nodes.forEach((destId, dest) -> {
                prePrepareMessage.setMessage(new PrePrepareMessage(String.format("Corrupted Value, ci: %d destId: %s", prePrepareMessage.getConsensusInstance(), destId)).toJson());

                send(destId, prePrepareMessage);
            });
        } else
            nodes.forEach((destId, dest) -> send(destId, gson.fromJson(gson.toJson(data), data.getClass())));
    }

    /**
     * Sends a message to a specific node with guarantee of delivery
     *
     * @param nodeId The node identifier
     * @param data   The message to be sent
     */
    public void send(String nodeId, Message data) {

        // Spawn a new thread to send the message
        // To avoid blocking while waiting for ACK
        new Thread(() -> {
            try {
                ProcessConfig node = nodes.get(nodeId);
                if (node == null)
                    throw new HDSSException(ErrorMessage.NoSuchNode);

                data.setMessageId(messageCounter.getAndIncrement());

                // If the message is not ACK, it will be resent
                InetAddress destAddress = InetAddress.getByName(node.getHostname());

                int destPort = sendToClientSocket ? node.getClientPort() : node.getPort();
                int count = 1;
                int messageId = data.getMessageId();
                long sleepTime = BASE_SLEEP_TIME;

                // Send message to local queue instead of using network if destination in self
                if (nodeId.equals(this.config.getId())) {
                    this.localhostQueue.add(data);

                    LOGGER.info(
                            MessageFormat.format("Message {0} (locally) with message ID {1} sent to {2}:{3} successfully",
                                    data.getType(), messageId, destAddress, String.valueOf(destPort)));

                    return;
                }

                for (; ; ) {
                    LOGGER.info(MessageFormat.format(
                            "Sending {0} message to {1}:{2} with message ID {3} - Attempt #{4}",
                            data.getType(), destAddress, String.valueOf(destPort), messageId, count++));

                    unreliableSend(destAddress, destPort, data);

                    // Wait (using exponential back-off), then look for ACK
                    Thread.sleep(sleepTime);

                    // Receive method will set receivedAcks when sees corresponding ACK
                    if (receivedAcks.contains(messageId))
                        break;

                    sleepTime <<= 1;
                }

                LOGGER.info(MessageFormat.format("Message {0} sent to {1}:{2} successfully",
                        data.getType(), destAddress, String.valueOf(destPort)));
            } catch (InterruptedException | UnknownHostException e) {
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * Sends a message to a specific node without guarantee of delivery.
     * Mainly used to send ACKs, if they are lost, the original message will be resent.
     *
     * @param hostname The hostname of the destination node
     * @param port     The port of the destination node
     * @param message  The message to be sent
     */
    public void unreliableSend(InetAddress hostname, int port, Message message) {
        new Thread(() -> {
            try {
                byte[] messageBuf = new Gson().toJson(message).getBytes();
                byte[] signature = CryptoUtils.sign(messageBuf, keyPair.getPrivate());
                SignedPacket signedPacket = new SignedPacket(messageBuf, signature);
                byte[] buf = new Gson().toJson(signedPacket).getBytes();
                DatagramPacket packet = new DatagramPacket(buf, buf.length, hostname, port);
                socket.send(packet);
            } catch (IOException e) {
                e.printStackTrace();
                throw new HDSSException(ErrorMessage.SocketSendingError);
            }
        }).start();
    }

    /**
     * Receives a message from any node in the network (blocking).
     *
     * @return The received message
     */
    public Message receive() throws IOException {
        Message message = null;
        SignedPacket signedPacket = null;
        String serializedMessage = "";
        Boolean local = false;
        DatagramPacket response = null;

        if (!this.localhostQueue.isEmpty()) {
            message = this.localhostQueue.poll();
            local = true;
            this.receivedAcks.add(message.getMessageId());
        } else {
            byte[] buf = new byte[65535];
            response = new DatagramPacket(buf, buf.length);
            socket.receive(response);

            byte[] buffer = Arrays.copyOfRange(response.getData(), 0, response.getLength());
            String serializedSignedPacket = new String(buffer);
            signedPacket = new Gson().fromJson(serializedSignedPacket, SignedPacket.class);
            serializedMessage = new String(signedPacket.getMessage());
            message = new Gson().fromJson(serializedMessage, Message.class);
        }

        String senderId = message.getSenderId();
        int messageId = message.getMessageId();

        if (!nodes.containsKey(senderId))
            throw new HDSSException(ErrorMessage.NoSuchNode);

        if (response == null)
            LOGGER.info(MessageFormat.format("Received {0} message from self with message ID {1}",
                    message.getType(), messageId));
        else
            LOGGER.info(MessageFormat.format("Received {0} message from {1}:{2} with message ID {3}",
                    message.getType(), response.getAddress(), String.valueOf(response.getPort()), messageId));

        // Validate signature
        if (signedPacket != null) {
            PublicKey publicKey = CryptoUtils.getPublicKey(nodes.get(senderId).getPublicKeyPath());

            boolean validSignature = CryptoUtils.verify(signedPacket.getMessage(), signedPacket.getSignature(), publicKey);
            if (!validSignature) {
                throw new HDSSException(ErrorMessage.InvalidSignatureError);
            }
        }

        // Handle ACKS, since it's possible to receive multiple acks from the same message
        if (message.getType().equals(Message.Type.ACK)) {
            receivedAcks.add(messageId);
            return message;
        }
        // It's not an ACK -> Deserialize for the correct type
        if (!local)
            message = new Gson().fromJson(serializedMessage, this.messageClass);
        boolean isRepeated = !receivedMessages.get(message.getSenderId()).add(messageId);
        Type originalType = message.getType();
        // Message already received (add returns false if already exists) => Discard
        if (isRepeated) {
            message.setType(Message.Type.IGNORE);
        }

        switch (message.getType()) {
            case PRE_PREPARE -> {
                return message;
            }
            case IGNORE -> {
                if (!originalType.equals(Type.COMMIT))
                    return message;
            }
            case PREPARE -> {
                ConsensusMessage consensusMessage = (ConsensusMessage) message;
                if (consensusMessage.getReplyTo() != null && consensusMessage.getReplyTo().equals(config.getId()))
                    receivedAcks.add(consensusMessage.getReplyToMessageId());

                return message;
            }
            case COMMIT -> {
                ConsensusMessage consensusMessage = (ConsensusMessage) message;
                if (consensusMessage.getReplyTo() != null && consensusMessage.getReplyTo().equals(config.getId()))
                    receivedAcks.add(consensusMessage.getReplyToMessageId());
            }
            default -> {
            }
        }

        // Send ACK to the sender if the message was not local and is not ignored

        if (!local) {
            InetAddress address = InetAddress.getByName(response.getAddress().getHostAddress());
            int port = response.getPort();

            Message responseMessage = new Message(this.config.getId(), Message.Type.ACK);
            responseMessage.setMessageId(messageId);

            // ACK is sent without needing for another ACK because
            // we're assuming an eventually synchronous network
            // Even if a node receives the message multiple times,
            // it will discard duplicates

            LOGGER.info(MessageFormat.format(
                    "Sending {0} message to {1}:{2} with message ID {3}",
                    Type.ACK, address, String.valueOf(port), messageId));

            unreliableSend(address, port, responseMessage);
        }

        return message;
    }
}
