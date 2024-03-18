package pt.ulisboa.tecnico.hdsledger.service.services;

import pt.ulisboa.tecnico.hdsledger.communication.AuthenticatedPerfectLink;
import pt.ulisboa.tecnico.hdsledger.service.services.models.message_bucket.CommitMessageBucket;
import pt.ulisboa.tecnico.hdsledger.service.services.models.message_bucket.PrepareMessageBucket;
import pt.ulisboa.tecnico.hdsledger.service.services.models.message_bucket.RoundChangeMessageBucket;
import pt.ulisboa.tecnico.hdsledger.shared.ProcessLogger;
import pt.ulisboa.tecnico.hdsledger.shared.communication.Message;
import pt.ulisboa.tecnico.hdsledger.shared.communication.consensus_message.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.shared.communication.consensus_message.ConsensusMessageBuilder;
import pt.ulisboa.tecnico.hdsledger.shared.communication.consensus_message.ConsensusMessageDto;
import pt.ulisboa.tecnico.hdsledger.shared.config.ClientProcessConfig;
import pt.ulisboa.tecnico.hdsledger.shared.config.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.shared.config.ServerProcessConfig;
import pt.ulisboa.tecnico.hdsledger.shared.models.Block;
import pt.ulisboa.tecnico.hdsledger.shared.models.InstanceInfo;
import pt.ulisboa.tecnico.hdsledger.shared.models.Ledger;
import pt.ulisboa.tecnico.hdsledger.shared.models.PreparedRoundValuePair;

import java.text.MessageFormat;
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

    // Expire time for the round-change timer
    private static final long ROUND_CHANGE_TIMER_EXPIRE_TIME = 1000;
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
    private final AtomicInteger currConsensusInstance = new AtomicInteger(0);
    // Last decided consensus instance
    private final AtomicInteger lastDecidedConsensusInstance = new AtomicInteger(0);
    // Ledger
    private final Ledger ledger;
    // Timers for the consensus instances, triggering round-change
    private final Map<Integer, MultiThreadTimer> timers = new ConcurrentHashMap<>();
    // Lock objects for prepare messages
    private final Map<Integer, Object> prepareLockObjects = new ConcurrentHashMap<>();
    // Lock objects for decides
    private final Map<Integer, Object> decideLockObjects = new ConcurrentHashMap<>();
    // Lock objects for round-change messages
    private final Map<Integer, Object> roundChangeLockObjects = new ConcurrentHashMap<>();
    // Wait for consensus object
    private final Map<Integer, Object> waitForConsensusObjects = new ConcurrentHashMap<>();

    public NodeService(AuthenticatedPerfectLink authenticatedPerfectLink, ServerProcessConfig config, ServerProcessConfig[] nodesConfig, ClientProcessConfig[] clientsConfig) {
        this.authenticatedPerfectLink = authenticatedPerfectLink;
        this.config = config;
        this.nodesConfig = nodesConfig;

        this.prepareMessages = new PrepareMessageBucket(nodesConfig.length);
        this.commitMessages = new CommitMessageBucket(nodesConfig.length);
        this.roundChangeMessages = new RoundChangeMessageBucket(nodesConfig.length);

        this.logger = new ProcessLogger(NodeService.class.getName(), config.getId());
        this.ledger = new Ledger(clientsConfig);
    }

    public ProcessConfig getConfig() {
        return this.config;
    }

    public Ledger getLedger() {
        return this.ledger;
    }

    /**
     * Get the leader id for a given consensus instance and round.
     * <p>
     * This is a deterministic function based on the consensus instance and round, that switches the leader of round 1
     * in every new consensus instance, such that every process is guaranteed to be the first leader the same number
     * of times (evenly distributed).
     *
     * @param consensusInstance The consensus instance
     * @param round             The round
     * @return The leader id
     */
    private String getLeaderId(int consensusInstance, int round) {
        return String.valueOf(((consensusInstance - 1 + round - 1) % this.nodesConfig.length) + 1);
    }

    /**
     * Check if the node is the leader for a given consensus instance and round.
     *
     * @param consensusInstance The consensus instance
     * @param round             The round
     * @param id                The node id
     * @return True if the node is the leader
     */
    private boolean isNodeLeader(int consensusInstance, int round, String id) {
        return getLeaderId(consensusInstance, round).equals(id);
    }

    /**
     * Start an instance of consensus for a value.
     * Only the current leader will start a consensus instance the remaining nodes only update values.
     *
     * @param inputValue Value to value agreed upon
     * @return True if proposal was started
     */
    public boolean startConsensus(Block inputValue) {
        final var localConsensusInstance = this.currConsensusInstance.incrementAndGet();
        final var existingConsensus = this.instanceInfo.put(localConsensusInstance, new InstanceInfo(inputValue));

        if (existingConsensus != null) {
            logger.info(MessageFormat.format("Node already started consensus for instance {0}", localConsensusInstance));
            return false;
        }

        final var nodeIsLeader = isNodeLeader(localConsensusInstance, STARTING_ROUND, this.config.getId());

        // Broadcasts PRE-PREPARE message
        if (nodeIsLeader
                || this.config.getBehavior() == ProcessConfig.ProcessBehavior.NON_LEADER_CONSENSUS_INITIATION
                || this.config.getBehavior() == ProcessConfig.ProcessBehavior.LEADER_IMPERSONATION
        ) {
            final var senderId = nodeIsLeader || this.config.getBehavior() == ProcessConfig.ProcessBehavior.NON_LEADER_CONSENSUS_INITIATION
                    ? this.config.getId()
                    : getLeaderId(localConsensusInstance, STARTING_ROUND); // Impersonate leader

            final var messageToBroadcast = ConsensusMessageDto.builder()
                    .senderId(senderId)
                    .type(Message.Type.PRE_PREPARE)
                    .consensusInstance(localConsensusInstance)
                    .round(STARTING_ROUND)
                    .value(inputValue.toJson())
                    .build();

            if (nodeIsLeader)
                logger.info(MessageFormat.format("Broadcasting {0} - Node is leader", messageToBroadcast.getConsensusMessageRepresentation()));
            else if (this.config.getBehavior() == ProcessConfig.ProcessBehavior.NON_LEADER_CONSENSUS_INITIATION)
                logger.info(MessageFormat.format("Broadcasting {0} - Node is not leader, but still sending", messageToBroadcast.getConsensusMessageRepresentation()));
            else
                logger.info(MessageFormat.format("Broadcasting {0} - Node is not leader, but is impersonating leader", messageToBroadcast.getConsensusMessageRepresentation()));

            this.authenticatedPerfectLink.broadcast(messageToBroadcast);
        } else {
            logger.info("Node is not leader, waiting for PRE-PREPARE message...");
        }

        // Start timer for the consensus instance
        startTimer(localConsensusInstance);

        return nodeIsLeader;
    }

    /**
     * Handle pre-prepare messages and if the message came from leader and is justified then broadcast prepare.
     *
     * @param message Message to be handled
     */
    public void uponPrePrepare(ConsensusMessage message) {
        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();
        Block value = (Block) message.getValue();
        String senderId = message.getSenderId();
        int senderMessageId = message.getMessageId();

        logger.info(MessageFormat.format("Received {0} from node \u001B[33m{1}\u001B[37m", message.getConsensusMessageRepresentation(), senderId));

        if (!isNodeLeader(consensusInstance, round, senderId) || !justifyPrePrepare(consensusInstance, round, value)) {
            logger.info(MessageFormat.format("Received \u001B[32mPRE-PREPARE\u001B[37m(\u001B[34m{0}\u001B[37m, \u001B[34m{1}\u001B[37m, _) from node \u001B[33m{2}\u001B[37m, but not justified. Replying to acknowledge reception", consensusInstance, round, senderId));

            // TODO Improve mechanism. Do not send ACK, keep receiving the same pre-prepare message without treating it as duplicate to eventually make the condition true

            Message responseMessage = new Message(this.config.getId(), Message.Type.ACK);
            responseMessage.setMessageId(senderMessageId);
            this.authenticatedPerfectLink.send(senderId, responseMessage);
            return;
        }

        this.instanceInfo.putIfAbsent(consensusInstance, new InstanceInfo(value));
        receivedPrePrepare.putIfAbsent(consensusInstance, new ConcurrentHashMap<>());

        if (receivedPrePrepare.get(consensusInstance).put(round, true) != null)
            logger.info(MessageFormat.format("Already received PRE-PREPARE({0}, {1}, _) from node {2}, leader. Replying again to make sure it reaches the initial sender", consensusInstance, round, senderId));
        else
            startTimer(consensusInstance);

        ConsensusMessageDto messageToBroadcast = ConsensusMessageDto.builder()
                .senderId(config.getId())
                .type(Message.Type.PREPARE)
                .consensusInstance(consensusInstance)
                .round(round)
                .value(value.toJson())
                .replyTo(senderId)
                .replyToMessageId(senderMessageId)
                .build();

        logger.info(MessageFormat.format("PRE-PREPARE is justified. Broadcasting {0}", messageToBroadcast.getMessageRepresentation()));

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
        Block value = (Block) message.getValue();
        String senderId = message.getSenderId();

        logger.info(MessageFormat.format("Received {0} from node {1}", message.getConsensusMessageRepresentation(), senderId));

        if (!validate(message)) {

        }

        prepareMessages.addMessage(message);

        this.instanceInfo.putIfAbsent(consensusInstance, new InstanceInfo(value));
        InstanceInfo instance = this.instanceInfo.get(consensusInstance);

        synchronized (prepareLockObjects.computeIfAbsent(consensusInstance, k -> new Object())) {
            if (instance.getPreparedRound() != -1) {
                logger.info(MessageFormat.format("Already received quorum of PREPARE for Consensus Instance {0}. Replying with COMMIT to make sure it reaches the initial senders of {1}", consensusInstance, message.getConsensusMessageRepresentation()));

                // TODO Change to normal broadcast instead of sending only to those who sent prepare messages (needs ACK to be sent in all messages, though)
                prepareMessages.getMessages(consensusInstance, round).values().forEach(senderMessage ->
                        this.authenticatedPerfectLink.send(
                                senderMessage.getSenderId(),
                                ConsensusMessageDto.builder()
                                        .senderId(config.getId())
                                        .type(Message.Type.COMMIT)
                                        .consensusInstance(consensusInstance)
                                        .round(instance.getPreparedRound())
                                        .replyTo(senderMessage.getSenderId())
                                        .replyToMessageId(senderMessage.getMessageId())
                                        .value(instance.getPreparedValue().toJson())
                                        .build()
                        )
                );

                return;
            }

            Optional<Block> preparedValue = prepareMessages.hasValidPrepareQuorum(consensusInstance, round);

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

                logger.info(MessageFormat.format("Received quorum of PREPARE({0}, {1}, \"{2}\"). Broadcasting COMMIT({0}, {1}, \"{2}\")", consensusInstance, round, preparedValue.get()));

                prepareMessages.getMessages(consensusInstance, round).values().forEach(senderMessage ->
                        this.authenticatedPerfectLink.send(
                                senderMessage.getSenderId(),
                                ConsensusMessageDto.builder()
                                        .senderId(config.getId())
                                        .type(Message.Type.COMMIT)
                                        .consensusInstance(consensusInstance)
                                        .round(round)
                                        .replyTo(senderMessage.getSenderId())
                                        .replyToMessageId(senderMessage.getMessageId())
                                        .value(preparedValue.get().toJson())
                                        .build()
                        )
                );
            }
        }
    }

    public boolean validate(ConsensusMessage message) {

        return true;
    }


    /**
     * Handle commit messages and decide if there is a valid quorum.
     *
     * @param message Message to be handled
     */
    public void uponCommit(ConsensusMessage message) {
        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();

        logger.info(MessageFormat.format("Received {0} from node {1}", message.getConsensusMessageRepresentation(), message.getSenderId()));

        commitMessages.addMessage(message);

        InstanceInfo instance = this.instanceInfo.get(consensusInstance);

        if (instance == null) {
            // Should never happen because only receives commit as a response to a prepare message
            logger.error(MessageFormat.format("\u001B[31mCRITICAL:\u001B[37m Received {0} from node {1} \u001B[31mBUT NO INSTANCE INFO\u001B[37m", message.getConsensusMessageRepresentation(), message.getSenderId()));
            return;
        }

        synchronized (decideLockObjects.computeIfAbsent(consensusInstance, k -> new Object())) {
            if (instance.alreadyDecided()) {
                logger.info(MessageFormat.format("Received {0} from node {1} but already decided for Consensus Instance {2}, ignoring...", message.getConsensusMessageRepresentation(), message.getSenderId(), consensusInstance));
                return;
            }

            Optional<Block> commitValue = commitMessages.hasValidCommitQuorum(consensusInstance, round);

            if (commitValue.isPresent()) {
                stopTimer(consensusInstance);

                var block = commitValue.get();

                instance.setDecidedRound(round);
                instance.setDecidedValue(block);

                logger.info(MessageFormat.format("Decided on value \"{0}\" for Consensus Instance {1}, Round {2} successfully", commitValue.get(), consensusInstance, round));
                logger.info(MessageFormat.format("Appending or waiting to append value \"{0}\" to ledger...", commitValue.get()));

                waitForPreviousConsensus(consensusInstance); // TODO Optimize to not wait in the thread, store a list of consensus values that are to be appended later

                appendToLedger(consensusInstance, block);

                int decidedConsensusInstance = lastDecidedConsensusInstance.incrementAndGet();
                Object waitObject = waitForConsensusObjects.computeIfAbsent(decidedConsensusInstance, k -> new Object());
                synchronized (waitObject) {
                    waitObject.notifyAll();
                }
            }
        }
    }

    /**
     * Append block to the ledger.
     *
     * @param consensusInstance Consensus instance
     * @param block             Block to append
     */
    private void appendToLedger(int consensusInstance, Block block) {
        logger.info(MessageFormat.format("Appending or waiting to append value \u001B[33m\"{0}\"\u001B[37m to ledger...", block));

        waitForPreviousConsensus(consensusInstance); // TODO Optimize to not wait in the thread, store a list of consensus values that are to be appended later

        synchronized (ledger) {
            var added = ledger.addBlock(block);

            if (added)
                logger.info(MessageFormat.format("Appended block \u001B[33m\"{0}\"\u001B[37m to ledger", block));
            else //TODO: What to do if the block is not added (Should not happen in decide)
                logger.info(MessageFormat.format("Block \u001B[33m\"{0}\"\u001B[37m not added", block));
        }
    }


    /**
     * Handle round change messages and decide if there is a valid quorum.
     *
     * @param message Message to be handled
     */
    public void uponRoundChange(ConsensusMessage message) {
        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();

        logger.info(MessageFormat.format("Received {0} from node {1}", message.getConsensusMessageRepresentation(), message.getSenderId()));

        if (message.getPreparedRound() < message.getRound())
            roundChangeMessages.addMessage(message);

        InstanceInfo instance = this.instanceInfo.get(consensusInstance);

        if (instance == null) {
            logger.error(MessageFormat.format("\u001B[31mCRITICAL:\u001B[37m Received {0} from node {1} \u001B[31mBUT NO INSTANCE INFO\u001B[37m", message.getConsensusMessageRepresentation(), message.getSenderId()));
            return;
        }

        if (instance.alreadyDecided()) {
            logger.info(MessageFormat.format("Received {0} from node {1} but already decided for Consensus Instance {2}, sending a COMMIT back to sender", message.getConsensusMessageRepresentation(), message.getSenderId(), consensusInstance));

            // TODO Instead send quorum of commits. How, without passing as an impersonator?
            // TODO Maybe send the commits to the guy, and then he will communicate with the others to verify
            this.authenticatedPerfectLink.send(
                    message.getSenderId(),
                    ConsensusMessageDto.builder()
                            .senderId(config.getId())
                            .type(Message.Type.COMMIT)
                            .consensusInstance(consensusInstance)
                            .round(instance.getDecidedRound())
                            .replyTo(message.getSenderId())
                            .replyToMessageId(message.getMessageId())
                            .value(instance.getDecidedValue().toJson())
                            .build()
            );
            return;
        }

        synchronized (roundChangeLockObjects.computeIfAbsent(consensusInstance, k -> new Object())) {
            if (instance.getCurrentRound() <= round) {
                List<ConsensusMessage> greaterRoundChangeMessages =
                        this.roundChangeMessages.getMessagesFromRoundGreaterThan(consensusInstance, round);
                int f = Math.floorDiv(nodesConfig.length - 1, 3);

                if (greaterRoundChangeMessages.size() >= f + 1) {
                    int newRound = greaterRoundChangeMessages.stream()
                            .mapToInt(ConsensusMessage::getRound)
                            .min().orElseThrow();

                    instance.setCurrentRound(newRound);

                    startTimer(consensusInstance);

                    ConsensusMessageDto messageToBroadcast = ConsensusMessageDto.builder()
                            .senderId(config.getId())
                            .type(Message.Type.ROUND_CHANGE)
                            .consensusInstance(consensusInstance)
                            .round(newRound)
                            .preparedRound(instance.getPreparedRound())
                            .preparedValue(instance.getPreparedValue().toJson())
                            .build();

                    logger.info(MessageFormat.format("Updated round to {0} for Consensus Instance {1}. Broadcasting {2}", newRound, consensusInstance, messageToBroadcast.getConsensusMessageRepresentation()));

                    this.authenticatedPerfectLink.broadcast(messageToBroadcast);
                }
            }

            if (receivedRoundChangeQuorum.computeIfAbsent(consensusInstance, k -> new ConcurrentHashMap<>()).get(round) != null) {
                logger.info(MessageFormat.format("Already received quorum of ROUND_CHANGE({0}, {1}, _, _). Ignoring...", consensusInstance, round));
                return;
            }

            var roundChangeQuorumMessages = roundChangeMessages.getValidRoundChangeQuorumMessages(consensusInstance, round).orElse(null);
            if (roundChangeQuorumMessages == null)
                return;

            Optional<PreparedRoundValuePair> highestPrepared = RoundChangeMessageBucket.getHighestPrepared(roundChangeQuorumMessages);

            var nodeIsLeader = isNodeLeader(consensusInstance, round, this.config.getId());

            if (nodeIsLeader && justifyRoundChange(consensusInstance, roundChangeQuorumMessages) && highestPrepared.isPresent()) {
                receivedRoundChangeQuorum.get(consensusInstance).putIfAbsent(round, true);

                Block valueToBroadcast = !highestPrepared.get().isNull()
                        ? highestPrepared.get().getValue()
                        : instance.getInputValue();

                ConsensusMessageDto messageToBroadcast = ConsensusMessageDto.builder()
                        .senderId(config.getId())
                        .type(Message.Type.PRE_PREPARE)
                        .consensusInstance(consensusInstance)
                        .round(round)
                        .value(valueToBroadcast.toJson())
                        .build();

                logger.info(MessageFormat.format("Received quorum of ROUND_CHANGE({0}, {1}, _, _). Broadcasting {2}", consensusInstance, round, messageToBroadcast.getConsensusMessageRepresentation()));

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
                        final var consensusMessage = (ConsensusMessage) this.authenticatedPerfectLink.receive();

                        // Separate thread to handle each message
                        new Thread(() -> {
                            switch (consensusMessage.getType()) {
                                case PRE_PREPARE -> uponPrePrepare(consensusMessage);

                                case PREPARE -> uponPrepare(consensusMessage);

                                case COMMIT -> uponCommit(consensusMessage);

                                case ROUND_CHANGE -> uponRoundChange(consensusMessage);

                                case ACK -> {
                                    /*logger.info(MessageFormat.format("Received ACK({0}) from node {1}",
                                            message.getMessageId(), message.getSenderId()));*/
                                }

                                case IGNORE -> {
                                    /*logger.info(MessageFormat.format("\u001B[31mIGNORING\u001B[37m message with ID {0} from node {1}",
                                    message.getMessageId(), message.getSenderId()));
                                    * */
                                }

                                default ->
                                        logger.info(MessageFormat.format("Received unknown message from {0}", consensusMessage.getSenderId()));
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
     * @param consensusInstance         Consensus instance
     * @param roundChangeQuorumMessages List of round change messages
     * @return True if the round change is justified
     */
    private boolean justifyRoundChange(int consensusInstance, List<ConsensusMessage> roundChangeQuorumMessages) {
        return roundChangeQuorumMessages.stream()
                .allMatch(roundChangeMessage ->
                        new PreparedRoundValuePair(
                                roundChangeMessage.getPreparedRound(),
                                (Block) roundChangeMessage.getPreparedValue()
                        ).isNull()
                )
                ||
                RoundChangeMessageBucket.getHighestPrepared(roundChangeQuorumMessages)
                        .map(highestPrepared ->
                                prepareMessages
                                        .hasValidPrepareQuorum(consensusInstance, highestPrepared.getRound())
                                        .map(value -> highestPrepared.getValue().equals(value))
                                        .orElse(false)
                        ).orElse(false);

    }

    /**
     * A pre-prepare message is justified if:
     * <p>
     * 1. Round is 1 (starting round)
     * <p>
     * 2. There is a valid quorum of round change messages such that their prepared round and prepared value are null
     * <p>
     * 3. There is a valid quorum of prepare messages such that their prepared pair is the same as the highest prepared pair
     * AND the value of the pre-prepare message is the same as the highest prepared value
     *
     * @param consensusInstance Consensus instance
     * @param round             Consensus round
     * @param value             Value in pre-prepare message
     * @return True if the pre-prepare message is justified
     */
    private boolean justifyPrePrepare(int consensusInstance, int round, Block value) {
        if (round == STARTING_ROUND)
            return true;

        var roundChangeQuorumMessages = roundChangeMessages.getValidRoundChangeQuorumMessages(consensusInstance, round).orElse(null);
        if (roundChangeQuorumMessages == null)
            return false;

        Optional<PreparedRoundValuePair> highestPreparedPair = RoundChangeMessageBucket.getHighestPrepared(roundChangeQuorumMessages);

        return roundChangeQuorumMessages.stream()
                .allMatch(roundChangeMessage ->
                        new PreparedRoundValuePair(
                                roundChangeMessage.getPreparedRound(),
                                (Block) roundChangeMessage.getPreparedValue()
                        ).isNull()
                )
                ||
                (highestPreparedPair
                        .map(highestPrepared ->
                                prepareMessages
                                        .hasValidPrepareQuorum(consensusInstance, highestPrepared.getRound())
                                        .map(prepareMessageValue -> highestPrepared.getValue().equals(prepareMessageValue))
                                        .orElse(false)
                        ).orElse(false) &&
                        value.equals(highestPreparedPair.get().getValue()));
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

        synchronized (decideLockObjects.computeIfAbsent(consensusInstance, k -> new Object())) {
            if (instance.alreadyDecided())
                return;

            logger.info(MessageFormat.format("Starting timer of {0}ms for Consensus Instance {1}", timeToWait, consensusInstance));

            MultiThreadTimer timer = timers.computeIfAbsent(consensusInstance, k -> new MultiThreadTimer());
            timer.startTimer(new TimerTask() {
                @Override
                public void run() {
                    instance.setCurrentRound(instance.getCurrentRound() + 1);
                    int round = instance.getCurrentRound();
                    int preparedRound = instance.getPreparedRound();
                    Block preparedValue = instance.getPreparedValue();

                    startTimer(consensusInstance);

                ConsensusMessageDto messageToBroadcast = ConsensusMessageDto.builder()
                        .senderId(config.getId())
                        .type(Message.Type.ROUND_CHANGE)
                        .consensusInstance(consensusInstance)
                        .round(round)
                        .preparedRound(preparedRound)
                        .preparedValue(preparedValue.toJson())
                        .build();

                    logger.info(MessageFormat.format("Timer expired for Consensus Instance {0}. Updated round to {1}, triggering round-change. Broadcasting {2}", consensusInstance, round, messageToBroadcast.getConsensusMessageRepresentation()));

                    authenticatedPerfectLink.broadcast(messageToBroadcast);
                }
            }, timeToWait);
        }
    }

    /**
     * Stop the timer for the consensus instance.
     */
    private void stopTimer(int consensusInstance) {
        MultiThreadTimer timer = timers.get(consensusInstance);

        if (timer == null)
            return;

        timer.stopTimer();
    }

    /**
     * Waits for the previous consensus to be decided before starting a new one.
     *
     * @param localConsensusInstance current consensus instance waiting to start
     */
    private void waitForPreviousConsensus(int localConsensusInstance) {
        int previousConsensusInstance = localConsensusInstance - 1;
        Object waitObject = waitForConsensusObjects.computeIfAbsent(previousConsensusInstance, k -> new Object());
        synchronized (waitObject) {
            while (lastDecidedConsensusInstance.get() < previousConsensusInstance) {
                try {
                    waitObject.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Multi-thread timer to handle multiple timers at the same time.
     */
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
}
