package pt.ulisboa.tecnico.hdsledger.shared.communication.consensus_message;

import lombok.Getter;
import lombok.Setter;
import pt.ulisboa.tecnico.hdsledger.shared.communication.Message;
import pt.ulisboa.tecnico.hdsledger.shared.models.ConsensusMessageValue;

import java.text.MessageFormat;

@Getter
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
    private ConsensusMessageValue preparedValue;
    // Who sent the previous message
    @Setter
    private String replyTo;
    // Id of the previous message
    @Setter
    private int replyToMessageId;
    // Value
    @Setter
    private ConsensusMessageValue value;

    public ConsensusMessage(String senderId, Type type) {
        super(senderId, type);
    }


    public String getConsensusMessageRepresentation() {
        switch (this.getType()) {
            case Type.PRE_PREPARE -> {
                return MessageFormat.format("PRE-PREPARE({0}, {1}, \"{2}\")",
                        this.getConsensusInstance(), this.getRound(),
                        this.getValue());
            }
            case Type.ROUND_CHANGE -> {
                return MessageFormat.format("ROUND-CHANGE({0}, {1}, {2}, \"{3}\")",
                        this.getConsensusInstance(), this.getRound(), this.getPreparedRound(), this.getPreparedValue());
            }
            default -> {
                return "NO REPRESENTATION";
            }
        }
    }
}

