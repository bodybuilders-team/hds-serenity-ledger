package pt.ulisboa.tecnico.hdsledger.clientlibrary;

import pt.ulisboa.tecnico.hdsledger.communication.AuthenticatedPerfectLink;
import pt.ulisboa.tecnico.hdsledger.service.services.UDPService;
import pt.ulisboa.tecnico.hdsledger.shared.ProcessLogger;
import pt.ulisboa.tecnico.hdsledger.shared.SerializationUtils;
import pt.ulisboa.tecnico.hdsledger.shared.communication.Message;
import pt.ulisboa.tecnico.hdsledger.shared.communication.hdsledger_message.SignedLedgerRequest;
import pt.ulisboa.tecnico.hdsledger.shared.communication.hdsledger_message.LedgerRequestDto;
import pt.ulisboa.tecnico.hdsledger.shared.communication.hdsledger_message.LedgerTransferRequest;
import pt.ulisboa.tecnico.hdsledger.shared.config.ClientProcessConfig;
import pt.ulisboa.tecnico.hdsledger.shared.config.ServerProcessConfig;
import pt.ulisboa.tecnico.hdsledger.shared.crypto.CryptoUtils;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
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
    private final Map<Long, Map<String, LedgerCheckBalanceResponse>> balanceResponses = new HashMap<>();
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
                    LedgerRequestDto.class,
                    LOGS_ENABLED
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
        logger.info(MessageFormat.format("Checking balance of account {0}...", accountId));

        ClientProcessConfig accountConfig = Arrays.stream(clientsConfig).filter(c -> c.getId().equals(accountId)).findAny().orElse(null);
        if (accountConfig == null) {
            logger.error(MessageFormat.format("Account {0} not found", accountId));
            return;
        }

        try {
            // Need to sign the message (stage 2 request)
            LedgerRequestDto message = LedgerRequestDto.builder()
                    .senderId(clientConfig.getId())
                    .type(Message.Type.BALANCE)
                    .value(accountId)
                    .requestId(requestIdCounter.getAndIncrement())
                    .build();

            authenticatedPerfectLink.broadcast(message);
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
    public void transfer(String sourceAccountId, String destinationAccountId, int amount) {
        logger.info(MessageFormat.format("Transferring \u001B[33m{0}\u001B[37m from account \u001B[33m{1}\u001B[37m to account \u001B[33m{2}\u001B[37m...", amount, sourceAccountId, destinationAccountId));

        try {
            var transferMessage = new LedgerTransferRequest(sourceAccountId, destinationAccountId, amount);
            var privateKey = CryptoUtils.getPrivateKey(clientConfig.getPrivateKeyPath());
            var signedPacket = CryptoUtils.signPacket(transferMessage, privateKey);

            var message = new HDSLedgerMessageBuilder(clientConfig.getId(), Message.Type.TRANSFER)
                    .setValue(SerializationUtils.getGson().toJson(signedPacket))
                    .build();

            LedgerRequestDto message = LedgerRequestDto.builder()
                    .senderId(clientConfig.getId())
                    .type(Message.Type.TRANSFER)
                    .value(transferMessage)
                    .requestId(requestIdCounter.getAndIncrement())
                    .build();

            authenticatedPerfectLink.broadcast(message);
        } catch (Exception e) {
            logger.error(MessageFormat.format("Error sending read: {0}", e.getMessage()));
        }
    }

    /**
     * Handles a balance response.
     */
    private void handleBalanceResponse( ledgerMessage) {
        if (ledgerMessage.getType() != Message.Type.BALANCE_RESPONSE)
            return;

        synchronized (balanceResponses) {
            // TODO: does order of read responses matter? (sending two reads and receiving the responses in different order)
            final var requestIdBalanceResponses = balanceResponses.computeIfAbsent(ledgerMessage.getRequestId(), k -> new HashMap<>());
            requestIdBalanceResponses.putIfAbsent(ledgerMessage.getSenderId(), ledgerMessage);

            if (requestIdBalanceResponses.size() != quorumSize)
                return;
        }

        logger.info(MessageFormat.format("Received balance response: \"{0}\"", ledgerMessage.getValue()));
    }

    @Override
    public void listen() {
        logger.info("Listening for messages...");

        new Thread(() -> {
            while (true) {
                try {
                    final var message = authenticatedPerfectLink.receive();

                    if (!(message instanceof SignedLedgerRequest signedLedgerRequest)) {
                        continue;
                    }

                    switch (signedLedgerRequest.getType()) {
                        case BALANCE_RESPONSE -> handleBalanceResponse(signedLedgerRequest);
                        case TRANSFER_RESPONSE ->
                                logger.info(MessageFormat.format("Received transfer response: \"{0}\"", signedLedgerRequest.getValue()));
                        case IGNORE -> { /* Do nothing */ }
                        default ->
                                logger.warn(MessageFormat.format("Received unknown message type: {0}", signedLedgerRequest.getType()));
                    }

                } catch (Exception e) {
                    logger.error(MessageFormat.format("Error receiving message: {0}", e.getMessage()));
                }
            }
        }).start();
    }
}
