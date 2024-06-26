package pt.ulisboa.tecnico.hdsledger.service.services;

import lombok.Getter;
import pt.ulisboa.tecnico.hdsledger.service.MessageAccumulator;
import pt.ulisboa.tecnico.hdsledger.service.services.message_bucket.CommitMessageBucket;
import pt.ulisboa.tecnico.hdsledger.service.services.message_bucket.PrepareMessageBucket;
import pt.ulisboa.tecnico.hdsledger.service.services.message_bucket.RoundChangeMessageBucket;
import pt.ulisboa.tecnico.hdsledger.shared.MultiThreadTimer;
import pt.ulisboa.tecnico.hdsledger.shared.communication.AuthenticatedPerfectLink;
import pt.ulisboa.tecnico.hdsledger.shared.communication.Message;
import pt.ulisboa.tecnico.hdsledger.shared.communication.SignedMessage;
import pt.ulisboa.tecnico.hdsledger.shared.communication.consensus_message.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.shared.config.ClientProcessConfig;
import pt.ulisboa.tecnico.hdsledger.shared.config.NodeProcessConfig;
import pt.ulisboa.tecnico.hdsledger.shared.config.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.shared.logger.ProcessLogger;
import pt.ulisboa.tecnico.hdsledger.shared.models.Block;
import pt.ulisboa.tecnico.hdsledger.shared.models.InstanceInfo;
import pt.ulisboa.tecnico.hdsledger.shared.models.Ledger;
import pt.ulisboa.tecnico.hdsledger.shared.models.PreparedRoundValuePair;

import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Service to handle consensus instances and ledger.
 */
public class NodeService implements UDPService {

    // Expire time for the round-change timer
    private static final long ROUND_CHANGE_TIMER_EXPIRE_TIME = 7000;
    private static final int STARTING_ROUND = 1;
    public final AtomicInteger lastDecidedConsensusInstance = new AtomicInteger(0);
    private final ProcessLogger logger;
    private final MessageAccumulator messageAccum;
    private final NodeProcessConfig[] nodesConfig; // All nodes configuration
    private final NodeProcessConfig config; // Current node configuration
    // Link to communicate with nodes
    private final AuthenticatedPerfectLink authenticatedPerfectLinkNode;
    // Link to communicate with clients
    private final AuthenticatedPerfectLink authenticatedPerfectLinkClient;
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
    private final AtomicInteger lastProposedConsensusInstance = new AtomicInteger(0);
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

    @Getter
    private final Ledger ledger;

    public NodeService(
            AuthenticatedPerfectLink authenticatedPerfectLinkNode,
            AuthenticatedPerfectLink authenticatedPerfectLinkClient,
            NodeProcessConfig config,
            NodeProcessConfig[] nodesConfig,
            ClientProcessConfig[] clientsConfig,
            MessageAccumulator messageAccum
    ) {
        this.authenticatedPerfectLinkNode = authenticatedPerfectLinkNode;
        this.authenticatedPerfectLinkClient = authenticatedPerfectLinkClient;
        this.config = config;
        this.nodesConfig = nodesConfig;

        this.prepareMessages = new PrepareMessageBucket(nodesConfig.length);
        this.commitMessages = new CommitMessageBucket(nodesConfig.length);
        this.roundChangeMessages = new RoundChangeMessageBucket(nodesConfig.length);

        this.logger = new ProcessLogger(NodeService.class.getName(), config.getId());
        this.messageAccum = messageAccum;
        this.ledger = new Ledger(clientsConfig, nodesConfig, config);
    }

    /**
     * Get the configuration of the node.
     *
     * @return The configuration
     */
    public ProcessConfig getConfig() {
        return this.config;
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
     * @param inputValueSupplier Value to value agreed upon
     */
    public void startConsensus(Supplier<Block> inputValueSupplier) {
        final int localConsensusInstance = getNextConsensusInstanceToPropose();

        logger.debug(MessageFormat.format("Starting consensus for instance {0} and waiting for previous consensus", localConsensusInstance));
        waitForPreviousConsensus(localConsensusInstance);
        logger.debug(MessageFormat.format("Previous consensus instance {0} decided", localConsensusInstance - 1));


        final var existingConsensus = this.instanceInfo.put(localConsensusInstance, new InstanceInfo());

        if (existingConsensus != null) {
            logger.info(MessageFormat.format("Node already started consensus for instance {0}", localConsensusInstance));
            return;
        }

        logger.debug(MessageFormat.format("Starting consensus for instance {0}", localConsensusInstance));

        final var nodeIsLeader = isNodeLeader(localConsensusInstance, STARTING_ROUND, this.config.getId());

        // Broadcasts PRE-PREPARE message
        if (nodeIsLeader && this.config.getBehavior() != ProcessConfig.ProcessBehavior.QUIET_LEADER
                || this.config.getBehavior() == ProcessConfig.ProcessBehavior.NON_LEADER_CONSENSUS_INITIATION
                || this.config.getBehavior() == ProcessConfig.ProcessBehavior.LEADER_IMPERSONATION
        ) {
            var inputValue = inputValueSupplier.get();

            logger.debug(MessageFormat.format("Proposing consensus for instance {0} with value {1}", localConsensusInstance, inputValue));

            filterRequests(inputValue);

            final var senderId = this.config.getBehavior() == ProcessConfig.ProcessBehavior.LEADER_IMPERSONATION
                    ? this.config.getId()
                    : getLeaderId(localConsensusInstance, STARTING_ROUND); // Impersonate leader

            final var messageToBroadcast = ConsensusMessage.builder()
                    .senderId(senderId)
                    .type(Message.Type.PRE_PREPARE)
                    .consensusInstance(localConsensusInstance)
                    .round(STARTING_ROUND)
                    .value(inputValue)
                    .messageId(-1)
                    .build();

            if (nodeIsLeader)
                logger.info(MessageFormat.format("Broadcasting {0} - Node is leader", messageToBroadcast));
            else if (this.config.getBehavior() == ProcessConfig.ProcessBehavior.NON_LEADER_CONSENSUS_INITIATION)
                logger.info(MessageFormat.format("Broadcasting {0} - Node is not leader, but still sending", messageToBroadcast));
            else
                logger.info(MessageFormat.format("Broadcasting {0} - Node is not leader, but is impersonating leader", messageToBroadcast));

            this.authenticatedPerfectLinkNode.broadcast(messageToBroadcast);
        } else {
            logger.info("Node is not leader, waiting for PRE-PREPARE message...");
        }

        // Start timer for the consensus instance
        startTimer(localConsensusInstance);
    }

    /**
     * Filters the requests in the block that are invalid, also removing them from the message accumulator.
     *
     * @param block Block to filter requests
     */
    private void filterRequests(Block block) {
        var iterator = block.getRequests().iterator();
        while (iterator.hasNext()) {
            var request = iterator.next();
            if (!ledger.validateRequest(request)) {
                logger.info(MessageFormat.format("Request {0} is invalid. Removing from block...", request));
                logger.debug(MessageFormat.format("Current ledger: {0}", ledger.getAccounts()));
                iterator.remove();
                messageAccum.remove(request);
            }
        }
    }

    /**
     * Get the next consensus instance to propose.
     *
     * @return The next consensus instance to propose
     */
    private int getNextConsensusInstanceToPropose() {
        final int localLastProposedConsensusInstance = this.lastProposedConsensusInstance.get();
        final int localLastDecidedConsensusInstance = this.lastDecidedConsensusInstance.get();

        if (localLastProposedConsensusInstance > localLastDecidedConsensusInstance) {
            return this.lastProposedConsensusInstance.incrementAndGet();
        } else {
            this.lastProposedConsensusInstance.set(localLastDecidedConsensusInstance + 1);
            return localLastDecidedConsensusInstance + 1;
        }
    }

    /**
     * Handle pre-prepare messages and if the message came from leader and is justified then broadcast prepare.
     *
     * @param signedMessage Signed message to be handled
     */
    public void uponPrePrepare(SignedMessage signedMessage) {
        ConsensusMessage message = ((ConsensusMessage) signedMessage.getMessage());
        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();
        Block value = message.getValue();
        String senderId = message.getSenderId();
        int senderMessageId = message.getMessageId();

        logger.info(MessageFormat.format("Received {0} from node {1}", message, senderId));

        if (!waitAndValidate(message)) {
            logger.info("Received invalid pre-prepare message. Ignoring... " + message);
            logger.debug(MessageFormat.format("Current ledger: {0}", ledger.getAccounts()));
            return;
        }

        if (!isNodeLeader(consensusInstance, round, senderId) || !justifyPrePrepare(consensusInstance, round, value)) {
            logger.info(MessageFormat.format("Received PRE-PREPARE({0}, {1}, _) from node {2}, but not justified. Replying to acknowledge reception", consensusInstance, round, senderId));

            // TODO Improve mechanism. Do not send ACK, keep receiving the same pre-prepare message without treating it as duplicate to eventually make the condition true

            Message responseMessage = new Message(this.config.getId(), Message.Type.ACK);
            responseMessage.setMessageId(senderMessageId);
            this.authenticatedPerfectLinkNode.send(senderId, responseMessage);
            return;
        }

        this.instanceInfo.putIfAbsent(consensusInstance, new InstanceInfo(value));
        receivedPrePrepare.putIfAbsent(consensusInstance, new ConcurrentHashMap<>());

        if (receivedPrePrepare.get(consensusInstance).put(round, true) != null)
            logger.info(MessageFormat.format("Already received PRE-PREPARE({0}, {1}, _) from node {2}, leader. Replying again to make sure it reaches the initial sender", consensusInstance, round, senderId));
        else
            startTimer(consensusInstance);

        ConsensusMessage messageToBroadcast = ConsensusMessage.builder()
                .senderId(config.getId())
                .type(Message.Type.PREPARE)
                .consensusInstance(consensusInstance)
                .round(round)
                .value(value)
                .replyTo(senderId)
                .replyToMessageId(senderMessageId)
                .messageId(-1)
                .build();

        logger.info(MessageFormat.format("PRE-PREPARE is justified. Broadcasting {0}", messageToBroadcast));

        this.authenticatedPerfectLinkNode.broadcast(messageToBroadcast);
    }

    /**
     * Handle prepare messages and if there is a valid quorum broadcast commit.
     *
     * @param signedMessage Signed message to be handled
     */
    public void uponPrepare(SignedMessage signedMessage) {
        ConsensusMessage message = ((ConsensusMessage) signedMessage.getMessage());
        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();
        Block block = message.getValue();
        String senderId = message.getSenderId();

        logger.info(MessageFormat.format("Received {0} from node {1}", message, senderId));

        if (!waitAndValidate(message)) {
            logger.info("Received invalid prepare message. Ignoring... " + message);
            logger.debug(MessageFormat.format("Current ledger: {0}", ledger.getAccounts()));
            return;
        }

        prepareMessages.addMessage(signedMessage);

        this.instanceInfo.putIfAbsent(consensusInstance, new InstanceInfo(block));
        InstanceInfo instance = this.instanceInfo.get(consensusInstance);

        synchronized (prepareLockObjects.computeIfAbsent(consensusInstance, k -> new Object())) {
            if (instance.getPreparedRound() != -1) {
                logger.info(MessageFormat.format("Already received quorum of PREPARE for Consensus Instance {0}. Replying with COMMIT to make sure it reaches the initial sender", consensusInstance));

                this.authenticatedPerfectLinkNode.send(
                        message.getSenderId(),
                        ConsensusMessage.builder()
                                .senderId(config.getId())
                                .type(Message.Type.COMMIT)
                                .consensusInstance(consensusInstance)
                                .round(instance.getPreparedRound())
                                .replyTo(message.getSenderId())
                                .replyToMessageId(message.getMessageId())
                                .value(instance.getPreparedValue())
                                .build()
                );

                return;
            }

            Optional<Block> preparedValue = prepareMessages.hasValidPrepareQuorum(consensusInstance, round);

            if (preparedValue.isPresent() && instance.getPreparedRound() < round) {
                synchronized (instance) {
                    instance.setPreparedRound(round);
                    instance.setPreparedValue(preparedValue.get());
                }

                // TODO Change to normal broadcast instead of sending only to those who sent prepare messages (needs ACK to be sent in all messages, though)

                logger.info(MessageFormat.format("Received quorum of PREPARE({0}, {1}, {2}). Broadcasting COMMIT({0}, {1}, \u001B[36m{2}\u001B[37m)", consensusInstance, round, preparedValue.get()));

                prepareMessages.getMessages(consensusInstance, round).values().forEach(senderSignedMessage -> {
                    ConsensusMessage senderMessage = (ConsensusMessage) senderSignedMessage.getMessage();
                    this.authenticatedPerfectLinkNode.send(
                            senderMessage.getSenderId(),
                            ConsensusMessage.builder()
                                    .senderId(config.getId())
                                    .type(Message.Type.COMMIT)
                                    .consensusInstance(consensusInstance)
                                    .round(round)
                                    .replyTo(senderMessage.getSenderId())
                                    .replyToMessageId(senderMessage.getMessageId())
                                    .value(preparedValue.get())
                                    .build()
                    );
                });
            }
        }
    }

    /**
     * Waits for the previous consensus to be decided and validates the block contained in the message.
     *
     * @param message Consensus message
     * @return True if the block is valid, false otherwise
     */
    public boolean waitAndValidate(ConsensusMessage message) {
        waitForPreviousConsensus(message.getConsensusInstance());
        final var block = message.getValue();

        return ledger.validateBlock(block);
    }


    /**
     * Handle commit messages and decide if there is a valid quorum.
     *
     * @param signedMessage Signed message to be handled
     */
    public void uponCommit(SignedMessage signedMessage) {
        ConsensusMessage message = ((ConsensusMessage) signedMessage.getMessage());
        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();

        logger.info(MessageFormat.format("Received {0} from node {1}", message, message.getSenderId()));

        if (!waitAndValidate(message)) {
            logger.info(MessageFormat.format("Received invalid commit message. Ignoring... {0}", message));
            logger.debug(MessageFormat.format("Current ledger: {0}", ledger.getAccounts()));
            return;
        }

        commitMessages.addMessage(signedMessage);

        InstanceInfo instance = this.instanceInfo.get(consensusInstance);

        if (instance == null) {
            // Should never happen because only receives commit as a response to a prepare message
            logger.error(MessageFormat.format("\u001B[31mCRITICAL:\u001B[37m Received {0} from node {1} \u001B[31mBUT NO INSTANCE INFO\u001B[37m", message, message.getSenderId()));
            return;
        }

        synchronized (decideLockObjects.computeIfAbsent(consensusInstance, k -> new Object())) {
            if (instance.alreadyDecided()) {
                logger.info(MessageFormat.format("Received {0} from node {1} but already decided for Consensus Instance {2}, ignoring...", message, message.getSenderId(), consensusInstance));
                return;
            }

            Optional<Block> commitValue = commitMessages.hasValidCommitQuorum(consensusInstance, round);

            if (commitValue.isPresent()) {
                stopTimer(consensusInstance);

                var block = commitValue.get();

                instance.setDecidedRound(round);
                instance.setDecidedValue(block);

                logger.info(MessageFormat.format("Decided on block {0} for Consensus Instance {1}, Round {2} successfully", commitValue.get(), consensusInstance, round));
                logger.info(MessageFormat.format("Starting or waiting to append block {0} to ledger...", commitValue.get()));

                waitForPreviousConsensus(consensusInstance); // TODO Optimize to not wait in the thread, store a list of consensus values that are to be appended later

                appendToLedger(block);

                int decidedConsensusInstance = lastDecidedConsensusInstance.incrementAndGet();
                Object waitObject = waitForConsensusObjects.computeIfAbsent(decidedConsensusInstance, k -> new Object());
                synchronized (waitObject) {
                    waitObject.notifyAll();
                }
            } else {
                logger.debug(MessageFormat.format("Did not receive quorum of COMMIT({0}, {1}, _) yet", consensusInstance, round));
                logger.debug(MessageFormat.format("Current quorum: {0}", commitMessages.getMessages(consensusInstance, round).values()));
            }
        }
    }

    /**
     * Append block to the ledger.
     *
     * @param block Block to append
     */
    private void appendToLedger(Block block) {
        logger.info(MessageFormat.format("Started to append block {0} to ledger decided on instance {1}...", block, lastDecidedConsensusInstance.get() + 1));

        synchronized (messageAccum) {
            synchronized (ledger) {
                // TODO: What to do if the block is not added (Should not happen in decide)
                if (!ledger.validateBlock(block))
                    logger.error(MessageFormat.format("Block {0} not added", block));

                var responses = ledger.addBlock(block);
                for (var response : responses)
                    authenticatedPerfectLinkClient.send(response.getOriginalRequestSenderId(), response);

                for (var request : block.getRequests())
                    messageAccum.remove(request);

                logger.info(MessageFormat.format("Appended block {0} to ledger", block));
                logger.debug(MessageFormat.format("Current ledger: {0}", ledger.getAccounts()));
            }
        }
    }


    /**
     * Handle round change messages and decide if there is a valid quorum.
     *
     * @param signedMessage Signed message to be handled
     */
    public void uponRoundChange(SignedMessage signedMessage) {
        ConsensusMessage message = ((ConsensusMessage) signedMessage.getMessage());
        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();

        logger.info(MessageFormat.format("Received {0} from node {1}", message, message.getSenderId()));

        if (!validateRoundChangeMessage(message)) {
            logger.debug(MessageFormat.format("Received invalid round-change message. Ignoring... {0}", message));
            return;
        }

        handleRoundChangePiggybackJustification(message, consensusInstance);

        roundChangeMessages.addMessage(signedMessage);

        InstanceInfo instance = this.instanceInfo.get(consensusInstance);

        if (instance == null) {
            logger.error(MessageFormat.format("\u001B[31mCRITICAL:\u001B[37m Received {0} from node {1} \u001B[31mBUT NO INSTANCE FOUND\u001B[37m", message, message.getSenderId()));
            return;
        }

        if (instance.alreadyDecided()) {
            logger.info(MessageFormat.format("Received {0} from node {1} but already decided for Consensus Instance {2}, sending the quorum of COMMIT back to sender", message, message.getSenderId(), consensusInstance));

            commitMessages.getValidCommitQuorumMessages(consensusInstance, instance.getDecidedRound()).ifPresent(commitQuorumMessages ->
                    commitQuorumMessages.forEach(commitQuorumSignedMessage -> {
                        this.authenticatedPerfectLinkNode.sendSignedMessage(
                                message.getSenderId(),
                                commitQuorumSignedMessage
                        );
                    }));

            return;
        }

        synchronized (roundChangeLockObjects.computeIfAbsent(consensusInstance, k -> new Object())) {
            if (instance.getCurrentRound() <= round) {
                List<SignedMessage> greaterRoundChangeMessages =
                        this.roundChangeMessages.getMessagesFromRoundGreaterThan(consensusInstance, round);
                int f = Math.floorDiv(nodesConfig.length - 1, 3);

                if (greaterRoundChangeMessages.size() >= f + 1) {
                    int newRound = greaterRoundChangeMessages.stream()
                            .mapToInt(signedRoundChangeMessage -> ((ConsensusMessage) signedRoundChangeMessage.getMessage()).getRound())
                            .min().orElseThrow();

                    instance.setCurrentRound(newRound);

                    ConsensusMessage messageToBroadcast = ConsensusMessage.builder()
                            .senderId(config.getId())
                            .type(Message.Type.ROUND_CHANGE)
                            .consensusInstance(consensusInstance)
                            .round(newRound)
                            .preparedRound(instance.getPreparedRound())
                            .preparedValue(instance.getPreparedValue())
                            .messageId(-1)
                            .build();

                    logger.info(MessageFormat.format("Updated round to {0} for Consensus Instance {1}. Broadcasting {2}", newRound, consensusInstance, messageToBroadcast));

                    startTimer(consensusInstance);
                    this.authenticatedPerfectLinkNode.broadcast(messageToBroadcast);
                }
            }

            if (receivedRoundChangeQuorum.computeIfAbsent(consensusInstance, k -> new ConcurrentHashMap<>()).get(round) != null) {
                logger.info(MessageFormat.format("Already received quorum of ROUND-CHANGE({0}, {1}, _, _). Ignoring...", consensusInstance, round));
                return;
            }

            var roundChangeQuorumMessages = roundChangeMessages.getValidRoundChangeQuorumMessages(consensusInstance, round).orElse(null);
            if (roundChangeQuorumMessages == null)
                return;

            Optional<PreparedRoundValuePair> highestPrepared = RoundChangeMessageBucket.getHighestPrepared(roundChangeQuorumMessages);

            var nodeIsLeader = isNodeLeader(consensusInstance, round, this.config.getId());

            if (nodeIsLeader && justifyRoundChange(consensusInstance, roundChangeQuorumMessages) && highestPrepared.isPresent()) {
                receivedRoundChangeQuorum.get(consensusInstance).putIfAbsent(round, true);

                Block inputValue = instance.getInputValue();
                if (inputValue == null) {
                    inputValue = messageAccum.getBlock();
                    instance.setInputValue(inputValue);

                    filterRequests(inputValue);
                }

                Block valueToBroadcast = !highestPrepared.get().isNull()
                        ? highestPrepared.get().value()
                        : instance.getInputValue();

                ConsensusMessage messageToBroadcast = ConsensusMessage.builder()
                        .senderId(config.getId())
                        .type(Message.Type.PRE_PREPARE)
                        .consensusInstance(consensusInstance)
                        .round(round)
                        .value(valueToBroadcast)
                        .messageId(-1)
                        .build();

                logger.info(MessageFormat.format("Received quorum of ROUND-CHANGE({0}, {1}, _, _). Broadcasting {2}", consensusInstance, round, messageToBroadcast));

                this.authenticatedPerfectLinkNode.broadcast(messageToBroadcast);
            }
        }
    }

    /**
     * Handles the prepare piggyback messages that are part of the justification of a round change message.
     *
     * @param message           The round change message to be justified
     * @param consensusInstance The consensus instance
     */
    private void handleRoundChangePiggybackJustification(ConsensusMessage message, int consensusInstance) {
        if (message.getPrepareQuorumPiggybackList() == null)
            return;

        message.getPrepareQuorumPiggybackList().forEach(signedPrepareMessage -> {
            ConsensusMessage prepareMessage = (ConsensusMessage) signedPrepareMessage.getMessage();

            if (!waitAndValidate(prepareMessage))
                return;

            if (prepareMessages.getMessages(consensusInstance, prepareMessage.getRound()).get(prepareMessage.getSenderId()) == null)
                uponPrepare(signedPrepareMessage);
        });
    }

    /**
     * Validate the round change message.
     * A round change message is valid if the prepared round is smaller than the round of the message and
     * the block contained in the prepared value is valid.
     *
     * @param message Consensus message
     * @return True if the message is valid
     */
    private boolean validateRoundChangeMessage(ConsensusMessage message) {
        if (message.getPreparedRound() >= message.getRound())
            return false;

        waitForPreviousConsensus(message.getConsensusInstance());

        if (message.getPreparedValue() == null || message.getPreparedRound() == -1)
            return true;

        final var block = message.getPreparedValue();

        return ledger.validateBlock(block);
    }

    @Override
    public void listen() {
        try {
            // Thread to listen on every request
            new Thread(() -> {
                while (true) {
                    try {
                        final var signedMessage = this.authenticatedPerfectLinkNode.receive();

                        if (!(signedMessage.getMessage() instanceof ConsensusMessage consensusMessage))
                            continue;

                        // Separate thread to handle each message
                        new Thread(() -> {
                            try {
                                switch (consensusMessage.getType()) {
                                    case PRE_PREPARE -> uponPrePrepare(signedMessage);

                                    case PREPARE -> uponPrepare(signedMessage);

                                    case COMMIT -> uponCommit(signedMessage);

                                    case ROUND_CHANGE -> uponRoundChange(signedMessage);

                                    case ACK -> {
                                        /*logger.info(MessageFormat.format("Received ACK({0}) from node {1}", message.getMessageId(), message.getSenderId()));*/
                                    }

                                    case IGNORE -> {
                                        /*logger.info(MessageFormat.format("\u001B[31mIGNORING\u001B[37m message with ID {0} from node {1}", message.getMessageId(), message.getSenderId()));*/
                                    }

                                    default ->
                                            logger.info(MessageFormat.format("Received unknown message from {0}", consensusMessage.getSenderId()));
                                }
                            } catch (Exception e) {
                                logger.error(MessageFormat.format("Error handling message: {0}", e.getMessage()));
                                e.printStackTrace();
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
    private boolean justifyRoundChange(int consensusInstance, List<SignedMessage> roundChangeQuorumMessages) {
        return roundChangeQuorumMessages.stream()
                .allMatch(roundChangeMessage -> {
                    ConsensusMessage consensusMessage = (ConsensusMessage) roundChangeMessage.getMessage();
                    return new PreparedRoundValuePair(
                            consensusMessage.getPreparedRound(),
                            consensusMessage.getPreparedValue()
                    ).isNull();
                })
                ||
                RoundChangeMessageBucket.getHighestPrepared(roundChangeQuorumMessages)
                        .map(highestPrepared ->
                                prepareMessages
                                        .hasValidPrepareQuorum(consensusInstance, highestPrepared.round())
                                        .map(value -> highestPrepared.value().equals(value))
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
                .allMatch(roundChangeMessage -> {
                    ConsensusMessage consensusMessage = (ConsensusMessage) roundChangeMessage.getMessage();
                    return new PreparedRoundValuePair(
                            consensusMessage.getPreparedRound(),
                            consensusMessage.getPreparedValue()
                    ).isNull();
                })
                ||
                (highestPreparedPair
                        .map(highestPrepared ->
                                prepareMessages
                                        .hasValidPrepareQuorum(consensusInstance, highestPrepared.round())
                                        .map(prepareMessageValue -> highestPrepared.value().equals(prepareMessageValue))
                                        .orElse(false)
                        ).orElse(false) &&
                        value.equals(highestPreparedPair.get().value()));
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
                    final int round;
                    final int preparedRound;
                    final Block preparedValue;

                    synchronized (instance) {
                        instance.setCurrentRound(instance.getCurrentRound() + 1);
                        round = instance.getCurrentRound();
                        preparedRound = instance.getPreparedRound();
                        preparedValue = instance.getPreparedValue();
                    }

                    final ConsensusMessage messageToBroadcast = ConsensusMessage.builder()
                            .senderId(config.getId())
                            .type(Message.Type.ROUND_CHANGE)
                            .consensusInstance(consensusInstance)
                            .round(round)
                            .preparedRound(preparedRound)
                            .preparedValue(preparedValue)
                            .prepareQuorumPiggybackList(prepareMessages.getValidPrepareQuorumMessages(consensusInstance, preparedRound).orElse(null))
                            .messageId(-1)
                            .build();

                    logger.info(MessageFormat.format("Timer expired for Consensus Instance {0}. Updated round to {1}, triggering round-change. Broadcasting {2}", consensusInstance, round, messageToBroadcast));

                    startTimer(consensusInstance);
                    authenticatedPerfectLinkNode.broadcast(messageToBroadcast);
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
                    logger.error(MessageFormat.format("Error while waiting for previous consensus: {0}", e.getMessage()));
                    e.printStackTrace();
                }
            }
        }
    }
}
