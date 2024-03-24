package pt.ulisboa.tecnico.hdsledger.shared.communication.consensus_message;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import pt.ulisboa.tecnico.hdsledger.shared.communication.Message;
import pt.ulisboa.tecnico.hdsledger.shared.communication.SignedMessage;
import pt.ulisboa.tecnico.hdsledger.shared.models.Block;

import java.text.MessageFormat;
import java.util.List;
import java.util.Objects;

/**
 * The {@code ConsensusMessage} class represents a message that is used in the IBFT consensus algorithm.
 */
@Setter
@Getter
@SuperBuilder
public class ConsensusMessage extends Message {

    private int consensusInstance;
    private int round;
    private int preparedRound;
    private Block preparedValue;
    private Block value;
    // Who sent the previous message
    private String replyTo;
    // ID of the previous message
    private int replyToMessageId;

    // For round-change messages, piggyback justification of round-change
    private List<SignedMessage> prepareQuorumPiggybackList;

    public ConsensusMessage(String senderId, Type type) {
        super(senderId, type);
    }

    @Override
    public String toString() {
        switch (this.getType()) {
            case Type.PRE_PREPARE, Type.PREPARE, Type.COMMIT -> {
                return MessageFormat.format("<{0}({1}, {2}, {3}), messageId={4}>",
                        this.getType(),
                        this.getConsensusInstance(),
                        this.getRound(),
//                        this.getValue() != null ? this.getValue().hashCode() : "null",
                        this.getValue(),
                        this.getMessageId()
                );
            }
            case Type.ROUND_CHANGE -> {
                return MessageFormat.format("<ROUND-CHANGE({0}, {1}, {2}, {3}), messageId={4}>",
                        this.getConsensusInstance(),
                        this.getRound(),
                        this.getPreparedRound(),
//                        this.getPreparedValue() != null ? this.getPreparedValue().hashCode() : "null",
                        this.getPreparedValue(),
                        this.getMessageId()
                );
            }
            default -> {
                throw new IllegalStateException("Unexpected value: " + this.getType());
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        ConsensusMessage that = (ConsensusMessage) o;
        return consensusInstance == that.consensusInstance && round == that.round && preparedRound == that.preparedRound
                && replyToMessageId == that.replyToMessageId && Objects.equals(preparedValue, that.preparedValue)
                && Objects.equals(value, that.value) && Objects.equals(replyTo, that.replyTo)
                && Objects.equals(prepareQuorumPiggybackList, that.prepareQuorumPiggybackList);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), consensusInstance, round, preparedRound, preparedValue, value, replyTo, replyToMessageId);
    }
}

