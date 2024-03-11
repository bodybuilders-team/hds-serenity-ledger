package pt.ulisboa.tecnico.hdsledger.service.models;

/**
 * An account in the HDSLedger.
 */
public class Account {

    private final String ownerId;
    private final String publicKey;
    private int balance = INITIAL_BALANCE;

    private static final int INITIAL_BALANCE = 100;

    public Account(String ownerId, String publicKey) {
        this.ownerId = ownerId;
        this.publicKey = publicKey;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public int getBalance() {
        return balance;
    }

    public void addBalance(int amount) {
        balance += amount;
    }

    public void subtractBalance(int amount) {
        balance -= amount;
    }
}
