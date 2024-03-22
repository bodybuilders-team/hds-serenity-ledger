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
 * Bucket for prepare messages.
 */
public class PrepareMessageBucket extends MessageBucket {

    public PrepareMessageBucket(int nodeCount) {
        super(nodeCount);
    }

    /**
     * Check if the bucket has a valid prepare quorum.
     * <p>
     * Only one value, if any, will have a frequency greater than or equal to the quorum size.
     *
     * @param instance The consensus instance
     * @param round    The round
     * @return The value if a valid prepare quorum exists
     */
    public Optional<Block> hasValidPrepareQuorum(int instance, int round) {
        if (!bucket.containsKey(instance) || !bucket.get(instance).containsKey(round))
            return Optional.empty();

        HashMap<Block, Integer> frequency = new HashMap<>();
        bucket.get(instance).get(round).values().forEach(signedMessage -> {
            ConsensusMessage message = (ConsensusMessage) signedMessage.getMessage();
            Block block = message.getValue();
            frequency.put(block, frequency.getOrDefault(block, 0) + 1);
        });

        return frequency.entrySet().stream()
                .filter(entry -> entry.getValue() >= quorumSize)
                .map(Map.Entry::getKey)
                .findFirst();
    }

    /**
     * Get the valid prepare quorum messages for a given instance and round.
     *
     * @param instance The consensus instance
     * @param round    The round
     * @return The messages
     */
    public Optional<List<SignedMessage>> getValidPrepareQuorumMessages(int instance, int round) {
        if (!bucket.containsKey(instance) || !bucket.get(instance).containsKey(round))
            return Optional.empty();

        HashMap<Block, List<SignedMessage>> messageList = new HashMap<>();

        bucket.get(instance).get(round).values().forEach(signedMessage -> {
            ConsensusMessage message = (ConsensusMessage) signedMessage.getMessage();
            Block value = (Block) message.getValue();
            List<SignedMessage> previousList = messageList.getOrDefault(value, new ArrayList<>());
            previousList.add(signedMessage);
            messageList.put(value, previousList);
        });

        return messageList.values().stream()
                .filter(list -> list.size() >= quorumSize)
                .findFirst();
    }
}
