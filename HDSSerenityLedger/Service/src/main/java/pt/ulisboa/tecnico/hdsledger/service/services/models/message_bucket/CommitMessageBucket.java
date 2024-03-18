package pt.ulisboa.tecnico.hdsledger.service.services.models.message_bucket;

import pt.ulisboa.tecnico.hdsledger.shared.models.Block;

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
     * @return The block if a valid commit quorum exists
     */
    public Optional<Block> hasValidCommitQuorum(int instance, int round) {
        if (!bucket.containsKey(instance) || !bucket.get(instance).containsKey(round))
            return Optional.empty();

        HashMap<Block, Integer> frequency = new HashMap<>();
        bucket.get(instance).get(round).values().forEach(message -> {
            Block value = (Block) message.getValue();
            frequency.put(value, frequency.getOrDefault(value, 0) + 1);
        });

        return frequency.entrySet().stream()
                .filter(entry -> entry.getValue() >= quorumSize)
                .map(Map.Entry::getKey)
                .findFirst();
    }
}
