package pt.ulisboa.tecnico.hdsledger.service.models;

import pt.ulisboa.tecnico.hdsledger.communication.ConsensusMessage;

import java.util.ArrayList;
import java.util.Collections;
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

    public List<ConsensusMessage> getValidRoundChangeQuorumMessages(int instance, int round) {
        if (!hasValidRoundChangeQuorum(instance, round))
            return Collections.emptyList();

        return new ArrayList<>(bucket.get(instance).get(round).values());
    }

    /**
     * Get the highest prepared pair from the existing round change quorum.
     *
     * @param instance The consensus instance
     * @param round    The round
     * @return The highest prepared pair (value, round) of the existing round change quorum
     */
    public Optional<PreparedRoundValuePair> getHighestPrepared(int instance, int round) {
        return getValidRoundChangeQuorumMessages(instance, round).stream()
                .max(Comparator.comparingInt(ConsensusMessage::getPreparedRound))
                .map(m -> new PreparedRoundValuePair(m.getPreparedRound(), m.getPreparedValue()));
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

