package pt.ulisboa.tecnico.hdsledger.clientlibrary;

import com.google.gson.Gson;
import pt.ulisboa.tecnico.hdsledger.communication.AuthenticatedPerfectLink;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.hdsledger_message.HDSLedgerMessage;
import pt.ulisboa.tecnico.hdsledger.communication.hdsledger_message.HDSLedgerMessageBuilder;
import pt.ulisboa.tecnico.hdsledger.communication.hdsledger_message.LedgerTransferMessage;
import pt.ulisboa.tecnico.hdsledger.service.services.UDPService;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.config.ClientProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.config.ServerProcessConfig;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * API for the HDSLedger client.
 */
public class ClientLibrary implements UDPService {

    private final ClientProcessConfig clientConfig;
    private final ClientProcessConfig[] clientsConfig;
    private final ProcessLogger logger;
    private AuthenticatedPerfectLink authenticatedPerfectLink;

    private static final boolean LOGS_ENABLED = false;

    // Balance response -> sender ID -> Message
    private final Map<String, Map<String, HDSLedgerMessage>> balanceResponses = new HashMap<>();
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
                    HDSLedgerMessage.class,
                    LOGS_ENABLED
            );

            int f = Math.floorDiv(nodesConfig.length - 1, 3);
            this.quorumSize = Math.floorDiv(nodesConfig.length + f, 2) + 1;
        } catch (Exception e) {
            logger.error(MessageFormat.format("Error creating link: {0}", e.getMessage()));
        }
    }

    /**
     * Creates an account for the client.
     */
    public void register() {
        logger.info("Creating account...");

        try {
            HDSLedgerMessage message = new HDSLedgerMessageBuilder(clientConfig.getId(), Message.Type.REGISTER)
                    .setValue(clientConfig.getId())
                    .build();

            authenticatedPerfectLink.broadcast(message);
        } catch (Exception e) {
            logger.error(MessageFormat.format("Error sending append: {0}", e.getMessage()));
        }
    }

    /**
     * Checks the balance of an account.
     *
     * @param accountId the account id
     */
    public void checkBalance(String accountId) {
        logger.info(MessageFormat.format("Checking balance of account \u001B[33m{0}\u001B[37m...", accountId));

        ClientProcessConfig accountConfig = Arrays.stream(clientsConfig).filter(c -> c.getId().equals(accountId)).findAny().orElse(null);
        if (accountConfig == null) {
            logger.error(MessageFormat.format("Account \u001B[33m{0}\u001B[37m not found", accountId));
            return;
        }

        try {
            HDSLedgerMessage message = new HDSLedgerMessageBuilder(clientConfig.getId(), Message.Type.BALANCE)
                    .setValue(accountId)
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
            LedgerTransferMessage transferMessage = new LedgerTransferMessage(sourceAccountId, destinationAccountId, amount);

            HDSLedgerMessage message = new HDSLedgerMessageBuilder(clientConfig.getId(), Message.Type.TRANSFER)
                    .setValue(new Gson().toJson(transferMessage))
                    .build();

            authenticatedPerfectLink.broadcast(message);
        } catch (Exception e) {
            logger.error(MessageFormat.format("Error sending read: {0}", e.getMessage()));
        }
    }

    /**
     * Handles a balance response.
     */
    private void handleBalanceResponse(HDSLedgerMessage ledgerMessage) {
        if (ledgerMessage.getType() != Message.Type.BALANCE_RESPONSE)
            return;

        synchronized (balanceResponses) {
            // TODO: does order of read responses matter? (sending two reads and receiving the responses in different order)
            balanceResponses.putIfAbsent(ledgerMessage.getValue(), new HashMap<>());
            balanceResponses.get(ledgerMessage.getValue()).putIfAbsent(ledgerMessage.getSenderId(), ledgerMessage);

            if (balanceResponses.get(ledgerMessage.getValue()).size() != quorumSize)
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
                    Message message = authenticatedPerfectLink.receive();

                    if (!(message instanceof HDSLedgerMessage ledgerMessage))
                        continue;

                    switch (ledgerMessage.getType()) {
                        case BALANCE_RESPONSE ->
                                handleBalanceResponse(ledgerMessage);
                        case TRANSFER_RESPONSE ->
                                logger.info(MessageFormat.format("Received transfer response: \"{0}\"", ledgerMessage.getValue()));
                        case IGNORE -> { /* Do nothing */ }
                        default ->
                                logger.warn(MessageFormat.format("Received unknown message type: {0}", ledgerMessage.getType()));
                    }

                } catch (Exception e) {
                    logger.error(MessageFormat.format("Error receiving message: {0}", e.getMessage()));
                }
            }
        }).start();
    }
}
