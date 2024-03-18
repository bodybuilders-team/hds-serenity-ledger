package pt.ulisboa.tecnico.hdsledger.shared.models;

import lombok.Getter;
import lombok.Setter;

/**
 * An account in the HDSLedger.
 */
@Getter
public class Account {

    private static final int INITIAL_BALANCE = 100;
    private final String ownerId;
    @Setter
    private long balance = INITIAL_BALANCE;

    public Account(String ownerId) {
        this.ownerId = ownerId;
    }

}
