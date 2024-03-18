package pt.ulisboa.tecnico.hdsledger.shared.communication.consensus_message;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import pt.ulisboa.tecnico.hdsledger.shared.communication.Message;

@Getter
@SuperBuilder
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
        final var dto = ConsensusMessageDtoConverter.convert(this);
        return dto.toString();
    }
}

