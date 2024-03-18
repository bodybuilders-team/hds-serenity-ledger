package pt.ulisboa.tecnico.hdsledger.shared.communication.hdsledger_message;

import lombok.Getter;

public class LedgerCheckBalanceMessage {
    @Getter
    private final String accountId;


    public LedgerCheckBalanceMessage(String accountId) {
        this.accountId = accountId;
    }
}
