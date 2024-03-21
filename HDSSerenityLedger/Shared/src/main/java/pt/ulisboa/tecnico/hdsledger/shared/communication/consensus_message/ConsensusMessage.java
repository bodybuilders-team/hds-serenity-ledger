package pt.ulisboa.tecnico.hdsledger.shared.communication.consensus_message;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import pt.ulisboa.tecnico.hdsledger.shared.communication.Message;

import java.text.MessageFormat;

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
    private Object preparedValue;
    private Object value;
    // Who sent the previous message
    private String replyTo;
    // ID of the previous message
    private int replyToMessageId;

    public ConsensusMessage(String senderId, Type type) {
        super(senderId, type);
    }

    @Override
    public String toString() {
        switch (this.getType()) {
            case Type.PRE_PREPARE, Type.PREPARE, Type.COMMIT -> {
                return MessageFormat.format("<{0}({1}, {2}, \u001B[36m{3}\u001B[37m), messageId={4}>",
                        this.getType(),
                        this.getConsensusInstance(),
                        this.getRound(),
                        this.getValue(),
                        this.getMessageId()
                );
            }
            case Type.ROUND_CHANGE -> {
                return MessageFormat.format("<ROUND-CHANGE({0}, {1}, {2}, \u001B[36m{3}\u001B[37m), messageId={4}>",
                        this.getConsensusInstance(),
                        this.getRound(),
                        this.getPreparedRound(),
                        this.getPreparedValue() != null ? this.getPreparedValue() : "null",
                        this.getMessageId()
                );
            }
            default -> {
                return "NO REPRESENTATION";
            }
        }
    }
}

