package pt.ulisboa.tecnico.hdsledger.service.services.models.message_bucket;

import pt.ulisboa.tecnico.hdsledger.shared.communication.consensus_message.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.shared.communication.consensus_message.ConsensusMessageDto;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bucket to store consensus messages.
 */
public abstract class MessageBucket {

    protected final int quorumSize;

    // Instance -> Round -> Sender ID -> Consensus message
    protected final Map<Integer, Map<Integer, Map<String, ConsensusMessage>>> bucket = new ConcurrentHashMap<>();

    protected MessageBucket(int nodeCount) {
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
        bucket.get(consensusInstance).get(round).put(message.getSenderId(), message); // TODO putIfAbsent?
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