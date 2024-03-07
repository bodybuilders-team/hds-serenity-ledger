package pt.ulisboa.tecnico.hdsledger.service.models;

import pt.ulisboa.tecnico.hdsledger.communication.CommitMessage;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class CommitMessageBucket extends MessageBucket {
    public CommitMessageBucket(int nodeCount) {
        super(nodeCount);
    }

    /**
     * Check if the bucket has a valid commit quorum.
     *
     * @param nodeId   The node ID
     * @param instance The consensus instance
     * @param round    The round
     * @return The value if a valid commit quorum exists
     */
    public Optional<String> hasValidCommitQuorum(String nodeId, int instance, int round) {
        // Create mapping of value to frequency
        HashMap<String, Integer> frequency = new HashMap<>();
        bucket.get(instance).get(round).values().forEach((message) -> {
            CommitMessage commitMessage = message.deserializeCommitMessage();
            String value = commitMessage.getValue();
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
