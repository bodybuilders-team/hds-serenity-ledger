package pt.ulisboa.tecnico.hdsledger.shared.communication.consensus_message;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import pt.ulisboa.tecnico.hdsledger.shared.communication.Message;

import java.text.MessageFormat;

@Getter
@SuperBuilder
//@ToString(callSuper = true)
public class ConsensusMessage extends Message {

    // Consensus instance
    @Setter
    private int consensusInstance;
    // Round
    @Setter
    private int round;
    // Prepared round
    @Setter
    private int preparedRound;
    // Prepared value
    @Setter
    private Object preparedValue;
    // Who sent the previous message
    @Setter
    private String replyTo;
    // Id of the previous message
    @Setter
    private int replyToMessageId;
    // Value
    @Setter
    private Object value;

    public ConsensusMessage(String senderId, Type type) {
        super(senderId, type);
    }

    @Override
    public String toString() {
        switch (this.getType()) {
            case Type.PRE_PREPARE, Type.PREPARE, Type.COMMIT -> {
                return MessageFormat.format("{0}({1}, {2}, \u001B[36m{3}\u001B[37m)",
                        this.getType(),
                        this.getConsensusInstance(), this.getRound(),
                        this.getValue().hashCode());
            }
            case Type.ROUND_CHANGE -> {
                return MessageFormat.format("ROUND-CHANGE({0}, {1}, {2}, \u001B[36m{3}\u001B[37m)",
                        this.getConsensusInstance(), this.getRound(), this.getPreparedRound(),
                        this.getPreparedValue() != null ? this.getPreparedValue().hashCode() : "null");
            }
            default -> {
                return "NO REPRESENTATION";
            }
        }
    }
}

