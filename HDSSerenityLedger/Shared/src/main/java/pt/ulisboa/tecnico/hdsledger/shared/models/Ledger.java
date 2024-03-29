package pt.ulisboa.tecnico.hdsledger.shared.models;

import lombok.Getter;
import pt.ulisboa.tecnico.hdsledger.shared.Utils;
import pt.ulisboa.tecnico.hdsledger.shared.communication.Message;
import pt.ulisboa.tecnico.hdsledger.shared.communication.Message.Type;
import pt.ulisboa.tecnico.hdsledger.shared.communication.ledger_message.LedgerCheckBalanceRequest;
import pt.ulisboa.tecnico.hdsledger.shared.communication.ledger_message.LedgerResponse;
import pt.ulisboa.tecnico.hdsledger.shared.communication.ledger_message.LedgerTransferRequest;
import pt.ulisboa.tecnico.hdsledger.shared.communication.ledger_message.SignedLedgerRequest;
import pt.ulisboa.tecnico.hdsledger.shared.config.ClientProcessConfig;
import pt.ulisboa.tecnico.hdsledger.shared.config.NodeProcessConfig;
import pt.ulisboa.tecnico.hdsledger.shared.config.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.shared.logger.ProcessLogger;

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

    private final ProcessLogger logger;
    public static final double FEE = 0.01;

    // PublicKey -> Account
    @Getter
    private final Map<String, Account> accounts = new ConcurrentHashMap<>();

    @Getter
    private final HashSet<SignedLedgerRequest> requests = new HashSet<>();
    private final ClientProcessConfig[] clientsConfig;
    private final String nodeId; // The id of the node that owns this ledger
    private final NodeProcessConfig config;

    public Ledger(ClientProcessConfig[] clientsConfig, NodeProcessConfig[] nodesConfig, NodeProcessConfig config) {
        this.clientsConfig = clientsConfig;
        this.nodeId = config.getId();
        this.config = config;
        this.logger = new ProcessLogger(Ledger.class.getName(), nodeId);

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
        var responses = new ArrayList<LedgerResponse>();

        for (var request : block.getRequests()) {
            requests.add(request);

            if (request.getType() == Type.TRANSFER) {
                final var transferRequest = (LedgerTransferRequest) request.getLedgerRequest();

                final Account sender = accounts.get(transferRequest.getSourceAccountId());
                final Account receiver = accounts.get(transferRequest.getDestinationAccountId());
                final Account blockCreator = accounts.get(block.getCreatorId());

                var fee = transferRequest.getAmount() * FEE;
                if (this.nodeId.equals(block.getCreatorId()) && this.config.getBehavior() == ProcessConfig.ProcessBehavior.ROBBER_LEADER) {
                    fee = transferRequest.getAmount() * (FEE * 2);
                }

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
        if (block == null)
            return false;

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

            Account sender = accounts.get(transferMessage.getSourceAccountId());
            Account receiver = accounts.get(transferMessage.getDestinationAccountId());

            if (sender == null || receiver == null)
                return false;

            final var fee = transferMessage.getAmount() * FEE;

            return sender.getBalance() >= transferMessage.getAmount() + fee;
        }

        return true;
    }

    @Override
    public String toString() {
        return Utils.convertWithStream(accounts);
    }
}
