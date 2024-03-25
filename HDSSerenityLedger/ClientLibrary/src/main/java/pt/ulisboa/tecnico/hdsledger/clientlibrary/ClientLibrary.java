package pt.ulisboa.tecnico.hdsledger.clientlibrary;

import pt.ulisboa.tecnico.hdsledger.service.services.UDPService;
import pt.ulisboa.tecnico.hdsledger.shared.communication.AuthenticatedPerfectLink;
import pt.ulisboa.tecnico.hdsledger.shared.communication.Message;
import pt.ulisboa.tecnico.hdsledger.shared.communication.ledger_message.LedgerCheckBalanceRequest;
import pt.ulisboa.tecnico.hdsledger.shared.communication.ledger_message.LedgerResponse;
import pt.ulisboa.tecnico.hdsledger.shared.communication.ledger_message.LedgerTransferRequest;
import pt.ulisboa.tecnico.hdsledger.shared.communication.ledger_message.SignedLedgerRequest;
import pt.ulisboa.tecnico.hdsledger.shared.config.ClientProcessConfig;
import pt.ulisboa.tecnico.hdsledger.shared.config.NodeProcessConfig;
import pt.ulisboa.tecnico.hdsledger.shared.config.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.shared.crypto.CryptoUtils;
import pt.ulisboa.tecnico.hdsledger.shared.logger.ProcessLogger;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * API for the HDSLedger client.
 */
public class ClientLibrary implements UDPService {

    private static final boolean AUTHENTICATED_PERFECT_LINK_LOGS_ENABLED = false;
    private final ProcessLogger logger;
    private final ClientProcessConfig clientConfig;
    private final ProcessConfig[] clientsConfig;
    private final ProcessConfig[] nodesConfig;

    private final AtomicLong requestIdCounter = new AtomicLong(0);
    // Response ID -> Sender ID -> Message
    private final Map<Long, Map<String, LedgerResponse>> ledgerResponses = new ConcurrentHashMap<>();
    private final Map<Long, Map<String, LedgerResponse>> ledgerAcks = new ConcurrentHashMap<>();
    private AuthenticatedPerfectLink authenticatedPerfectLink;
    private int quorumSize;

    public ClientLibrary(ClientProcessConfig clientConfig, NodeProcessConfig[] nodesConfig, ClientProcessConfig[] clientsConfig) {
        this.clientConfig = clientConfig;
        this.logger = new ProcessLogger(ClientLibrary.class.getName(), clientConfig.getId());
        this.clientsConfig = clientsConfig;
        this.nodesConfig = nodesConfig;

        try {
            this.authenticatedPerfectLink = new AuthenticatedPerfectLink(
                    clientConfig,
                    clientConfig.getPort(),
                    nodesConfig,
                    AUTHENTICATED_PERFECT_LINK_LOGS_ENABLED
            );

            int f = Math.floorDiv(nodesConfig.length - 1, 3);
            this.quorumSize = Math.floorDiv(nodesConfig.length + f, 2) + 1;
        } catch (Exception e) {
            logger.error(MessageFormat.format("Error creating link: {0}", e.getMessage()));
        }
    }


    /**
     * Checks the balance of an account.
     *
     * @param accountId the account id
     */
    public void checkBalance(String accountId) {
        logger.info(MessageFormat.format("Checking balance of account \u001B[33m{0}\u001B[37m...", accountId));

        ProcessConfig accountConfig = Arrays.stream(clientsConfig).filter(c -> c.getId().equals(accountId)).findAny()
                .orElse(Arrays.stream(nodesConfig).filter(c -> c.getId().equals(accountId)).findAny().orElse(null));
        if (accountConfig == null) {
            logger.error(MessageFormat.format("Account {0} not found", accountId));
            return;
        }

        try {
            final var ledgerRequest = LedgerCheckBalanceRequest.builder()
                    .requestId(requestIdCounter.getAndIncrement())
                    .accountId(accountId)
                    .requesterId(clientConfig.getId())
                    .build();

            var privateKey = CryptoUtils.getPrivateKey(clientConfig.getPrivateKeyPath());
            var signature = CryptoUtils.sign(ledgerRequest, privateKey);

            final var request = SignedLedgerRequest.builder()
                    .senderId(clientConfig.getId())
                    .type(Message.Type.BALANCE)
                    .ledgerRequest(ledgerRequest)
                    .signature(signature)
                    .build();

            authenticatedPerfectLink.broadcast(request);
        } catch (Exception e) {
            logger.error(MessageFormat.format("Error sending append: {0}", e.getMessage()));
        }
    }

    /**
     * Transfers money from one account to another.
     *
     * @param sourceAccountId      the source account id
     * @param destinationAccountId the destination account id
     * @param amount               the amount to transfer
     */
    public void transfer(String sourceAccountId, String destinationAccountId, double amount) {
        logger.info(MessageFormat.format("Transferring \u001B[33m{0} HDC\u001B[37m from account \u001B[33m{1}\u001B[37m to account \u001B[33m{2}\u001B[37m...", amount, sourceAccountId, destinationAccountId));

        try {
            final var transferRequest = LedgerTransferRequest.builder()
                    .requestId(requestIdCounter.getAndIncrement())
                    .sourceAccountId(clientConfig.getBehavior() == ProcessConfig.ProcessBehavior.ROBBER_CLIENT ? destinationAccountId : sourceAccountId)
                    .destinationAccountId(clientConfig.getBehavior() == ProcessConfig.ProcessBehavior.ROBBER_CLIENT ? sourceAccountId : destinationAccountId)
                    .amount(amount)
                    .build();

            final var privateKey = CryptoUtils.getPrivateKey(clientConfig.getPrivateKeyPath());
            final var signature = CryptoUtils.sign(transferRequest, privateKey);

            final var signedLedgerRequest = SignedLedgerRequest.builder()
                    .senderId(clientConfig.getId())
                    .type(Message.Type.TRANSFER)
                    .ledgerRequest(transferRequest)
                    .signature(signature)
                    .build();

            authenticatedPerfectLink.broadcast(signedLedgerRequest);
        } catch (Exception e) {
            logger.error(MessageFormat.format("Error sending read: {0}", e.getMessage()));
        }
    }

    @Override
    public void listen() {
        logger.info("Listening for messages...");

        new Thread(() -> {
            while (true) {
                try {
                    final var signedMessage = authenticatedPerfectLink.receive();

                    if (!(signedMessage.getMessage() instanceof LedgerResponse ledgerResponse))
                        continue;

                    switch (ledgerResponse.getType()) {
                        case BALANCE_RESPONSE, TRANSFER_RESPONSE -> handleLedgerResponse(ledgerResponse);
                        case IGNORE -> { /* Do nothing */ }
                        case LEDGER_ACK -> {
                            final var requestIdAcks = ledgerAcks.computeIfAbsent(ledgerResponse.getOriginalRequestId(), k -> new HashMap<>());
                            requestIdAcks.putIfAbsent(ledgerResponse.getSenderId(), ledgerResponse);

                            if (requestIdAcks.size() != quorumSize)
                                continue;

                            logger.info(MessageFormat.format("Received acknowledgement: \"{0}\" for request ID {1}",
                                    ledgerResponse.getMessage(),
                                    ledgerResponse.getOriginalRequestId())
                            );
                        }
                        default ->
                                logger.warn(MessageFormat.format("Received unknown message type: {0}", ledgerResponse.getType()));
                    }

                } catch (Exception e) {
                    logger.error(MessageFormat.format("Error receiving message: {0}", e.getMessage()));
                }
            }
        }).start();
    }

    /**
     * Handles a ledger response, BALANCE or TRANSFER.
     *
     * @param ledgerResponse the ledger response
     */
    private void handleLedgerResponse(LedgerResponse ledgerResponse) {
        final var requestIdBalanceResponses = ledgerResponses.computeIfAbsent(ledgerResponse.getOriginalRequestId(), k -> new HashMap<>());
        requestIdBalanceResponses.putIfAbsent(ledgerResponse.getSenderId(), ledgerResponse);

        if (requestIdBalanceResponses.size() != quorumSize)
            return;

        logger.info(MessageFormat.format("Received {0} response: \"{1}\" for request ID {2}",
                switch (ledgerResponse.getType()) {
                    case BALANCE_RESPONSE -> "balance";
                    case TRANSFER_RESPONSE -> "transfer";
                    default -> "unknown";
                },
                ledgerResponse.getMessage(),
                ledgerResponse.getOriginalRequestId())
        );
    }
}
