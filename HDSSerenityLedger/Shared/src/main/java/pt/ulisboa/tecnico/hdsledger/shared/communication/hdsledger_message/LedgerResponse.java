package pt.ulisboa.tecnico.hdsledger.shared.communication.hdsledger_message;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import pt.ulisboa.tecnico.hdsledger.shared.communication.Message;

@SuperBuilder
@ToString(callSuper = true)
public class LedgerResponse extends Message {
    @Getter
    @Setter
    private long originalRequestId;

    @Getter
    private String message;
}
