package pt.ulisboa.tecnico.hdsledger.shared.models;

import pt.ulisboa.tecnico.hdsledger.shared.communication.Message.Type;
import pt.ulisboa.tecnico.hdsledger.shared.communication.hdsledger_message.LedgerTransferRequest;
import pt.ulisboa.tecnico.hdsledger.shared.config.ClientProcessConfig;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Ledger {

    // PublicKey -> Account
    private final Map<String, Account> accounts = new ConcurrentHashMap<>();
    private final ClientProcessConfig[] clientsConfig; // All clients configuration

    public Ledger(ClientProcessConfig[] clientsConfig) {
        this.clientsConfig = clientsConfig;
        // Initialize the accounts
        for (var clientConfig : clientsConfig) {
            accounts.put(clientConfig.getId(), new Account(clientConfig.getId()));
        }
    }

    public void addAccount(String publicKey, Account account) {
        accounts.put(publicKey, account);
    }

    public Account getAccount(String id) {
        return accounts.get(id);
    }

    public boolean addBlock(Block block) {
        if (!validateBlock(block)) {
            return false;
        }

        for (var request : block.getRequests()) {
            if (request.getType() == Type.TRANSFER) {
                var transferRequest = (LedgerTransferRequest) request.getLedgerRequest();

                Account sender = accounts.get(transferRequest.getDestinationAccountId());
                Account receiver = accounts.get(transferRequest.getSourceAccountId());
                sender.setBalance(sender.getBalance() - transferRequest.getAmount());
                receiver.setBalance(receiver.getBalance() + transferRequest.getAmount());
            }
        }

        return true;
    }

    public boolean validateBlock(Block block) {
        for (var request : block.getRequests()) {
            if (!request.verifySignature(clientsConfig)) {
                return false;
            }

            if (request.getType() == Type.TRANSFER) {
                var transferMessage = (LedgerTransferRequest) request.getLedgerRequest();

                Account sender = accounts.get(transferMessage.getDestinationAccountId());
                Account receiver = accounts.get(transferMessage.getSourceAccountId());

                if (sender == null || receiver == null) {
                    return false;
                }

                if (sender.getBalance() < transferMessage.getAmount()) {
                    return false;
                }

            }
        }


        return true;
    }
}
