package pt.ulisboa.tecnico.hdsledger.shared.models;

import lombok.Getter;
import lombok.Setter;

/**
 * An account in the HDSLedger system.
 */
@Getter
public class Account {
    private static final int INITIAL_BALANCE = 100;

    private final String ownerId;

    @Setter
    private double balance = INITIAL_BALANCE;

    public Account(String ownerId) {
        this.ownerId = ownerId;
    }

    /**
     * Adds the given amount to the account's balance.
     *
     * @param amount the amount to add
     */
    public void addBalance(double amount) {
        balance += amount;
    }

    /**
     * Subtracts the given amount from the account's balance.
     *
     * @param amount the amount to subtract
     */
    public void subtractBalance(double amount) {
        balance -= amount;
    }
}
