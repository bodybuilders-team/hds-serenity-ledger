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
@SuppressWarnings("CallToPrintStackTrace")
public class NodeService implements UDPService {

    // Expire time for the round-change timer
    private static final long ROUND_CHANGE_TIMER_EXPIRE_TIME = 500;
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
    // Store if already received quorum of round-change for a given <consensus, round>
    private final Map<Integer, Map<Integer, Boolean>> receivedRoundChangeQuorum = new ConcurrentHashMap<>();
    // Consensus instance information per consensus instance
    private final Map<Integer, InstanceInfo> instanceInfo = new ConcurrentHashMap<>();
    // Current consensus instance
    private final AtomicInteger consensusInstance = new AtomicInteger(0);
    // Last decided consensus instance
    private final AtomicInteger lastDecidedConsensusInstance = new AtomicInteger(0);
    // Ledger (for now, just a list of strings)
    private final ArrayList<String> ledger = new ArrayList<>();
    // Timer for the consensus instance, triggering round-change
    private final MultiThreadTimer timer = new MultiThreadTimer();
    // Lock objects for prepare messages
    private final Map<Integer, Object> prepareLockObjects = new ConcurrentHashMap<>();
    // Lock objects for commit messages
    private final Map<Integer, Object> commitLockObjects = new ConcurrentHashMap<>();
    // Lock objects for round-change messages
    private final Map<Integer, Object> roundChangeLockObjects = new ConcurrentHashMap<>();
    // Wait for consensus object
    private final Map<Integer, Object> waitForConsensusObjects = new ConcurrentHashMap<>();

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
            logger.info(MessageFormat.format("Node already started consensus for instance \u001B[34m{0}\u001B[37m", localConsensusInstance));
            return;
        }

        waitForPreviousConsensus(localConsensusInstance);

        var nodeIsLeader = isNodeLeader(localConsensusInstance, STARTING_ROUND, this.config.getId());

        // Leader broadcasts PRE-PREPARE message
        if (nodeIsLeader || this.config.getBehavior() == ProcessConfig.ProcessBehavior.NON_LEADER_CONSENSUS_INITIATION) {
            ConsensusMessage messageToBroadcast = new ConsensusMessageBuilder(config.getId(), Message.Type.PRE_PREPARE)
                    .setConsensusInstance(localConsensusInstance)
                    .setRound(STARTING_ROUND)
                    .setMessage(new PrePrepareMessage(inputValue).toJson())
                    .build();

            if (nodeIsLeader)
                logger.info(MessageFormat.format("Broadcasting {0} - Node is leader",
                        messageToBroadcast.getConsensusMessageRepresentation()));
            else
                logger.info(MessageFormat.format("Broadcasting {0} - Node is not leader, but still sending",
                        messageToBroadcast.getConsensusMessageRepresentation()));

            this.authenticatedPerfectLink.broadcast(messageToBroadcast);
        } else if (this.config.getBehavior() == ProcessConfig.ProcessBehavior.LEADER_IMPERSONATION) {
            ConsensusMessage messageToBroadcast = new ConsensusMessageBuilder(getLeaderId(localConsensusInstance, STARTING_ROUND),
                    Message.Type.PRE_PREPARE)
                    .setConsensusInstance(localConsensusInstance)
                    .setRound(STARTING_ROUND)
                    .setMessage(new PrePrepareMessage(inputValue).toJson())
                    .build();

            logger.info(MessageFormat.format("Broadcasting {0} - Node is not leader, but is impersonating leader",
                    messageToBroadcast.getConsensusMessageRepresentation()));

            this.authenticatedPerfectLink.broadcast(messageToBroadcast);
        } else {
            logger.info("Node is not leader, waiting for \u001B[32mPRE-PREPARE\u001B[37m message...");
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

        logger.info(MessageFormat.format("Received {0} from node \u001B[33m{1}\u001B[37m", message.getConsensusMessageRepresentation(), senderId));

        if (!isNodeLeader(consensusInstance, round, senderId) || !justifyPrePrepare(consensusInstance, round)) {
            logger.info(MessageFormat.format("Received \u001B[32mPRE-PREPARE\u001B[37m(\u001B[34m{0}\u001B[37m, \u001B[34m{1}\u001B[37m, _) from node \u001B[33m{2}\u001B[37m, but not justified. Replying to acknowledge reception",
                    consensusInstance, round, senderId));

            // TODO Improve mechanism. Do not send ACK, keep receiving the same pre-prepare message without treating it as duplicate to eventually make the condition true

            Message responseMessage = new Message(this.config.getId(), Message.Type.ACK);
            responseMessage.setMessageId(senderMessageId);
            this.authenticatedPerfectLink.send(senderId, responseMessage);
            return;
        }

        this.instanceInfo.putIfAbsent(consensusInstance, new InstanceInfo(prePrepareMessage.getValue()));
        receivedPrePrepare.putIfAbsent(consensusInstance, new ConcurrentHashMap<>());
        if (receivedPrePrepare.get(consensusInstance).put(round, true) != null) {
            logger.info(
                    MessageFormat.format(
                            "Already received \u001B[33mPRE-PREPARE\u001B[37m(\u001B[34m{0}\u001B[37m, \u001B[34m{1}\u001B[37m, _) from node \u001B[33m{2}\u001B[37m, leader. Replying again to make sure it reaches the initial sender",
                            consensusInstance, round, senderId));
        } else {
            startTimer(consensusInstance);
        }

        ConsensusMessage messageToBroadcast = new ConsensusMessageBuilder(config.getId(), Message.Type.PREPARE)
                .setConsensusInstance(consensusInstance)
                .setRound(round)
                .setMessage(new PrepareMessage(prePrepareMessage.getValue()).toJson())
                .setReplyTo(senderId)
                .setReplyToMessageId(senderMessageId)
                .build();

        logger.info(MessageFormat.format("\u001B[32mPRE-PREPARE\u001B[37m is justified. Broadcasting {0}", messageToBroadcast.getMessageRepresentation()));

        this.authenticatedPerfectLink.broadcast(messageToBroadcast);
    }

    /**
     * Handle prepare messages and if there is a valid quorum broadcast commit.
     *
     * @param message Message to be handled
     */
    public void uponPrepare(ConsensusMessage message) {
        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();
        String senderId = message.getSenderId();

        logger.info(MessageFormat.format("Received {0} from node \u001B[33m{1}\u001B[37m", message.getConsensusMessageRepresentation(), senderId));

        prepareMessages.addMessage(message);

        PrepareMessage prepareMessage = message.deserializePrepareMessage();

        this.instanceInfo.putIfAbsent(consensusInstance, new InstanceInfo(prepareMessage.getValue()));
        InstanceInfo instance = this.instanceInfo.get(consensusInstance);

        synchronized (prepareLockObjects.computeIfAbsent(consensusInstance, k -> new Object())) {
            if (instance.getPreparedRound() != -1) {
                logger.info(
                        MessageFormat.format(
                                "Already received quorum of \u001B[32mPREPARE\u001B[37m for Consensus Instance \u001B[34m{0}\u001B[37m. " +
                                        "Replying with \u001B[32mCOMMIT\u001B[37m to make sure it reaches the initial senders of {1}",
                                consensusInstance, message.getConsensusMessageRepresentation()));

                // TODO Change to normal broadcast instead of sending only to those who sent prepare messages (needs ACK to be sent in all messages, though)
                prepareMessages.getMessages(consensusInstance, round).values().forEach(senderMessage ->
                        this.authenticatedPerfectLink.send(senderMessage.getSenderId(),
                                new ConsensusMessageBuilder(config.getId(), Message.Type.COMMIT)
                                        .setConsensusInstance(consensusInstance)
                                        .setRound(instance.getPreparedRound())
                                        .setReplyTo(senderMessage.getSenderId())
                                        .setReplyToMessageId(senderMessage.getMessageId())
                                        .setMessage(new CommitMessage(instance.getPreparedValue()).toJson())
                                        .build()
                        ));

                return;
            }

            Optional<String> preparedValue = prepareMessages.hasValidPrepareQuorum(consensusInstance, round);

            if (preparedValue.isPresent() && instance.getPreparedRound() < round) {
                instance.setPreparedRound(round);
                instance.setPreparedValue(preparedValue.get());

                // TODO Change to normal broadcast instead of sending only to those who sent prepare messages (needs ACK to be sent in all messages, though)
                /*this.authenticatedPerfectLink.broadcast(
                    new ConsensusMessageBuilder(config.getId(), Message.Type.COMMIT)
                            .setConsensusInstance(consensusInstance)
                            .setRound(round)
                            .setMessage(c.toJson())
                            .build());*/

                logger.info(MessageFormat.format("Received quorum of \u001B[32mPREPARE\u001B[37m(\u001B[34m{0}\u001B[37m, \u001B[34m{1}\u001B[37m, \u001B[33m\"{2}\"\u001B[37m). " +
                                "Broadcasting \u001B[32mCOMMIT\u001B[37m(\u001B[34m{0}\u001B[37m, \u001B[34m{1}\u001B[37m, \u001B[33m\"{2}\"\u001B[37m)",
                        consensusInstance, round, preparedValue.get()));

                prepareMessages.getMessages(consensusInstance, round).values().forEach(senderMessage -> {
                    ConsensusMessage m = new ConsensusMessageBuilder(config.getId(), Message.Type.COMMIT)
                            .setConsensusInstance(consensusInstance)
                            .setRound(round)
                            .setReplyTo(senderMessage.getSenderId())
                            .setReplyToMessageId(senderMessage.getMessageId())
                            .setMessage(new CommitMessage(preparedValue.get()).toJson())
                            .build();

                    this.authenticatedPerfectLink.send(senderMessage.getSenderId(), m);
                });
            }
        }
    }


    /**
     * Handle commit messages and decide if there is a valid quorum.
     *
     * @param message Message to be handled
     */
    public void uponCommit(ConsensusMessage message) {
        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();

        logger.info(MessageFormat.format("Received {0} from node \u001B[33m{1}\u001B[37m", message.getConsensusMessageRepresentation(), message.getSenderId()));

        commitMessages.addMessage(message);

        InstanceInfo instance = this.instanceInfo.get(consensusInstance);

        if (instance == null) {
            // Should never happen because only receives commit as a response to a prepare message
            logger.error(MessageFormat.format("\u001B[31mCRITICAL:\u001B[37m Received {0} from node \u001B[33m{1}\u001B[37m \u001B[31mBUT NO INSTANCE INFO\u001B[37m", message.getConsensusMessageRepresentation(), message.getSenderId()));
            return;
        }

        synchronized (commitLockObjects.computeIfAbsent(consensusInstance, k -> new Object())) {
            if (instance.alreadyDecided()) {
                logger.info(
                        MessageFormat.format(
                                "Received {0} from node \u001B[33m{1}\u001B[37m but already decided for Consensus Instance \u001B[34m{2}\u001B[37m, ignoring...",
                                message.getConsensusMessageRepresentation(), message.getSenderId(), consensusInstance));
                return;
            }

            Optional<String> commitValue = commitMessages.hasValidCommitQuorum(consensusInstance, round);

            if (commitValue.isPresent()) {
                stopTimer();

                instance.setDecidedRound(round);
                instance.setDecidedValue(commitValue.get());

                logger.info(MessageFormat.format("Decided on value \u001B[33m\"{0}\"\u001B[37m for Consensus Instance \u001B[34m{1}\u001B[37m, Round \u001B[34m{2}\u001B[37m successfully", commitValue.get(), consensusInstance, round));

                appendToLedger(consensusInstance, commitValue.get());

                Object waitObject = waitForConsensusObjects.computeIfAbsent(lastDecidedConsensusInstance.get() + 1, k -> new Object());
                synchronized (waitObject) {
                    lastDecidedConsensusInstance.getAndIncrement();
                    waitObject.notifyAll();
                }
            }
        }
    }

    /**
     * Append value to the ledger.
     *
     * @param consensusInstance Consensus instance
     * @param value             Value to append
     */
    private void appendToLedger(int consensusInstance, String value) {
        logger.info(MessageFormat.format("Appending or waiting to append value \u001B[33m\"{0}\"\u001B[37m to ledger...", value));

        waitForPreviousConsensus(consensusInstance); // TODO Optimize to not wait in the thread, store a list of consensus values that are to be appended later

        synchronized (ledger) {
            ledger.ensureCapacity(consensusInstance);

            ledger.add(value);

            logger.info(MessageFormat.format("Value \u001B[33m\"{0}\"\u001B[37m appended successfully to ledger. Current Ledger: \u001B[33m\"{1}\"\u001B[37m",
                    value, String.join(", ", ledger)));
        }
    }

    public void uponRoundChange(ConsensusMessage message) {
        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();

        logger.info(MessageFormat.format("Received {0} from node \u001B[33m{1}\u001B[37m", message.getConsensusMessageRepresentation(), message.getSenderId()));

        if (message.getPreparedRound() < message.getRound()) {
            roundChangeMessages.addMessage(message);
        }

        InstanceInfo instance = this.instanceInfo.get(consensusInstance);

        if (instance == null) {
            logger.error(MessageFormat.format("\u001B[31mCRITICAL:\u001B[37m Received {0} from node \u001B[33m{1}\u001B[37m \u001B[31mBUT NO INSTANCE INFO\u001B[37m", message.getConsensusMessageRepresentation(), message.getSenderId()));
            return;
        }

        if (instance.alreadyDecided()) {
            logger.info(MessageFormat.format("Received {0} from node \u001B[33m{1}\u001B[37m but already decided for Consensus Instance \u001B[34m{2}\u001B[37m, sending a \u001B[32mCOMMIT\u001B[37m back to sender",
                    message.getConsensusMessageRepresentation(), message.getSenderId(), consensusInstance));

            // TODO Instead send quorum of commits. How, without passing as an impersonator?
            this.authenticatedPerfectLink.send(message.getSenderId(),
                    new ConsensusMessageBuilder(config.getId(), Message.Type.COMMIT)
                            .setConsensusInstance(consensusInstance)
                            .setRound(instance.getDecidedRound())
                            .setReplyTo(message.getSenderId())
                            .setReplyToMessageId(message.getMessageId())
                            .setMessage(new CommitMessage(instance.getDecidedValue()).toJson())
                            .build()
            );

            return;
        }

        synchronized (roundChangeLockObjects.computeIfAbsent(consensusInstance, k -> new Object())) {
            if (instance.getCurrentRound() <= round) {
                List<ConsensusMessage> biggerRoundChangeMessages =
                        this.roundChangeMessages.getMessagesFromRoundGreaterThan(consensusInstance, round);
                int f = Math.floorDiv(nodesConfig.length - 1, 3);
                if (biggerRoundChangeMessages.size() >= f + 1) {
                    int newRound = biggerRoundChangeMessages.stream()
                            .mapToInt(ConsensusMessage::getRound)
                            .min().orElseThrow();

                    instance.setCurrentRound(newRound);

                    startTimer(consensusInstance);

                    ConsensusMessage messageToBroadcast = new ConsensusMessageBuilder(config.getId(), Message.Type.ROUND_CHANGE)
                            .setConsensusInstance(consensusInstance)
                            .setRound(newRound)
                            .setPreparedRound(instance.getPreparedRound())
                            .setPreparedValue(instance.getPreparedValue())
                            .build();

                    logger.info(MessageFormat.format("Updated round to \u001B[34m{0}\u001B[37m for Consensus Instance \u001B[34m{1}\u001B[37m. Broadcasting {2}", newRound, consensusInstance, messageToBroadcast.getConsensusMessageRepresentation()));

                    this.authenticatedPerfectLink.broadcast(messageToBroadcast);
                }
            }

            if (receivedRoundChangeQuorum.computeIfAbsent(consensusInstance, k -> new ConcurrentHashMap<>()).get(round) != null) {
                logger.info(MessageFormat.format("Already received quorum of \u001B[32mROUND_CHANGE\u001B[37m(\u001B[34m{0}\u001B[37m, \u001B[34m{1}\u001B[37m, _, _). Ignoring...",
                        consensusInstance, round));
                return;
            }

            Optional<PreparedRoundValuePair> highestPrepared = roundChangeMessages.getHighestPrepared(consensusInstance, round);

            var nodeIsLeader = isNodeLeader(consensusInstance, round, this.config.getId());

            if (nodeIsLeader && justifyRoundChange(consensusInstance, round) && highestPrepared.isPresent()) {
                receivedRoundChangeQuorum.get(consensusInstance).putIfAbsent(round, true);

                String valueToBroadcast = !highestPrepared.get().isNull()
                        ? highestPrepared.get().getValue()
                        : instance.getInputValue();

                ConsensusMessage messageToBroadcast = new ConsensusMessageBuilder(config.getId(), Message.Type.PRE_PREPARE)
                        .setConsensusInstance(consensusInstance)
                        .setRound(round)
                        .setMessage(new PrePrepareMessage(valueToBroadcast).toJson())
                        .build();

                logger.info(MessageFormat.format("Received quorum of \u001B[32mROUND_CHANGE\u001B[37m(\u001B[34m{0}\u001B[37m, \u001B[34m{1}\u001B[37m, _, _). Broadcasting {2}",
                        consensusInstance, round, messageToBroadcast.getConsensusMessageRepresentation()));

                this.authenticatedPerfectLink.broadcast(messageToBroadcast);
            }
        }
    }

    @Override
    public void listen() {
        try {
            // Thread to listen on every request
            new Thread(() -> {
                while (true) {
                    try {
                        Message message = this.authenticatedPerfectLink.receive();

                        // Separate thread to handle each message
                        new Thread(() -> {
                            switch (message.getType()) {
                                case PRE_PREPARE -> uponPrePrepare((ConsensusMessage) message);

                                case PREPARE -> uponPrepare((ConsensusMessage) message);

                                case COMMIT -> uponCommit((ConsensusMessage) message);

                                case ROUND_CHANGE -> uponRoundChange((ConsensusMessage) message);

                                case ACK -> {
                                    /*logger.info(MessageFormat.format("Received \u001B[32mACK\u001B[37m(\u001B[34m{0}\u001B[37m) from node \u001B[33m{1}\u001B[37m",
                                            message.getMessageId(), message.getSenderId()));*/
                                }

                                case IGNORE -> {
                                    /*logger.info(MessageFormat.format("\u001B[31mIGNORING\u001B[37m message with ID \u001B[34m{0}\u001B[37m from node \u001B[33m{1}\u001B[37m",
                                    message.getMessageId(), message.getSenderId()));
                                    * */
                                }

                                default -> logger.info(MessageFormat.format("Received unknown message from {0}",
                                        message.getSenderId()));
                            }
                        }).start();
                    } catch (Exception e) {
                        logger.error(MessageFormat.format("Error receiving message: {0}", e.getMessage()));
                        e.printStackTrace();
                    }
                }
            }).start();
        } catch (Exception e) {
            logger.error(MessageFormat.format("Error while listening: {0}", e.getMessage()));
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
        if (!roundChangeMessages.hasValidRoundChangeQuorum(consensusInstance, round))
            return false;

        return roundChangeMessages.getValidRoundChangeQuorumMessages(consensusInstance, round).stream()
                .allMatch((roundChangeMessage) ->
                        new PreparedRoundValuePair(
                                roundChangeMessage.getPreparedRound(),
                                roundChangeMessage.getPreparedValue()
                        ).isNull()
                )
                ||
                roundChangeMessages.getHighestPrepared(consensusInstance, round)
                        .map((highestPrepared) -> prepareMessages
                                .hasValidPrepareQuorum(consensusInstance, highestPrepared.getRound())
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
        InstanceInfo instance = instanceInfo.get(consensusInstance);

        long timeToWait = ROUND_CHANGE_TIMER_EXPIRE_TIME << (instance.getCurrentRound() - 1);

        logger.info(MessageFormat.format("Starting timer of \u001B[34m{0}\u001B[37mms for Consensus Instance \u001B[34m{1}\u001B[37m",
                timeToWait, consensusInstance));

        timer.startTimer(new TimerTask() {
            @Override
            public void run() {
                instance.setCurrentRound(instance.getCurrentRound() + 1);
                int round = instance.getCurrentRound();
                int preparedRound = instance.getPreparedRound();
                String preparedValue = instance.getPreparedValue();

                startTimer(consensusInstance);

                ConsensusMessage messageToBroadcast = new ConsensusMessageBuilder(config.getId(), Message.Type.ROUND_CHANGE)
                        .setConsensusInstance(consensusInstance)
                        .setRound(round)
                        .setPreparedRound(preparedRound)
                        .setPreparedValue(preparedValue)
                        .build();

                logger.info(MessageFormat.format("Timer expired for Consensus Instance \u001B[34m{0}\u001B[37m. Updated round to \u001B[34m{1}\u001B[37m, triggering round-change. Broadcasting {2}", consensusInstance, round, messageToBroadcast.getConsensusMessageRepresentation()));

                authenticatedPerfectLink.broadcast(messageToBroadcast);
            }
        }, timeToWait);
    }

    private void stopTimer() {
        timer.stopTimer();
    }

    static class MultiThreadTimer {
        private Timer timer;

        public MultiThreadTimer() {
            this.timer = new Timer();
        }

        public void startTimer(TimerTask task, long delay) {
            synchronized (this) {
                this.stopTimer();
                this.timer.schedule(task, delay);
            }
        }

        public void stopTimer() {
            synchronized (this) {
                this.timer.cancel();
                this.timer = new Timer();
            }
        }
    }

    /**
     * Waits for the previous consensus to be decided before starting a new one.
     *
     * @param localConsensusInstance current consensus instance waiting to start
     */
    private void waitForPreviousConsensus(int localConsensusInstance) {
        Object waitObject = waitForConsensusObjects.computeIfAbsent(localConsensusInstance - 1, k -> new Object());
        synchronized (waitObject) {
            while (lastDecidedConsensusInstance.get() < localConsensusInstance - 1) {
                try {
                    waitObject.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
