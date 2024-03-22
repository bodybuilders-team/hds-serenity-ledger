package pt.ulisboa.tecnico.hdsledger.shared.models;

import lombok.Getter;
import pt.ulisboa.tecnico.hdsledger.shared.communication.Message.Type;
import pt.ulisboa.tecnico.hdsledger.shared.communication.ledger_message.LedgerTransferRequest;
import pt.ulisboa.tecnico.hdsledger.shared.communication.ledger_message.SignedLedgerRequest;
import pt.ulisboa.tecnico.hdsledger.shared.config.ClientProcessConfig;
import pt.ulisboa.tecnico.hdsledger.shared.config.NodeProcessConfig;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The {@code Ledger} class represents the ledger of the system, containing all the accounts.
 */
public class Ledger {

    public static final double FEE = 0.01;

    // PublicKey -> Account
    private final Map<String, Account> accounts = new ConcurrentHashMap<>();
    private final List<Block> blockChain = new ArrayList<>();
    @Getter
    private final HashSet<SignedLedgerRequest> requests = new HashSet<>();

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

        blockChain.add(block);

        for (var request : block.getRequests()) {
            requests.add(request);

            if (request.getType() == Type.TRANSFER) {
                final var transferRequest = (LedgerTransferRequest) request.getLedgerRequest();

                final Account sender = accounts.get(transferRequest.getSourceAccountId());
                final Account receiver = accounts.get(transferRequest.getDestinationAccountId());
                final Account blockCreator = accounts.get(block.getCreatorId());

                final var fee = transferRequest.getAmount() * FEE;

                sender.subtractBalance(transferRequest.getAmount() + fee);
                receiver.addBalance(transferRequest.getAmount());

                blockCreator.addBalance(fee);
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
            if (!validateRequest(request))
                return false;
        }
        return true;
    }

    private boolean validateRequest(SignedLedgerRequest request) {
        if (!request.verifySignature(clientsConfig))
            return false;

        if (requests.contains(request))
            return false;

        if (request.getType() == Type.TRANSFER) {
            var transferMessage = (LedgerTransferRequest) request.getLedgerRequest();

            Account sender = accounts.get(transferMessage.getDestinationAccountId());
            Account receiver = accounts.get(transferMessage.getSourceAccountId());

            if (sender == null || receiver == null)
                return false;

            final var fee = transferMessage.getAmount() * FEE;

            if (sender.getBalance() < transferMessage.getAmount() + fee)
                return false;
        }

        return true;
    }
}
