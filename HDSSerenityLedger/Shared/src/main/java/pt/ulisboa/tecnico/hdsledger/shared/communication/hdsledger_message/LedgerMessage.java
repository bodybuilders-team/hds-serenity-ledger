package pt.ulisboa.tecnico.hdsledger.shared.communication.hdsledger_message;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import pt.ulisboa.tecnico.hdsledger.shared.communication.Message;

@SuperBuilder
public class LedgerMessage extends Message {
    @Getter
    @Setter
    private Object value;
    @Getter
    @Setter
    private byte[] signature;

    public LedgerMessage(String senderId, Type type) {
        super(senderId, type);
    }

}
