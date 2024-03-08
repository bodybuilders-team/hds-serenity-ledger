package pt.ulisboa.tecnico.hdsledger.service.models;

import pt.ulisboa.tecnico.hdsledger.communication.PrepareMessage;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Bucket for prepare messages.
 */
public class PrepareMessageBucket extends MessageBucket {

    public PrepareMessageBucket(int nodeCount) {
        super(nodeCount);
    }

    /**
     * Check if the bucket has a valid prepare quorum.
     *
     * @param nodeId   The node ID
     * @param instance The consensus instance
     * @param round    The round
     * @return The value if a valid prepare quorum exists
     */
    public Optional<String> hasValidPrepareQuorum(String nodeId, int instance, int round) { // TODO: nodeID is not used
        // Create mapping of value to frequency
        HashMap<String, Integer> frequency = new HashMap<>();
        bucket.get(instance).get(round).values().forEach(message -> {
            PrepareMessage prepareMessage = message.deserializePrepareMessage();
            String value = prepareMessage.getValue();
            frequency.put(value, frequency.getOrDefault(value, 0) + 1);
        });

        // Only one value (if any, thus the optional) will have a frequency
        // greater than or equal to the quorum size
        return frequency.entrySet().stream()
                .filter((Map.Entry<String, Integer> entry) -> entry.getValue() >= quorumSize)
                .map(Map.Entry::getKey)
                .findFirst();
    }
}
