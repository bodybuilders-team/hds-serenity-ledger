package pt.ulisboa.tecnico.hdsledger.shared.communication.hdsledger_message;

import lombok.Getter;
import pt.ulisboa.tecnico.hdsledger.shared.models.LedgerMessageValue;

public class LedgerCheckBalanceMessage implements LedgerMessageValue {
    @Getter
    private final String accountId;


    public LedgerCheckBalanceMessage(String accountId) {
        this.accountId = accountId;
    }
}
