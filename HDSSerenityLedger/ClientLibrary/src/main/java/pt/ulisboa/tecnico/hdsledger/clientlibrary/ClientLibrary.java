package pt.ulisboa.tecnico.hdsledger.clientlibrary;

import pt.ulisboa.tecnico.hdsledger.communication.AuthenticatedPerfectLink;
import pt.ulisboa.tecnico.hdsledger.service.services.UDPService;
import pt.ulisboa.tecnico.hdsledger.shared.ProcessLogger;
import pt.ulisboa.tecnico.hdsledger.shared.SerializationUtils;
import pt.ulisboa.tecnico.hdsledger.shared.communication.Message;
import pt.ulisboa.tecnico.hdsledger.shared.communication.hdsledger_message.HDSLedgerMessageBuilder;
import pt.ulisboa.tecnico.hdsledger.shared.communication.hdsledger_message.LedgerMessage;
import pt.ulisboa.tecnico.hdsledger.shared.communication.hdsledger_message.LedgerMessageDto;
import pt.ulisboa.tecnico.hdsledger.shared.communication.hdsledger_message.LedgerTransferMessage;
import pt.ulisboa.tecnico.hdsledger.shared.config.ClientProcessConfig;
import pt.ulisboa.tecnico.hdsledger.shared.config.ServerProcessConfig;
import pt.ulisboa.tecnico.hdsledger.shared.crypto.CryptoUtils;

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
    private final Map<String, Map<String, LedgerMessage>> balanceResponses = new HashMap<>();
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
                    LedgerMessageDto.class,
                    LOGS_ENABLED
            );

            int f = Math.floorDiv(nodesConfig.length - 1, 3);
            this.quorumSize = Math.floorDiv(nodesConfig.length + f, 2) + 1;
        } catch (Exception e) {
            logger.error(MessageFormat.format("Error creating link: {0}", e.getMessage()));
        }
    }

//    /**
//     * Creates an account for the client.
//     */
//    public void register() {
//        logger.info("Creating account...");
//
//        try {
//            LedgerRegisterMessage transferMessage = new LedgerRegisterMessage(clientConfig.getId(), );
//            var privateKey = CryptoUtils.getPrivateKey(clientConfig.getPrivateKeyPath());
//            var signedMessage = CryptoUtils.signMessage(transferMessage, privateKey);
//
//            HDSLedgerMessage message = new HDSLedgerMessageBuilder(clientConfig.getId(), Message.Type.TRANSFER)
//                    .setValue(SerializationUtils.getGson().toJson(signedMessage))
//                    .build();
//
//            HDSLedgerMessage message = new HDSLedgerMessageBuilder(clientConfig.getId(), Message.Type.REGISTER)
//                    .setValue(clientConfig.getId())
//                    .build();
//
//            authenticatedPerfectLink.broadcast(message);
//        } catch (Exception e) {
//            logger.error(MessageFormat.format("Error sending append: {0}", e.getMessage()));
//        }
//    }

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
            // Need to sign the message (stage 2 request)
            LedgerMessageDto message = new HDSLedgerMessageBuilder(clientConfig.getId(), Message.Type.BALANCE)
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
            var transferMessage = new LedgerTransferMessage(sourceAccountId, destinationAccountId, amount);
            var privateKey = CryptoUtils.getPrivateKey(clientConfig.getPrivateKeyPath());
            var signedPacket = CryptoUtils.signPacket(transferMessage, privateKey);

            var message = new HDSLedgerMessageBuilder(clientConfig.getId(), Message.Type.TRANSFER)
                    .setValue(SerializationUtils.getGson().toJson(signedPacket))
                    .build();

            authenticatedPerfectLink.broadcast(message);
        } catch (Exception e) {
            logger.error(MessageFormat.format("Error sending read: {0}", e.getMessage()));
        }
    }

    /**
     * Handles a balance response.
     */
    private void handleBalanceResponse(LedgerMessage ledgerMessage) {
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
                    final var ledgerMessage = (LedgerMessage) authenticatedPerfectLink.receive();

                    switch (ledgerMessage.getType()) {
                        case BALANCE_RESPONSE ->
                                handleBalanceResponse(ledgerMessage);
                        case TRANSFER_RESPONSE ->
                                logger.info(MessageFormat.format("Received transfer response: \"{0}\"", ledgerMessageDto.getValue()));
                        case IGNORE -> { /* Do nothing */ }
                        default ->
                                logger.warn(MessageFormat.format("Received unknown message type: {0}", ledgerMessageDto.getType()));
                    }

                } catch (Exception e) {
                    logger.error(MessageFormat.format("Error receiving message: {0}", e.getMessage()));
                }
            }
        }).start();
    }
}
