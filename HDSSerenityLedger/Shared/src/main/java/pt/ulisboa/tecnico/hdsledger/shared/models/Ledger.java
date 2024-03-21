package pt.ulisboa.tecnico.hdsledger.shared.models;

import pt.ulisboa.tecnico.hdsledger.shared.communication.Message.Type;
import pt.ulisboa.tecnico.hdsledger.shared.communication.hdsledger_message.LedgerTransferRequest;
import pt.ulisboa.tecnico.hdsledger.shared.config.ClientProcessConfig;
import pt.ulisboa.tecnico.hdsledger.shared.config.ServerProcessConfig;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Ledger {

    public static final double FEE = 0.01;

    // PublicKey -> Account
    private final Map<String, Account> accounts = new ConcurrentHashMap<>();
    private final ClientProcessConfig[] clientsConfig; // All clients configuration
    private final ServerProcessConfig[] nodesConfig;

    public Ledger(ClientProcessConfig[] clientsConfig, ServerProcessConfig[] nodesConfig) {
        this.clientsConfig = clientsConfig;
        this.nodesConfig = nodesConfig;

        // Initialize the client accounts
        for (var clientConfig : clientsConfig)
            accounts.put(clientConfig.getId(), new Account(clientConfig.getId()));

        // Initialize the node accounts
        for (var nodeConfig : nodesConfig)
            accounts.put(nodeConfig.getId(), new Account(nodeConfig.getId()));
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
                final var transferRequest = (LedgerTransferRequest) request.getLedgerRequest();

                final Account sender = accounts.get(transferRequest.getSourceAccountId());
                final Account receiver = accounts.get(transferRequest.getDestinationAccountId());
                final Account blockCreator = accounts.get(block.getCreatorId());

                sender.subtractBalance(transferRequest.getAmount() + transferRequest.getFee());
                receiver.addBalance(transferRequest.getAmount());
                blockCreator.addBalance(transferRequest.getFee());
            }
        }

        return true;
    }

    public boolean validateBlock(Block block) {
        for (var request : block.getRequests()) {
            if (!request.verifySignature(clientsConfig))
                return false;

            if (request.getType() == Type.TRANSFER) {
                var transferMessage = (LedgerTransferRequest) request.getLedgerRequest();

                Account sender = accounts.get(transferMessage.getDestinationAccountId());
                Account receiver = accounts.get(transferMessage.getSourceAccountId());

                if (sender == null || receiver == null)
                    return false;

                if (sender.getBalance() < transferMessage.getAmount())
                    return false;
            }
        }
        return true;
    }
}
