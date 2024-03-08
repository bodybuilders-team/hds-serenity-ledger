package pt.ulisboa.tecnico.hdsledger.service.services;

import pt.ulisboa.tecnico.hdsledger.communication.AuthenticatedPerfectLink;
import pt.ulisboa.tecnico.hdsledger.communication.CommitMessage;
import pt.ulisboa.tecnico.hdsledger.communication.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.PrePrepareMessage;
import pt.ulisboa.tecnico.hdsledger.communication.PrepareMessage;
import pt.ulisboa.tecnico.hdsledger.communication.builder.ConsensusMessageBuilder;
import pt.ulisboa.tecnico.hdsledger.service.models.CommitMessageBucket;
import pt.ulisboa.tecnico.hdsledger.service.models.InstanceInfo;
import pt.ulisboa.tecnico.hdsledger.service.models.PrepareMessageBucket;
import pt.ulisboa.tecnico.hdsledger.service.models.PreparedRoundValuePair;
import pt.ulisboa.tecnico.hdsledger.service.models.RoundChangeMessageBucket;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.config.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.config.ServerProcessConfig;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service to handle consensus instances and ledger.
 */
public class NodeService implements UDPService {

    // Time to periodically wait for the previous consensus to be decided before starting a new one
    private static final int CONSENSUS_WAIT_TIME = 1000;
    // Expire time for the round-change timer
    private static final int ROUND_CHANGE_TIMER_EXPIRE_TIME = 1000;
    // Starting round
    private static final int STARTING_ROUND = 1;

    private final ProcessLogger logger;
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
    // Ledger (for now, just a list of strings)
    private final ArrayList<String> ledger = new ArrayList<>();
    // Timer for the consensus instance, triggering round-change
    private Timer timer = new Timer();

    public NodeService(AuthenticatedPerfectLink authenticatedPerfectLink, ServerProcessConfig config, ServerProcessConfig[] nodesConfig) {
        this.authenticatedPerfectLink = authenticatedPerfectLink;
        this.config = config;
        this.nodesConfig = nodesConfig;

        this.prepareMessages = new PrepareMessageBucket(nodesConfig.length);
        this.commitMessages = new CommitMessageBucket(nodesConfig.length);
        this.roundChangeMessages = new RoundChangeMessageBucket(nodesConfig.length);

        this.logger = new ProcessLogger(NodeService.class.getName(), config.getId());
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
            logger.info(MessageFormat.format("Node already started consensus for instance {0}", localConsensusInstance));
            return;
        }

        waitForPreviousConsensus(localConsensusInstance);

        var nodeIsLeader = isNodeLeader(localConsensusInstance, STARTING_ROUND, this.config.getId());

        // Leader broadcasts PRE-PREPARE message
        if (nodeIsLeader || this.config.getBehavior() == ProcessConfig.ProcessBehavior.NON_LEADER_CONSENSUS_INITIATION) {
            if (nodeIsLeader)
                logger.info(MessageFormat.format("Sending PRE-PREPARE({0}, {1}) - Node is leader",
                        localConsensusInstance, STARTING_ROUND));
            else
                logger.info(MessageFormat.format("Sending PRE-PREPARE({0}, {1}) - Node is not leader, but still sending",
                        localConsensusInstance, STARTING_ROUND));

            this.authenticatedPerfectLink.broadcast(
                    new ConsensusMessageBuilder(config.getId(), Message.Type.PRE_PREPARE)
                            .setConsensusInstance(localConsensusInstance)
                            .setRound(STARTING_ROUND)
                            .setMessage(new PrePrepareMessage(inputValue).toJson())
                            .build()
            );
        } else if (this.config.getBehavior() == ProcessConfig.ProcessBehavior.LEADER_IMPERSONATION) {
            logger.info(MessageFormat.format("Sending PRE-PREPARE({0}, {1}, {2}) - Node is not leader, but is impersonating leader",
                    localConsensusInstance, STARTING_ROUND, inputValue));

            this.authenticatedPerfectLink.broadcast(
                    new ConsensusMessageBuilder(getLeaderId(localConsensusInstance, STARTING_ROUND),
                            Message.Type.PRE_PREPARE)
                            .setConsensusInstance(localConsensusInstance)
                            .setRound(STARTING_ROUND)
                            .setMessage(new PrePrepareMessage(inputValue).toJson())
                            .build()
            );
        } else {
            logger.info("Node is not leader, waiting for PRE-PREPARE message");
        }

        // Start timer for the consensus instance
        startTimer(localConsensusInstance);
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

        logger.info(MessageFormat.format("Received PRE-PREPARE({0}, {1}) from node {2}", consensusInstance, round, senderId));

        if (!isNodeLeader(consensusInstance, round, senderId) || !justifyPrePrepare(consensusInstance, round))
            return;

        this.instanceInfo.putIfAbsent(consensusInstance, new InstanceInfo(prePrepareMessage.getValue()));
        receivedPrePrepare.putIfAbsent(consensusInstance, new ConcurrentHashMap<>());

        if (receivedPrePrepare.get(consensusInstance).put(round, true) != null) {
            logger.info(
                    MessageFormat.format(
                            "Already received PRE-PREPARE({0}, {1}) replying again to make sure it reaches the initial sender",
                            consensusInstance, round));
        } else {
            startTimer(consensusInstance);
        }

        ConsensusMessage consensusMessage = new ConsensusMessageBuilder(config.getId(), Message.Type.PREPARE)
                .setConsensusInstance(consensusInstance)
                .setRound(round)
                .setMessage(new PrepareMessage(prePrepareMessage.getValue()).toJson())
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

        logger.info(
                MessageFormat.format("Received PREPARE({0}, {1}) from node {2}",
                        consensusInstance, round, senderId));

        prepareMessages.addMessage(message);

        InstanceInfo instance = this.instanceInfo.get(consensusInstance);

        if (instance.getPreparedRound() >= round) {
            logger.info(
                    MessageFormat.format(
                            "Already received quorum of PREPARE for Consensus Instance {0}: PREPARE({0}, {1}) replying with commit to make sure it reaches the initial sender",
                            consensusInstance, round));

            authenticatedPerfectLink.send(senderId,
                    new ConsensusMessageBuilder(config.getId(), Message.Type.COMMIT)
                            .setConsensusInstance(consensusInstance)
                            .setRound(round)
                            .setReplyTo(senderId)
                            .setReplyToMessageId(message.getMessageId())
                            .setMessage(instance.getCommitMessage().toJson())
                            .build()
            );

            return;
        }

        Optional<String> preparedValue = prepareMessages.hasValidPrepareQuorum(config.getId(), consensusInstance, round);

        if (preparedValue.isPresent() && instance.getPreparedRound() < round) {
            CommitMessage commitMessage = new CommitMessage(preparedValue.get());
            instance.setCommitMessage(commitMessage);

            instance.setPreparedValue(preparedValue.get());
            instance.setPreparedRound(round);

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
                        .setMessage(commitMessage.toJson())
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

        logger.info(MessageFormat.format("Received COMMIT({0}, {1}) from node {2}",
                consensusInstance, round, message.getSenderId()));

        commitMessages.addMessage(message);

        InstanceInfo instance = this.instanceInfo.get(consensusInstance);

        if (instance == null) {
            // Should never happen because only receives commit as a response to a prepare message
            logger.error(MessageFormat.format(
                    "CRITICAL: Received COMMIT({0}, {1})) from node {2} BUT NO INSTANCE INFO",
                    consensusInstance, round, message.getSenderId()));
            return;
        }

        if (instance.alreadyDecided()) {
            logger.info(
                    MessageFormat.format(
                            "Received COMMIT({0}, {1}) from node {2} but already decided for Consensus Instance {0}, ignoring...",
                            consensusInstance, round, message.getSenderId()));
            return;
        }

        Optional<String> commitValue = commitMessages.hasValidCommitQuorum(config.getId(), consensusInstance, round);

        if (commitValue.isPresent()) {
            stopTimer();

            instance.setDecidedRound(round);

            appendToLedger(consensusInstance, commitValue.get());

            lastDecidedConsensusInstance.getAndIncrement();

            logger.info(
                    MessageFormat.format("Decided on Consensus Instance {0}, Round {1} successfully",
                            consensusInstance, round));
        }
    }

    /**
     * Append value to the ledger.
     *
     * @param consensusInstance Consensus instance
     * @param value             Value to append
     */
    private void appendToLedger(int consensusInstance, String value) {
        synchronized (ledger) {
            // Increment size of ledger to accommodate current instance
            ledger.ensureCapacity(consensusInstance);
            while (ledger.size() < consensusInstance - 1) {
                ledger.add("");
            }

            ledger.add(consensusInstance - 1, value);

            logger.info(MessageFormat.format("Current Ledger: {0}", String.join("", ledger)));
        }
    }

    public synchronized void uponRoundChange(ConsensusMessage message) {
        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();

        logger.info(MessageFormat.format("Received ROUND-CHANGE({0}, {1}) from node {2}",
                consensusInstance, round, message.getSenderId()));

        roundChangeMessages.addMessage(message);

        InstanceInfo instance = this.instanceInfo.get(consensusInstance);

        if (instance == null) {
            logger.error(
                    MessageFormat.format("CRITICAL: Received ROUND-CHANGE({0}, {1}) from node {2} BUT NO INSTANCE INFO",
                            consensusInstance, round, message.getSenderId()));
            return;
        }

        if (instance.alreadyDecided()) {
            logger.info(
                    MessageFormat.format(
                            "Received ROUND_CHANGE({0}, {1}) from node {2} but already decided for Consensus Instance {1}, sending a COMMIT back to sender",
                            consensusInstance, round, message.getSenderId()));

            authenticatedPerfectLink.send(message.getSenderId(),
                    new ConsensusMessageBuilder(config.getId(), Message.Type.COMMIT)
                            .setConsensusInstance(consensusInstance)
                            .setRound(round)
                            .setReplyTo(message.getSenderId())
                            .setReplyToMessageId(message.getMessageId())
                            .setMessage(instance.getCommitMessage().toJson())
                            .build()
            );

            return;
        }

        List<ConsensusMessage> biggerRoundChangeMessages =
                this.roundChangeMessages.getMessagesFromRoundGreaterThan(consensusInstance, round);
        int f = Math.floorDiv(nodesConfig.length - 1, 3);
        if (biggerRoundChangeMessages.size() >= f + 1) {
            int newRound = biggerRoundChangeMessages.stream()
                    .mapToInt(ConsensusMessage::getRound)
                    .min().getAsInt();

            instance.setCurrentRound(newRound);

            startTimer(consensusInstance);

            logger.info(
                    MessageFormat.format("Updated round to {0} for Consensus Instance {1}, broadcasting ROUND-CHANGE({1}, {0}, {2}, {3})",
                            newRound, consensusInstance, instance.getPreparedRound(), instance.getPreparedValue()));

            authenticatedPerfectLink.broadcast(new ConsensusMessageBuilder(config.getId(), Message.Type.ROUND_CHANGE)
                    .setConsensusInstance(consensusInstance)
                    .setRound(newRound)
                    .setPreparedRound(instance.getPreparedRound())
                    .setPreparedValue(instance.getPreparedValue())
                    .build());
        }


        Optional<PreparedRoundValuePair> highestPrepared = roundChangeMessages.getHighestPrepared(config.getId(), consensusInstance, round);

        var nodeIsLeader = isNodeLeader(consensusInstance, round, this.config.getId());

        if (nodeIsLeader && justifyRoundChange(consensusInstance, round) && highestPrepared.isPresent()) {
            String valueToBroadcast = !highestPrepared.get().isNull()
                    ? highestPrepared.get().getValue()
                    : instance.getInputValue();

            logger.info(
                    MessageFormat.format("Updated round to {0} for Consensus Instance {1}, broadcasting PRE-PREPARE({1}, {0}, {2})",
                            round, consensusInstance, valueToBroadcast));

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
                while (true) {
                    try {
                        Message message = authenticatedPerfectLink.receive();

                        // Separate thread to handle each message
                        new Thread(() -> {
                            switch (message.getType()) {
                                case PRE_PREPARE -> uponPrePrepare((ConsensusMessage) message);

                                case PREPARE -> uponPrepare((ConsensusMessage) message);

                                case COMMIT -> uponCommit((ConsensusMessage) message);

                                case ROUND_CHANGE -> uponRoundChange((ConsensusMessage) message);

                                case ACK -> logger.info(MessageFormat.format("Received ACK({0} from {1}",
                                        message.getMessageId(), message.getSenderId()));

                                case IGNORE -> {
                                    /*LOGGER.info(
                                        MessageFormat.format("Received IGNORE message from {0}",
                                            message.getSenderId()));*/
                                }

                                default -> logger.info(MessageFormat.format("Received unknown message from {0}",
                                        message.getSenderId()));
                            }
                        }).start();
                    } catch (Exception e) {
                        logger.error(MessageFormat.format("Error receiving message: {0}", e.getMessage()));
                    }
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
                        new PreparedRoundValuePair(
                                roundChangeMessage.getPreparedRound(),
                                roundChangeMessage.getPreparedValue()
                        ).isNull()
                )
                ||
                roundChangeMessages.getHighestPrepared(config.getId(), consensusInstance, round)
                        .map((highestPrepared) -> prepareMessages
                                .hasValidPrepareQuorum(config.getId(), consensusInstance, highestPrepared.getRound())
                                .map((value) -> highestPrepared.getValue().equals(value))
                                .orElse(false))
                        .orElse(false);

    }

    /**
     * A preprepare is justified if:
     * <p>
     * 1. Round is 1 (starting round)
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
        return round == STARTING_ROUND || justifyRoundChange(consensusInstance, round);
    }

    /**
     * Start the timer for the consensus instance, expiring after TIMER_EXPIRE_TIME.
     * If the timer expires, the round is incremented and a ROUND-CHANGE message is broadcast.
     *
     * @param consensusInstance the consensus instance
     */
    private void startTimer(int consensusInstance) {
        stopTimer();
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                InstanceInfo instance = instanceInfo.get(consensusInstance);
                instance.setCurrentRound(instance.getCurrentRound() + 1);
                int round = instance.getCurrentRound();
                int preparedRound = instance.getPreparedRound();
                String preparedValue = instance.getPreparedValue();

                logger.info(
                        MessageFormat.format("Timer expired for Consensus Instance {0}. Updated round to {1}, triggering round-change. " +
                                        "Broadcasting ROUND_CHANGE({0}, {1}, {2}, {3})",
                                consensusInstance, round, preparedRound, preparedValue));

                authenticatedPerfectLink.broadcast(new ConsensusMessageBuilder(config.getId(), Message.Type.ROUND_CHANGE)
                        .setConsensusInstance(consensusInstance)
                        .setRound(round)
                        .setPreparedRound(preparedRound)
                        .setPreparedValue(preparedValue)
                        .build());
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
