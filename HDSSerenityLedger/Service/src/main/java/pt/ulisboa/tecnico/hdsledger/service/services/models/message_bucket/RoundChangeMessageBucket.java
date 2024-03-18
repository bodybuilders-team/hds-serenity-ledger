package pt.ulisboa.tecnico.hdsledger.service.services.models.message_bucket;

import pt.ulisboa.tecnico.hdsledger.shared.communication.consensus_message.ConsensusMessageDto;
import pt.ulisboa.tecnico.hdsledger.shared.models.Block;
import pt.ulisboa.tecnico.hdsledger.shared.models.PreparedRoundValuePair;

import java.util.ArrayList;
import java.util.Comparator;
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
     * Get the highest prepared pair from the existing round change quorum.
     *
     * @param roundChanceQuorumMessages The messages in the round change quorum messages
     * @return The highest prepared pair (value, round) of the existing round change quorum
     */
    public static Optional<PreparedRoundValuePair> getHighestPrepared(List<ConsensusMessageDto> roundChanceQuorumMessages) {
        return roundChanceQuorumMessages.stream()
                .max(Comparator.comparingInt(ConsensusMessageDto::getPreparedRound))
                .map(m -> new PreparedRoundValuePair(m.getPreparedRound(), Block.fromJson(m.getPreparedValue())));
    }

    /**
     * Check if the bucket has a valid round change quorum.
     *
     * @param instance The consensus instance
     * @param round    The round
     * @return True if a valid round change quorum exists
     */
    public boolean hasValidRoundChangeQuorum(int instance, int round) {
        if (!bucket.containsKey(instance) || !bucket.get(instance).containsKey(round))
            return false;

        return bucket.get(instance).get(round).values().size() >= quorumSize;
    }

    public Optional<List<ConsensusMessageDto>> getValidRoundChangeQuorumMessages(int instance, int round) {
        if (!hasValidRoundChangeQuorum(instance, round))
            return Optional.empty();

        return Optional.of(new ArrayList<>(bucket.get(instance).get(round).values()));
    }

    /**
     * Get messages from rounds greater than the specified round.
     *
     * @param round The round
     * @return The messages
     */
    public List<ConsensusMessageDto> getMessagesFromRoundGreaterThan(int consensusInstance, int round) {
        List<ConsensusMessageDto> messages = new ArrayList<>();

        for (Map.Entry<Integer, Map<String, ConsensusMessageDto>> roundEntry : bucket.get(consensusInstance).entrySet()) {
            if (roundEntry.getKey() > round)
                messages.addAll(roundEntry.getValue().values());
        }

        return messages;
    }
}

