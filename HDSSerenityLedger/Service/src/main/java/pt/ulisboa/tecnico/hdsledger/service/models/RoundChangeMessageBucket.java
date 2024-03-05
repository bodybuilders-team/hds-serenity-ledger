package pt.ulisboa.tecnico.hdsledger.service.models;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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
    public boolean hasValidRoundChangeQuorum(String nodeId, int instance, int round) {
        return bucket.get(instance).get(round).values().size() >= quorumSize;
    }

    /**
     * Get the highest prepared pair from the existing round change quorum.
     *
     * @param nodeId   The node ID
     * @param instance The consensus instance
     * @param round    The round
     * @return The highest prepared pair (value, round) of the existing round change quorum
     */
    public Optional<PreparedRoundValuePair> getHighestPrepared(String nodeId, int instance, int round) {
        // Create mapping of value (round) to frequency
        HashMap<PreparedRoundValuePair, Integer> frequency = new HashMap<>();
        bucket.get(instance).get(round).values().forEach((message) -> {
            PreparedRoundValuePair preparedRoundValuePair = new PreparedRoundValuePair(message.getPreparedRound(), message.getPreparedValue());
            frequency.put(preparedRoundValuePair, frequency.getOrDefault(preparedRoundValuePair, 0) + 1);
        });

        // Only one value (if any, thus the optional) will have a frequency
        // greater than or equal to the quorum size
        return frequency.entrySet().stream()
                .filter((Map.Entry<PreparedRoundValuePair, Integer> entry) -> entry.getValue() >= quorumSize)
                .map(Map.Entry::getKey)
                .findFirst();
    }
}

