package pt.ulisboa.tecnico.hdsledger.service.services;

import pt.ulisboa.tecnico.hdsledger.communication.*;
import pt.ulisboa.tecnico.hdsledger.communication.builder.ConsensusMessageBuilder;
import pt.ulisboa.tecnico.hdsledger.service.models.InstanceInfo;
import pt.ulisboa.tecnico.hdsledger.service.models.MessageBucket;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.config.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.config.ServerProcessConfig;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service to handle consensus instances and ledger.
 */
public class NodeService implements UDPService {

    private static final CustomLogger LOGGER = new CustomLogger(NodeService.class.getName());

    private final ServerProcessConfig[] nodesConfig; // All nodes configuration
    private final ServerProcessConfig config; // Current node configuration
    private final ServerProcessConfig leaderConfig; // Leader configuration

    // Link to communicate with nodes
    private final AuthenticatedPerfectLink authenticatedPerfectLink;

    // Consensus instance -> Round -> List of prepare messages
    private final MessageBucket prepareMessages;
    // Consensus instance -> Round -> List of commit messages
    private final MessageBucket commitMessages;

    // Store if already received pre-prepare for a given <consensus, round>
    private final Map<Integer, Map<Integer, Boolean>> receivedPrePrepare = new ConcurrentHashMap<>();
    // Consensus instance information per consensus instance
    private final Map<Integer, InstanceInfo> instanceInfo = new ConcurrentHashMap<>();
    // Current consensus instance
    private final AtomicInteger consensusInstance = new AtomicInteger(0);
    // Last decided consensus instance
    private final AtomicInteger lastDecidedConsensusInstance = new AtomicInteger(0);

    // Ledger (for now, just a list of strings)
    private ArrayList<String> ledger = new ArrayList<String>();

    public NodeService(AuthenticatedPerfectLink authenticatedPerfectLink, ServerProcessConfig config, ServerProcessConfig leaderConfig, ServerProcessConfig[] nodesConfig) {
        this.authenticatedPerfectLink = authenticatedPerfectLink;
        this.config = config;
        this.leaderConfig = leaderConfig;
        this.nodesConfig = nodesConfig;

        this.prepareMessages = new MessageBucket(nodesConfig.length);
        this.commitMessages = new MessageBucket(nodesConfig.length);
    }

    public ProcessConfig getConfig() {
        return this.config;
    }

    public int getConsensusInstance() {
        return this.consensusInstance.get();
    }

    public ArrayList<String> getLedger() {
        synchronized (ledger) {
            return this.ledger;
        }
    }

    private boolean isLeader(String id) {
        return this.leaderConfig.getId().equals(id);
    }

    /**
     * Create a consensus message.
     *
     * @param value    Value to be agreed upon
     * @param instance Consensus instance
     * @param round    Consensus round
     * @return Consensus message
     */
    public ConsensusMessage createConsensusMessage(String value, int instance, int round) {
        PrePrepareMessage prePrepareMessage = new PrePrepareMessage(value);

        return new ConsensusMessageBuilder(config.getId(), Message.Type.PRE_PREPARE)
                .setConsensusInstance(instance)
                .setRound(round)
                .setMessage(prePrepareMessage.toJson())
                .build();
    }

    /**
     * Start an instance of consensus for a value.
     * Only the current leader will start a consensus instance the remaining nodes only update values.
     *
     * @param inputValue Value to value agreed upon
     */
    public void startConsensus(String inputValue) {
        // Set initial consensus values
        int localConsensusInstance = this.consensusInstance.incrementAndGet();
        InstanceInfo existingConsensus = this.instanceInfo.put(localConsensusInstance, new InstanceInfo(inputValue));

        // If startConsensus was already called for a given round
        if (existingConsensus != null) {
            LOGGER.info(MessageFormat.format("{0} - Node already started consensus for instance {1}",
                    config.getId(), localConsensusInstance));
            return;
        }

        // Only start a consensus instance if the last one was decided
        // We need to be sure that the previous value has been decided
        while (lastDecidedConsensusInstance.get() < localConsensusInstance - 1) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // Leader broadcasts PRE-PREPARE message
        if (this.config.isLeader() || this.config.getBehavior() == ProcessConfig.ProcessBehavior.NON_LEADER_CONSENSUS_INITIATION) {
            InstanceInfo instance = this.instanceInfo.get(localConsensusInstance);
            LOGGER.info(MessageFormat.format("{0} - Node is leader, sending PRE-PREPARE message", config.getId()));
            this.authenticatedPerfectLink.broadcast(this.createConsensusMessage(inputValue, localConsensusInstance, instance.getCurrentRound()));
        } else if (this.config.getBehavior() == ProcessConfig.ProcessBehavior.LEADER_IMPERSONATION) {
            InstanceInfo instance = this.instanceInfo.get(localConsensusInstance);
            LOGGER.info(MessageFormat.format("{0} - Node is not leader, sending PRE-PREPARE message with leader ID", config.getId()));
            PrePrepareMessage prePrepareMessage = new PrePrepareMessage(inputValue);

            var message = new ConsensusMessageBuilder(leaderConfig.getId(), Message.Type.PRE_PREPARE)
                    .setConsensusInstance(localConsensusInstance)
                    .setRound(instance.getCurrentRound())
                    .setMessage(prePrepareMessage.toJson())
                    .build();

            this.authenticatedPerfectLink.broadcast(message);
        } else {
            LOGGER.info(MessageFormat.format("{0} - Node is not leader, waiting for PRE-PREPARE message", config.getId()));
        }
    }

    /**
     * Handle pre-prepare messages and if the message came from leader and is justified them broadcast prepare.
     *
     * @param message Message to be handled
     */
    public void uponPrePrepare(ConsensusMessage message) {
        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();
        String senderId = message.getSenderId();
        int senderMessageId = message.getMessageId();

        PrePrepareMessage prePrepareMessage = message.deserializePrePrepareMessage();

        String value = prePrepareMessage.getValue();

        LOGGER.info(
                MessageFormat.format(
                        "{0} - Received PRE-PREPARE message from node {1}: Consensus Instance {2}, Round {3}",
                        config.getId(), senderId, consensusInstance, round));

        // Verify if pre-prepare was sent by leader
        if (!isLeader(senderId))
            return;

        // Set instance value
        this.instanceInfo.putIfAbsent(consensusInstance, new InstanceInfo(value));

        // Within an instance of the algorithm, each upon rule is triggered at most once
        // for any round r
        receivedPrePrepare.putIfAbsent(consensusInstance, new ConcurrentHashMap<>());
        if (receivedPrePrepare.get(consensusInstance).put(round, true) != null) {
            LOGGER.info(
                    MessageFormat.format(
                            "{0} - Already received PRE-PREPARE message for Consensus Instance {1}, Round {2}, "
                                    + "replying again to make sure it reaches the initial sender",
                            config.getId(), consensusInstance, round));
        }

        PrepareMessage prepareMessage = new PrepareMessage(prePrepareMessage.getValue());

        ConsensusMessage consensusMessage = new ConsensusMessageBuilder(config.getId(), Message.Type.PREPARE)
                .setConsensusInstance(consensusInstance)
                .setRound(round)
                .setMessage(prepareMessage.toJson())
                .setReplyTo(senderId)
                .setReplyToMessageId(senderMessageId)
                .build();

        this.authenticatedPerfectLink.broadcast(consensusMessage);
    }

    /**
     * Handle prepare messages and if there is a valid quorum broadcast commit.
     *
     * @param message Message to be handled
     */
    public synchronized void uponPrepare(ConsensusMessage message) {
        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();
        String senderId = message.getSenderId();

        PrepareMessage prepareMessage = message.deserializePrepareMessage();

        String value = prepareMessage.getValue();

        LOGGER.info(
                MessageFormat.format(
                        "{0} - Received PREPARE message from node {1}: Consensus Instance {2}, Round {3}",
                        config.getId(), senderId, consensusInstance, round));

        // Doesn't add duplicate messages
        prepareMessages.addMessage(message);

        // Set instance values
        this.instanceInfo.putIfAbsent(consensusInstance, new InstanceInfo(value)); // TODO: Check if we should trust the value coming from prepare message for input value (specially for round change)
        InstanceInfo instance = this.instanceInfo.get(consensusInstance);

        // Within an instance of the algorithm, each upon rule is triggered at most once
        // for any round r
        // Late prepare (consensus already ended for other nodes) only reply to him (as
        // an ACK)
        if (instance.getPreparedRound() >= round) {
            LOGGER.info(
                    MessageFormat.format(
                            "{0} - Already received PREPARE message for Consensus Instance {1}, Round {2}, "
                                    + "replying again to make sure it reaches the initial sender",
                            config.getId(), consensusInstance, round));

            ConsensusMessage m = new ConsensusMessageBuilder(config.getId(), Message.Type.COMMIT)
                    .setConsensusInstance(consensusInstance)
                    .setRound(round)
                    .setReplyTo(senderId)
                    .setReplyToMessageId(message.getMessageId())
                    .setMessage(instance.getCommitMessage().toJson())
                    .build();

            authenticatedPerfectLink.send(senderId, m);
            return;
        }

        // Find value with valid quorum
        Optional<String> preparedValue = prepareMessages.hasValidPrepareQuorum(config.getId(), consensusInstance, round);
        if (preparedValue.isPresent() && instance.getPreparedRound() < round) {
            instance.setPreparedValue(preparedValue.get());
            instance.setPreparedRound(round);

            // Must reply to prepare message senders
            Collection<ConsensusMessage> sendersMessage = prepareMessages.getMessages(consensusInstance, round)
                    .values();

            CommitMessage c = new CommitMessage(preparedValue.get());
            instance.setCommitMessage(c);

            sendersMessage.forEach(senderMessage -> {
                ConsensusMessage m = new ConsensusMessageBuilder(config.getId(), Message.Type.COMMIT)
                        .setConsensusInstance(consensusInstance)
                        .setRound(round)
                        .setReplyTo(senderMessage.getSenderId())
                        .setReplyToMessageId(senderMessage.getMessageId())
                        .setMessage(c.toJson())
                        .build();

                authenticatedPerfectLink.send(senderMessage.getSenderId(), m);
            });
        }
    }


    /**
     * Handle commit messages and decide if there is a valid quorum.
     *
     * @param message Message to be handled
     */
    public synchronized void uponCommit(ConsensusMessage message) {
        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();

        LOGGER.info(
                MessageFormat.format("{0} - Received COMMIT message from node {1}: Consensus Instance {2}, Round {3}",
                        config.getId(), message.getSenderId(), consensusInstance, round));

        commitMessages.addMessage(message);

        InstanceInfo instance = this.instanceInfo.get(consensusInstance);

        if (instance == null) {
            // Should never happen because only receives commit as a response to a prepare message
            MessageFormat.format(
                    "{0} - CRITICAL: Received COMMIT message from {1}: Consensus Instance {2}, Round {3} BUT NO INSTANCE INFO",
                    config.getId(), message.getSenderId(), consensusInstance, round);
            return;
        }

        // Within an instance of the algorithm, each upon rule is triggered at most once
        // for any round r
        if (instance.getCommittedRound() >= round) {
            LOGGER.info(
                    MessageFormat.format(
                            "{0} - Already received COMMIT message for Consensus Instance {1}, Round {2}, ignoring",
                            config.getId(), consensusInstance, round));
            return;
        }

        Optional<String> commitValue = commitMessages.hasValidCommitQuorum(config.getId(),
                consensusInstance, round);

        if (commitValue.isPresent() && instance.getCommittedRound() < round) {

            instance = this.instanceInfo.get(consensusInstance);
            instance.setCommittedRound(round);

            String value = commitValue.get();

            // Append value to the ledger (must be synchronized to be thread-safe)
            synchronized (ledger) {

                // Increment size of ledger to accommodate current instance
                ledger.ensureCapacity(consensusInstance);
                while (ledger.size() < consensusInstance - 1) {
                    ledger.add("");
                }

                ledger.add(consensusInstance - 1, value);

                LOGGER.info(
                        MessageFormat.format(
                                "{0} - Current Ledger: {1}",
                                config.getId(), String.join("", ledger)));
            }

            lastDecidedConsensusInstance.getAndIncrement();

            LOGGER.info(
                    MessageFormat.format(
                            "{0} - Decided on Consensus Instance {1}, Round {2}, Successful? {3}",
                            config.getId(), consensusInstance, round, true));
        }
    }

    @Override
    public void listen() {
        try {
            // Thread to listen on every request
            new Thread(() -> {
                try {
                    while (true) {
                        Message message = authenticatedPerfectLink.receive();

                        // Separate thread to handle each message
                        new Thread(() -> {
                            switch (message.getType()) {
                                case PRE_PREPARE -> uponPrePrepare((ConsensusMessage) message);

                                case PREPARE -> uponPrepare((ConsensusMessage) message);

                                case COMMIT -> uponCommit((ConsensusMessage) message);

                                case ACK -> LOGGER.info(MessageFormat.format("{0} - Received ACK message from {1}",
                                        config.getId(), message.getSenderId()));

                                case IGNORE -> LOGGER.info(
                                        MessageFormat.format("{0} - Received IGNORE message from {1}",
                                                config.getId(), message.getSenderId()));

                                default -> LOGGER.info(
                                        MessageFormat.format("{0} - Received unknown message from {1}",
                                                config.getId(), message.getSenderId()));
                            }
                        }).start();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}