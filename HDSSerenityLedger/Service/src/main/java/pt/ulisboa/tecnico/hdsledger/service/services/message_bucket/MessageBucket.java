package pt.ulisboa.tecnico.hdsledger.service.services.message_bucket;

import pt.ulisboa.tecnico.hdsledger.shared.communication.SignedMessage;
import pt.ulisboa.tecnico.hdsledger.shared.communication.consensus_message.ConsensusMessage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bucket to store consensus messages.
 */
public abstract class MessageBucket {

    protected final int quorumSize;

    // Instance -> Round -> Sender ID -> Consensus message
    protected final Map<Integer, Map<Integer, Map<String, SignedMessage>>> bucket = new ConcurrentHashMap<>();

    protected MessageBucket(int nodeCount) {
        int f = Math.floorDiv(nodeCount - 1, 3);
        quorumSize = Math.floorDiv(nodeCount + f, 2) + 1;
    }

    /**
     * Add a message to the bucket.
     *
     * @param signedMessage The message to add
     */
    public void addMessage(SignedMessage signedMessage) {
        ConsensusMessage message = (ConsensusMessage) signedMessage.getMessage();
        int consensusInstance = message.getConsensusInstance();
        int round = message.getRound();

        bucket.putIfAbsent(consensusInstance, new ConcurrentHashMap<>());
        bucket.get(consensusInstance).putIfAbsent(round, new ConcurrentHashMap<>());
        bucket.get(consensusInstance).get(round).put(message.getSenderId(), signedMessage); // TODO putIfAbsent?
    }

    /**
     * Get the messages for a given instance and round.
     *
     * @param instance The consensus instance
     * @param round    The round
     * @return The messages
     */
    public Map<String, SignedMessage> getMessages(int instance, int round) {
        return bucket.get(instance).get(round);
    }
}