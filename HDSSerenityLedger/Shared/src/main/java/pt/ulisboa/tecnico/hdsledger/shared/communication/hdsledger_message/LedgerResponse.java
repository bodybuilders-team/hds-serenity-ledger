package pt.ulisboa.tecnico.hdsledger.shared.communication.hdsledger_message;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import pt.ulisboa.tecnico.hdsledger.shared.communication.Message;

@SuperBuilder
public class LedgerResponse extends Message {
    @Getter
    @Setter
    private long originalRequestId;

    private String message;
}
