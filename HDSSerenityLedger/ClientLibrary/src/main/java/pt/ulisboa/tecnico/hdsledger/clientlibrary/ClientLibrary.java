package pt.ulisboa.tecnico.hdsledger.clientlibrary;

import pt.ulisboa.tecnico.hdsledger.communication.AuthenticatedPerfectLink;
import pt.ulisboa.tecnico.hdsledger.service.services.UDPService;
import pt.ulisboa.tecnico.hdsledger.shared.ProcessLogger;
import pt.ulisboa.tecnico.hdsledger.shared.communication.Message;
import pt.ulisboa.tecnico.hdsledger.shared.communication.hdsledger_message.LedgerCheckBalanceRequest;
import pt.ulisboa.tecnico.hdsledger.shared.communication.hdsledger_message.LedgerResponse;
import pt.ulisboa.tecnico.hdsledger.shared.communication.hdsledger_message.LedgerTransferRequest;
import pt.ulisboa.tecnico.hdsledger.shared.communication.hdsledger_message.SignedLedgerRequest;
import pt.ulisboa.tecnico.hdsledger.shared.config.ClientProcessConfig;
import pt.ulisboa.tecnico.hdsledger.shared.config.ServerProcessConfig;
import pt.ulisboa.tecnico.hdsledger.shared.crypto.CryptoUtils;

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

    private static final boolean LOGS_ENABLED = false;
    private final ClientProcessConfig clientConfig;
    private final ClientProcessConfig[] clientsConfig;
    private final ProcessLogger logger;
    private AuthenticatedPerfectLink authenticatedPerfectLink;

    private final AtomicLong requestIdCounter = new AtomicLong(0);
    // Response ID -> Sender ID -> Message
    private final Map<Long, Map<String, LedgerResponse>> ledgerResponses = new ConcurrentHashMap<>();
    private int quorumSize;

    public ClientLibrary(ClientProcessConfig clientConfig, ServerProcessConfig[] nodesConfig, ClientProcessConfig[] clientsConfig) {
        this.clientConfig = clientConfig;
        this.logger = new ProcessLogger(ClientLibrary.class.getName(), clientConfig.getId());
        this.clientsConfig = clientsConfig;

        try {
            this.authenticatedPerfectLink = new AuthenticatedPerfectLink(
                    clientConfig,
                    clientConfig.getPort(),
                    nodesConfig,
                    LOGS_ENABLED
            );

            int f = Math.floorDiv(nodesConfig.length - 1, 3);
            this.quorumSize = Math.floorDiv(nodesConfig.length + f, 2) + 1;
        } catch (Exception e) {
            logger.error(MessageFormat.format("Error creating link: {0}", e.getMessage()));
            e.printStackTrace();
        }
    }


    /**
     * Checks the balance of an account.
     *
     * @param accountId the account id
     */
    public void checkBalance(String accountId) {
        logger.info(MessageFormat.format("Checking balance of account {0}...", accountId));

        ClientProcessConfig accountConfig = Arrays.stream(clientsConfig).filter(c -> c.getId().equals(accountId)).findAny().orElse(null);
        if (accountConfig == null) {
            logger.error(MessageFormat.format("Account {0} not found", accountId));
            return;
        }

        try {
            final var ledgerRequest = LedgerCheckBalanceRequest.builder()
                    .requestId(requestIdCounter.getAndIncrement())
                    .accountId(accountId)
                    .build();

            var privateKey = CryptoUtils.getPrivateKey(clientConfig.getPrivateKeyPath());
            var signature = CryptoUtils.sign(ledgerRequest, privateKey);

            // Need to sign the message (stage 2 request)
            final var request = SignedLedgerRequest.builder()
                    .senderId(clientConfig.getId())
                    .type(Message.Type.BALANCE)
                    .ledgerRequest(ledgerRequest)
                    .signature(signature)
                    .build();

            authenticatedPerfectLink.broadcast(request);
        } catch (Exception e) {
            logger.error(MessageFormat.format("Error sending append: {0}", e.getMessage()));
            e.printStackTrace();
        }
    }

    /**
     * Transfers money from one account to another.
     *
     * @param sourceAccountId      the source account id
     * @param destinationAccountId the destination account id
     * @param amount               the amount to transfer
     */
    public void transfer(String sourceAccountId, String destinationAccountId, int amount) {
        logger.info(MessageFormat.format("Transferring \u001B[33m{0}\u001B[37m from account \u001B[33m{1}\u001B[37m to account \u001B[33m{2}\u001B[37m...", amount, sourceAccountId, destinationAccountId));

        try {
            final var transferRequest = LedgerTransferRequest.builder()
                    .requestId(requestIdCounter.getAndIncrement())
                    .sourceAccountId(sourceAccountId)
                    .destinationAccountId(destinationAccountId)
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
            e.printStackTrace();
        }
    }

    @Override
    public void listen() {
        logger.info("Listening for messages...");

        new Thread(() -> {
            while (true) {
                try {
                    final var signedMessage = authenticatedPerfectLink.receive();

                    if (!(signedMessage.getMessage() instanceof LedgerResponse ledgerResponse)) {
                        continue;
                    }

                    switch (ledgerResponse.getType()) {
                        case BALANCE_RESPONSE -> handleLedgerResponse(ledgerResponse);
                        case TRANSFER_RESPONSE -> handleLedgerResponse(ledgerResponse);
                        case IGNORE -> { /* Do nothing */ }
                        default ->
                                logger.warn(MessageFormat.format("Received unknown message type: {0}", ledgerResponse.getType()));
                    }

                } catch (Exception e) {
                    logger.error(MessageFormat.format("Error receiving message: {0}", e.getMessage()));
                    e.printStackTrace();
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

        logger.info(MessageFormat.format("Received {0} response: \"{1}\" for request ID \"{2}\"",
                ledgerResponse.getType(),
                ledgerResponse.getMessage(),
                ledgerResponse.getOriginalRequestId()));
    }
}
