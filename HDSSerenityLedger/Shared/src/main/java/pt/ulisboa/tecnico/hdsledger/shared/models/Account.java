package pt.ulisboa.tecnico.hdsledger.shared.models;

/**
 * An account in the HDSLedger.
 */
public class Account {

    private static final int INITIAL_BALANCE = 100;
    private final String ownerId;
    private long balance = INITIAL_BALANCE;

    public Account(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public long getBalance() {
        return balance;
    }

    public void setBalance(long balance) {
        this.balance = balance;
    }
}
