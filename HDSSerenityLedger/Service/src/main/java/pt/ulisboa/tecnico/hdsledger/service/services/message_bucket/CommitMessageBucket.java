package pt.ulisboa.tecnico.hdsledger.service.services.message_bucket;

import pt.ulisboa.tecnico.hdsledger.shared.communication.SignedMessage;
import pt.ulisboa.tecnico.hdsledger.shared.communication.consensus_message.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.shared.models.Block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
        bucket.get(instance).get(round).values().forEach(signedMessage -> {
            ConsensusMessage message = (ConsensusMessage) signedMessage.getMessage();
            Block value = message.getValue();
            frequency.put(value, frequency.getOrDefault(value, 0) + 1);
        });

        return frequency.entrySet().stream()
                .filter(entry -> entry.getValue() >= quorumSize)
                .map(Map.Entry::getKey)
                .findFirst();
    }

    /**
     * Get the messages for a given instance and round.
     *
     * @param instance The consensus instance
     * @param round    The round
     * @return The messages
     */
    public Optional<List<SignedMessage>> getValidCommitQuorumMessages(int instance, int round) {
        if (!bucket.containsKey(instance) || !bucket.get(instance).containsKey(round))
            return Optional.empty();

        HashMap<Block, List<SignedMessage>> messageList = new HashMap<>();

        bucket.get(instance).get(round).values().forEach(signedMessage -> {
            ConsensusMessage message = (ConsensusMessage) signedMessage.getMessage();
            Block value = message.getValue();
            List<SignedMessage> previousList = messageList.getOrDefault(value, new ArrayList<>());
            previousList.add(signedMessage);
            messageList.put(value, previousList);
        });

        return messageList.values().stream()
                .filter(list -> list.size() >= quorumSize)
                .findFirst();
    }
}
