package pt.ulisboa.tecnico.hdsledger.service.services;

import pt.ulisboa.tecnico.hdsledger.communication.*;
import pt.ulisboa.tecnico.hdsledger.communication.builder.ConsensusMessageBuilder;
import pt.ulisboa.tecnico.hdsledger.service.models.*;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.config.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.config.ServerProcessConfig;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service to handle consensus instances and ledger.
 */
public class NodeService implements UDPService {

    private static final CustomLogger LOGGER = new CustomLogger(NodeService.class.getName());

    // Time to periodically wait for the previous consensus to be decided before starting a new one
    private static final int CONSENSUS_WAIT_TIME = 1000;
    // Expire time for the round-change timer
    private static final int ROUND_CHANGE_TIMER_EXPIRE_TIME = 500;

    private final ServerProcessConfig[] nodesConfig; // All nodes configuration
    private final ServerProcessConfig config; // Current node configuration

    // Link to communicate with nodes
    private final AuthenticatedPerfectLink authenticatedPerfectLink;
    // Consensus instance -> Round -> List of prepare messages
    private final PrepareMessageBucket prepareMessages;
    // Consensus instance -> Round -> List of commit messages
    private final CommitMessageBucket commitMessages;
    // Consensus instance -> Round -> List of round-change messages
    private final RoundChangeMessageBucket roundChangeMessages;
    // Store if already received pre-prepare for a given <consensus, round>
    private final Map<Integer, Map<Integer, Boolean>> receivedPrePrepare = new ConcurrentHashMap<>();
    // Consensus instance information per consensus instance
    private final Map<Integer, InstanceInfo> instanceInfo = new ConcurrentHashMap<>();
    // Current consensus instance
    private final AtomicInteger consensusInstance = new AtomicInteger(0);
    // Last decided consensus instance
    private final AtomicInteger lastDecidedConsensusInstance = new AtomicInteger(0);
    // Timer for the consensus instance, triggering round-change
    private Timer timer = new Timer();

    // Ledger (for now, just a list of strings)
    private ArrayList<String> ledger = new ArrayList<String>();

    public NodeService(AuthenticatedPerfectLink authenticatedPerfectLink, ServerProcessConfig config, ServerProcessConfig[] nodesConfig) {
        this.authenticatedPerfectLink = authenticatedPerfectLink;
        this.config = config;
        this.nodesConfig = nodesConfig;

        this.prepareMessages = new PrepareMessageBucket(nodesConfig.length);
        this.commitMessages = new CommitMessageBucket(nodesConfig.length);
        this.roundChangeMessages = new RoundChangeMessageBucket(nodesConfig.length);
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

    private String getLeaderId(int consensusInstance, int round) {
        return String.valueOf(((round - 1) % this.nodesConfig.length) + 1);
    }

    private boolean isNodeLeader(int consensusInstance, int round, String id) {
        return getLeaderId(consensusInstance, round).equals(id);
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

        waitForPreviousConsensus(localConsensusInstance);

        InstanceInfo instance = this.instanceInfo.get(localConsensusInstance);

        var nodeIsLeader = isNodeLeader(localConsensusInstance, instance.getCurrentRound(), this.config.getId());

        // Leader broadcasts PRE-PREPARE message
        if (nodeIsLeader || this.config.getBehavior() == ProcessConfig.ProcessBehavior.NON_LEADER_CONSENSUS_INITIATION) {
            if (nodeIsLeader)
                LOGGER.info(MessageFormat.format("{0} - Node is leader, sending PRE-PREPARE message", config.getId()));
            else
                LOGGER.info(MessageFormat.format("{0} - Node is not leader, but still sending PRE-PREPARE message", config.getId()));

            this.authenticatedPerfectLink.broadcast(
                    new ConsensusMessageBuilder(config.getId(), Message.Type.PRE_PREPARE)
                            .setConsensusInstance(localConsensusInstance)
                            .setRound(instance.getCurrentRound())
                            .setMessage(new PrePrepareMessage(inputValue).toJson())
                            .build()
            );
        } else if (this.config.getBehavior() == ProcessConfig.ProcessBehavior.LEADER_IMPERSONATION) {
            LOGGER.info(MessageFormat.format("{0} - Node is not leader, sending PRE-PREPARE message impersonating leader", config.getId()));

            this.authenticatedPerfectLink.broadcast(
                    new ConsensusMessageBuilder(getLeaderId(localConsensusInstance, instance.getCurrentRound()),
                            Message.Type.PRE_PREPARE)
                            .setConsensusInstance(localConsensusInstance)
                            .setRound(instance.getCurrentRound())
                            .setMessage(new PrePrepareMessage(inputValue).toJson())
                            .build()
            );
        } else {
            LOGGER.info(MessageFormat.format("{0} - Node is not leader, waiting for PRE-PREPARE message", config.getId()));
        }

        // Start timer for the consensus instance
        startTimer();
    }

    /**
     * Handle pre-prepare messages and if the message came from leader and is justified then broadcast prepare.
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


        if (!isNodeLeader(consensusInstance, round, senderId) || !justifyPrePrepare(consensusInstance, round))
            return;

        this.instanceInfo.putIfAbsent(consensusInstance, new InstanceInfo(value));
        receivedPrePrepare.putIfAbsent(consensusInstance, new ConcurrentHashMap<>());

        if (receivedPrePrepare.get(consensusInstance).put(round, true) != null) {
            LOGGER.info(
                    MessageFormat.format(
                            "{0} - Already received PRE-PREPARE message for Consensus Instance {1}, Round {2}, "
                                    + "replying again to make sure it reaches the initial sender",
                            config.getId(), consensusInstance, round));
        } else {
            stopTimer();
            startTimer();
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

        LOGGER.info(
                MessageFormat.format(
                        "{0} - Received PREPARE message from node {1}: Consensus Instance {2}, Round {3}",
                        config.getId(), senderId, consensusInstance, round));

        prepareMessages.addMessage(message);

        InstanceInfo instance = this.instanceInfo.get(consensusInstance);

        if (instance.getPreparedRound() >= round) {
            LOGGER.info(
                    MessageFormat.format(
                            "{0} - Already received PREPARE quorum for Consensus Instance {1}, Round {2}, "
                                    + "replying with commit to make sure it reaches the initial sender",
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

            CommitMessage c = new CommitMessage(preparedValue.get());
            instance.setCommitMessage(c);

            // TODO Change to normal broadcast instead of sending only to those who sent prepare messages
            /*authenticatedPerfectLink.broadcast(
                    new ConsensusMessageBuilder(config.getId(), Message.Type.COMMIT)
                            .setConsensusInstance(consensusInstance)
                            .setRound(round)
                            .setMessage(c.toJson())
                            .build());*/

            // Must reply to prepare message senders
            Collection<ConsensusMessage> sendersMessage = prepareMessages.getMessages(consensusInstance, round)
                    .values();

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
            LOGGER.error(MessageFormat.format(
                    "{0} - CRITICAL: Received COMMIT message from {1}: Consensus Instance {2}, Round {3} BUT NO INSTANCE INFO",
                    config.getId(), message.getSenderId(), consensusInstance, round));
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
            stopTimer();

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

    public synchronized void uponRoundChange(ConsensusMessage message) {
        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();

        LOGGER.info(
                MessageFormat.format("{0} - Received ROUND-CHANGE message from node {1}: Consensus Instance {2}, Round {3}",
                        config.getId(), message.getSenderId(), consensusInstance, round));

        roundChangeMessages.addMessage(message);

        InstanceInfo instance = this.instanceInfo.get(consensusInstance);

        if (instance == null) {
            LOGGER.error(
                    MessageFormat.format("{0} - CRITICAL: Received ROUND-CHANGE message from {1}: Consensus Instance {2}, Round {3} BUT NO INSTANCE INFO",
                            config.getId(), message.getSenderId(), consensusInstance, round));
            return;
        }

        Optional<PreparedRoundValuePair> highestPrepared = roundChangeMessages.getHighestPrepared(config.getId(), consensusInstance, round);

        var nodeIsLeader = isNodeLeader(consensusInstance, round, this.config.getId());

        if (nodeIsLeader && justifyRoundChange(consensusInstance, round) && highestPrepared.isPresent()) {
            LOGGER.info(
                    MessageFormat.format("{0} - Updated round to {1} for Consensus Instance {2}",
                            config.getId(), round, consensusInstance));

            String valueToBroadcast = highestPrepared.get().round != 0 && highestPrepared.get().value != null
                    ? highestPrepared.get().value
                    : instance.getInputValue();

            // Broadcast preprepare message
            ConsensusMessage consensusMessage = new ConsensusMessageBuilder(config.getId(), Message.Type.PRE_PREPARE)
                    .setConsensusInstance(consensusInstance)
                    .setRound(round)
                    .setMessage(new PrePrepareMessage(valueToBroadcast).toJson())
                    .build();

            authenticatedPerfectLink.broadcast(consensusMessage);
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

                                case ROUND_CHANGE -> uponRoundChange((ConsensusMessage) message);

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


    /**
     * A round change is justified if:
     * <p>
     * 1. There is a valid quorum of round change messages such that their prepared round and prepared value are null
     * <p>
     * 2. There is a valid quorum of prepare messages such that their prepared pair is the same as the highest prepared pair
     *
     * @param consensusInstance Consensus instance
     * @param round             Consensus round
     * @return True if the round change is justified
     */
    private boolean justifyRoundChange(int consensusInstance, int round) {
        if (!roundChangeMessages.hasValidRoundChangeQuorum(config.getId(), consensusInstance, round))
            return false;

        return roundChangeMessages.getMessages(consensusInstance, round).values().stream()
                .allMatch((roundChangeMessage) ->
                        roundChangeMessage.getPreparedRound() == -1 && roundChangeMessage.getPreparedValue() == null
                )
                ||
                roundChangeMessages.getHighestPrepared(config.getId(), consensusInstance, round)
                        .map((highestPrepared) -> prepareMessages
                                .hasValidPrepareQuorum(config.getId(), consensusInstance, highestPrepared.round)
                                .map((value) -> highestPrepared.value.equals(value))
                                .orElse(false))
                        .orElse(false);

    }

    /**
     * A preprepare is justified if:
     * <p>
     * 1. Round is 1
     * <p>
     * 2. There is a valid quorum of round change messages such that their prepared round and prepared value are null
     * <p>
     * 3. There is a valid quorum of prepare messages such that their prepared pair is the same as the highest prepared pair
     * <p>
     * Points 2 and 3 are simplified to a call to justifyRoundChange,
     * as they are the same condition to justify the round change.
     *
     * @param consensusInstance Consensus instance
     * @param round             Consensus round
     * @return True if the preprepare is justified
     */
    private boolean justifyPrePrepare(int consensusInstance, int round) {
        return round == 1 || justifyRoundChange(consensusInstance, round);
    }

    /**
     * Start the timer for the consensus instance, expiring after TIMER_EXPIRE_TIME.
     * If the timer expires, the round is incremented and a ROUND-CHANGE message is broadcast.
     */
    private void startTimer() {
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                LOGGER.info(
                        MessageFormat.format("{0} - Timer expired for Consensus Instance {1}, triggering round-change", config.getId(), lastDecidedConsensusInstance.get() + 1));

                InstanceInfo instance = instanceInfo.get(lastDecidedConsensusInstance.get() + 1);
                instance.setCurrentRound(instance.getCurrentRound() + 1);

                ConsensusMessage consensusMessage = new ConsensusMessageBuilder(config.getId(), Message.Type.ROUND_CHANGE)
                        .setConsensusInstance(lastDecidedConsensusInstance.get() + 1)
                        .setRound(instance.getCurrentRound())
                        .setPreparedRound(instance.getPreparedRound())
                        .setPreparedValue(instance.getPreparedValue())
                        .build();

                authenticatedPerfectLink.broadcast(consensusMessage);
            }
        }, ROUND_CHANGE_TIMER_EXPIRE_TIME);
    }

    private void stopTimer() {
        timer.cancel();
    }

    /**
     * Waits for the previous consensus to be decided before starting a new one.
     *
     * @param localConsensusInstance current consensus instance waiting to start
     */
    private void waitForPreviousConsensus(int localConsensusInstance) {

        while (lastDecidedConsensusInstance.get() < localConsensusInstance - 1) {
            try {
                Thread.sleep(CONSENSUS_WAIT_TIME);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
