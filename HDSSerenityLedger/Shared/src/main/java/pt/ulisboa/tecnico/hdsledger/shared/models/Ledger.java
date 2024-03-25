package pt.ulisboa.tecnico.hdsledger.shared.models;

import lombok.Getter;
import pt.ulisboa.tecnico.hdsledger.shared.communication.Message;
import pt.ulisboa.tecnico.hdsledger.shared.communication.Message.Type;
import pt.ulisboa.tecnico.hdsledger.shared.communication.ledger_message.LedgerCheckBalanceRequest;
import pt.ulisboa.tecnico.hdsledger.shared.communication.ledger_message.LedgerResponse;
import pt.ulisboa.tecnico.hdsledger.shared.communication.ledger_message.LedgerTransferRequest;
import pt.ulisboa.tecnico.hdsledger.shared.communication.ledger_message.SignedLedgerRequest;
import pt.ulisboa.tecnico.hdsledger.shared.config.ClientProcessConfig;
import pt.ulisboa.tecnico.hdsledger.shared.config.NodeProcessConfig;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The {@code Ledger} class represents the ledger of the system, containing all the accounts and the blockchain.
 */
public class Ledger {

    public static final double FEE = 0.01;

    // PublicKey -> Account
    private final Map<String, Account> accounts = new ConcurrentHashMap<>();
    private final List<Block> blockchain = new ArrayList<>();

    @Getter
    private final HashSet<SignedLedgerRequest> requests = new HashSet<>();
    private final ClientProcessConfig[] clientsConfig;
    private final String nodeId; // The id of the node that owns this ledger

    public Ledger(ClientProcessConfig[] clientsConfig, NodeProcessConfig[] nodesConfig, String nodeId) {
        this.clientsConfig = clientsConfig;
        this.nodeId = nodeId;

        // Initialize accounts for both clients and nodes
        for (var clientConfig : clientsConfig)
            accounts.put(clientConfig.getId(), new Account(clientConfig.getId()));

        for (var nodeConfig : nodesConfig)
            accounts.put(nodeConfig.getId(), new Account(nodeConfig.getId()));
    }


    /**
     * Adds a block to the ledger.
     *
     * @param block the block to add
     * @return the responses to the requests in the block
     */
    public List<LedgerResponse> addBlock(Block block) {
        blockchain.add(block);

        var responses = new ArrayList<LedgerResponse>();

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

                responses.add(LedgerResponse.builder()
                        .senderId(nodeId)
                        .originalRequestSenderId(request.getSenderId())
                        .originalRequestId(request.getLedgerRequest().getRequestId())
                        .type(Message.Type.TRANSFER_RESPONSE)
                        .message(MessageFormat.format("Successfully transferred {0} HDC from {1} to {2}",
                                transferRequest.getAmount(),
                                transferRequest.getSourceAccountId(),
                                transferRequest.getDestinationAccountId()))
                        .build());
            } else if (request.getType() == Type.BALANCE) {
                String accountId = ((LedgerCheckBalanceRequest) request.getLedgerRequest()).getAccountId();

                Account account = accounts.get(accountId);
                double balance = account.getBalance();

                responses.add(LedgerResponse.builder()
                        .senderId(nodeId)
                        .originalRequestSenderId(request.getSenderId())
                        .originalRequestId(request.getLedgerRequest().getRequestId())
                        .type(Message.Type.BALANCE_RESPONSE)
                        .message(MessageFormat.format("The balance of account {0} is {1}", accountId, balance))
                        .build());
            }
        }

        return responses;
    }

    /**
     * Validates a block.
     * A block is valid if all the requests inside it are valid.
     *
     * @param block the block to validate
     * @return {@code true} if the block is valid, {@code false} otherwise
     */
    public boolean validateBlock(Block block) {
        return block.getRequests().stream().allMatch(this::validateRequest);
    }

    /**
     * Validates a request.
     * A request is valid if it is signed by the correct client and the sender has enough balance.
     *
     * @param request the request to validate
     * @return {@code true} if the request is valid, {@code false} otherwise
     */
    public boolean validateRequest(SignedLedgerRequest request) {
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
