package pt.ulisboa.tecnico.hdsledger.service.models;

import pt.ulisboa.tecnico.hdsledger.communication.ConsensusMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Bucket for round change messages.
 */
public class RoundChangeMessageBucket extends MessageBucket {

    public RoundChangeMessageBucket(int nodeCount) {
        super(nodeCount);
    }

    /**
     * Check if the bucket has a valid round change quorum.
     *
     * @param nodeId   The node ID
     * @param instance The consensus instance
     * @param round    The round
     * @return True if a valid round change quorum exists
     */
    public boolean hasValidRoundChangeQuorum(String nodeId, int instance, int round) { // TODO: nodeID is not used
        return bucket.get(instance).get(round).values().size() >= quorumSize;
    }

    /**
     * Get the highest prepared pair from the existing round change quorum.
     * <p>
     * Only one pair, if any, will have a frequency greater than or equal to the quorum size.
     *
     * @param nodeId   The node ID
     * @param instance The consensus instance
     * @param round    The round
     * @return The highest prepared pair (value, round) of the existing round change quorum
     */
    public Optional<PreparedRoundValuePair> getHighestPrepared(String nodeId, int instance, int round) { // TODO: nodeID is not used
        HashMap<PreparedRoundValuePair, Integer> frequency = new HashMap<>();
        bucket.get(instance).get(round).values().forEach(message -> {
            var preparedRoundValuePair = new PreparedRoundValuePair(message.getPreparedRound(), message.getPreparedValue());
            frequency.put(preparedRoundValuePair, frequency.getOrDefault(preparedRoundValuePair, 0) + 1);
        });

        return frequency.entrySet().stream()
                .filter((Map.Entry<PreparedRoundValuePair, Integer> entry) -> entry.getValue() >= quorumSize)
                .map(Map.Entry::getKey)
                .findFirst();
    }

    /**
     * Get messages from rounds greater than the specified round.
     *
     * @param round The round
     * @return The messages
     */
    public List<ConsensusMessage> getMessagesFromRoundGreaterThan(int consensusInstance, int round) {
        List<ConsensusMessage> messages = new ArrayList<>();

        for (Map.Entry<Integer, Map<String, ConsensusMessage>> roundEntry : bucket.get(consensusInstance).entrySet()) {
            if (roundEntry.getKey() > round) {
                messages.addAll(roundEntry.getValue().values());
            }
        }

        return messages;
    }
}

