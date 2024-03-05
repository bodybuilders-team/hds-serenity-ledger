package pt.ulisboa.tecnico.hdsledger.service.models;

import pt.ulisboa.tecnico.hdsledger.communication.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bucket to store consensus messages.
 */
public abstract class MessageBucket {

    protected static final CustomLogger LOGGER = new CustomLogger(MessageBucket.class.getName());

    protected final int quorumSize;

    // Instance -> Round -> Sender ID -> Consensus message
    protected final Map<Integer, Map<Integer, Map<String, ConsensusMessage>>> bucket = new ConcurrentHashMap<>();

    public MessageBucket(int nodeCount) {
        int f = Math.floorDiv(nodeCount - 1, 3);
        quorumSize = Math.floorDiv(nodeCount + f, 2) + 1;
    }

    /**
     * Add a message to the bucket.
     *
     * @param message The message to add
     */
    public void addMessage(ConsensusMessage message) {
        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();

        bucket.putIfAbsent(consensusInstance, new ConcurrentHashMap<>());
        bucket.get(consensusInstance).putIfAbsent(round, new ConcurrentHashMap<>());
        bucket.get(consensusInstance).get(round).put(message.getSenderId(), message);
    }

    /**
     * Get the messages for a given instance and round.
     *
     * @param instance The consensus instance
     * @param round    The round
     * @return The messages
     */
    public Map<String, ConsensusMessage> getMessages(int instance, int round) {
        return bucket.get(instance).get(round);
    }
}