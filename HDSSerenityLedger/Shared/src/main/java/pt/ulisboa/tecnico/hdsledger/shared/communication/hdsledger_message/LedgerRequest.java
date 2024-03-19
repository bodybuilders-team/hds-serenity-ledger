package pt.ulisboa.tecnico.hdsledger.shared.communication.hdsledger_message;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@SuperBuilder
public abstract class LedgerRequest {
    @Getter
    @Setter
    private long requestId;

    public LedgerRequest() {

    }
}
