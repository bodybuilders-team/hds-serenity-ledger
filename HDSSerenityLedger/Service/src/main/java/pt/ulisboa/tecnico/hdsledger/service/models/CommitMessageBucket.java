package pt.ulisboa.tecnico.hdsledger.service.models;

import pt.ulisboa.tecnico.hdsledger.communication.CommitMessage;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Bucket for commit messages.
 */
public class CommitMessageBucket extends MessageBucket {

    public CommitMessageBucket(int nodeCount) {
        super(nodeCount);
    }

    /**
     * Check if the bucket has a valid commit quorum.
     * <p>
     * Only one value, if any, will have a frequency greater than or equal to the quorum size.
     *
     * @param instance The consensus instance
     * @param round    The round
     * @return The value if a valid commit quorum exists
     */
    public Optional<String> hasValidCommitQuorum(int instance, int round) {
        if (!bucket.containsKey(instance) || !bucket.get(instance).containsKey(round))
            return Optional.empty();

        HashMap<String, Integer> frequency = new HashMap<>();
        bucket.get(instance).get(round).values().forEach(message -> {
            CommitMessage commitMessage = message.deserializeCommitMessage();
            String value = commitMessage.getValue();
            frequency.put(value, frequency.getOrDefault(value, 0) + 1);
        });

        return frequency.entrySet().stream()
                .filter(entry -> entry.getValue() >= quorumSize)
                .map(Map.Entry::getKey)
                .findFirst();
    }
}
