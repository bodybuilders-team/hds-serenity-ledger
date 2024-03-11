package pt.ulisboa.tecnico.hdsledger.service.models;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Ledger {

    // PublicKey -> Account
    private final Map<String, Account> accounts = new ConcurrentHashMap<>();

    public Ledger() {
    }

    public void addAccount(String publicKey, Account account) {
        accounts.put(publicKey, account);
    }

    public Account getAccount(String publicKey) {
        return accounts.get(publicKey);
    }
}
