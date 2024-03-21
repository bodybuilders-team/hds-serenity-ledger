package pt.ulisboa.tecnico.hdsledger.shared.models;

import pt.ulisboa.tecnico.hdsledger.shared.communication.Message.Type;
import pt.ulisboa.tecnico.hdsledger.shared.communication.ledger_message.LedgerTransferRequest;
import pt.ulisboa.tecnico.hdsledger.shared.config.ClientProcessConfig;
import pt.ulisboa.tecnico.hdsledger.shared.config.NodeProcessConfig;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The {@code Ledger} class represents the ledger of the system, containing all the accounts.
 */
public class Ledger {

    public static final double FEE = 0.01;

    // PublicKey -> Account
    private final Map<String, Account> accounts = new ConcurrentHashMap<>();
    private final ClientProcessConfig[] clientsConfig;

    public Ledger(ClientProcessConfig[] clientsConfig, NodeProcessConfig[] nodesConfig) {
        this.clientsConfig = clientsConfig;

        // Initialize accounts for both clients and nodes
        for (var clientConfig : clientsConfig)
            accounts.put(clientConfig.getId(), new Account(clientConfig.getId()));

        for (var nodeConfig : nodesConfig)
            accounts.put(nodeConfig.getId(), new Account(nodeConfig.getId()));
    }

    /**
     * Adds an account to the ledger.
     *
     * @param publicKey the public key of the account
     * @param account   the account to add
     */
    public void addAccount(String publicKey, Account account) {
        accounts.put(publicKey, account);
    }

    /**
     * Gets the account with the given id.
     *
     * @param id the id of the account
     * @return the account with the given id
     */
    public Account getAccount(String id) {
        return accounts.get(id);
    }

    /**
     * Adds a block to the ledger.
     *
     * @param block the block to add
     * @return {@code true} if the block was added successfully, {@code false} otherwise
     */
    public boolean addBlock(Block block) {
        if (!validateBlock(block))
            return false;

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

    /**
     * Validates a block.
     * A block is valid if all the requests are signed by the correct client and the sender has enough balance.
     *
     * @param block the block to validate
     * @return {@code true} if the block is valid, {@code false} otherwise
     */
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

                if (sender.getBalance() < transferMessage.getAmount() + transferMessage.getFee())
                    return false;
            }
        }
        return true;
    }
}
