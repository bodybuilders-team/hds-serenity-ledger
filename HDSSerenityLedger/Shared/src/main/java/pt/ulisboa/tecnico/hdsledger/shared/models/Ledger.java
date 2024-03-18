package pt.ulisboa.tecnico.hdsledger.shared.models;

import pt.ulisboa.tecnico.hdsledger.shared.SerializationUtils;
import pt.ulisboa.tecnico.hdsledger.shared.communication.Message.Type;
import pt.ulisboa.tecnico.hdsledger.shared.communication.hdsledger_message.LedgerMessageDto;
import pt.ulisboa.tecnico.hdsledger.shared.communication.hdsledger_message.LedgerTransferMessage;
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
        for (var request : block.getRequests()) {
            if (LedgerTransferMessage.verifySignature(request, clientsConfig)) {
                return false;
            }

            if (request.getType() == Type.TRANSFER) {
                var transferMessage = (LedgerTransferMessage) request.getValue();

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

        for (var request : block.getRequests()) {
            if (request.getType() == Type.TRANSFER) {
                var transferMessage = (LedgerTransferMessage) request.getValue();

                Account sender = accounts.get(transferMessage.getDestinationAccountId());
                Account receiver = accounts.get(transferMessage.getSourceAccountId());
                sender.setBalance(sender.getBalance() - transferMessage.getAmount());
                receiver.setBalance(receiver.getBalance() + transferMessage.getAmount());
            }
        }

        return true;
    }
}
