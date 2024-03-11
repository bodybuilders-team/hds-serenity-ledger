package pt.ulisboa.tecnico.hdsledger.service.services;

import pt.ulisboa.tecnico.hdsledger.communication.AuthenticatedPerfectLink;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.hdsledger_message.HDSLedgerMessage;
import pt.ulisboa.tecnico.hdsledger.communication.hdsledger_message.HDSLedgerMessageBuilder;
import pt.ulisboa.tecnico.hdsledger.communication.hdsledger_message.LedgerTransferMessage;
import pt.ulisboa.tecnico.hdsledger.crypto.CryptoUtils;
import pt.ulisboa.tecnico.hdsledger.service.models.Account;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.config.ClientProcessConfig;

import java.security.PublicKey;
import java.text.MessageFormat;
import java.util.Arrays;

/**
 * Service used to interact with the HDSLedger.
 */
public class HDSLedgerService implements UDPService {

    private final NodeService nodeService;
    private final ProcessLogger logger;
    private final ClientProcessConfig[] clientsConfig; // All clients configuration

    // Link to communicate with the clients
    private final AuthenticatedPerfectLink authenticatedPerfectLink;

    public HDSLedgerService(
            AuthenticatedPerfectLink authenticatedPerfectLink,
            NodeService nodeService,
            ClientProcessConfig[] clientsConfig
    ) {
        this.nodeService = nodeService;
        this.authenticatedPerfectLink = authenticatedPerfectLink;
        this.logger = new ProcessLogger(HDSLedgerService.class.getName(), nodeService.getConfig().getId());
        this.clientsConfig = clientsConfig;
    }

    /**
     * Handles a register message.
     *
     * @param message the register message
     */
    public void uponRegister(HDSLedgerMessage message) {
        logger.info(MessageFormat.format("Received register request: {0}", message.getValue()));

        try {
            String ownerId = message.getValue();
            String publicKey = CryptoUtils.getPublicKey(ownerId).toString();

            nodeService.getLedger().addAccount(publicKey, new Account(ownerId, publicKey));

            // Send the response
            HDSLedgerMessage response = new HDSLedgerMessageBuilder(nodeService.getConfig().getId(), Message.Type.REGISTER_RESPONSE)
                    .setValue("Account created successfully")
                    .build();

            authenticatedPerfectLink.send(message.getSenderId(), response);
        } catch (Exception e) {
            logger.error(MessageFormat.format("Error registering account: {0}", e.getMessage()));
        }
    }

    /**
     * Handles a transfer message.
     *
     * @param message the transfer message
     */
    public void uponTransfer(HDSLedgerMessage message) {
        logger.info(MessageFormat.format("Received transfer request: {0}", message.getValue()));

        try {
            LedgerTransferMessage transferMessage = message.deserializeLedgerTransferMessage();
            /* TODO: Implement this
            nodeService.startConsensus(message.getValue());
            */

            // Send the response
            HDSLedgerMessage response = new HDSLedgerMessageBuilder(nodeService.getConfig().getId(), Message.Type.TRANSFER_RESPONSE)
                    .setValue(MessageFormat.format("Transfer from {0} to {1} of {2} was successful",
                            transferMessage.getSourceAccountId(), transferMessage.getDestinationAccountId(), transferMessage.getAmount()))
                    .build();

            authenticatedPerfectLink.send(message.getSenderId(), response);
        } catch (Exception e) {
            logger.error(MessageFormat.format("Error transferring: {0}", e.getMessage()));
        }
    }

    /**
     * Handles a balance message.
     *
     * @param message the balance message
     */
    public void uponBalance(HDSLedgerMessage message) {
        logger.info("Received balance request");

        try {
            String accountId = message.getValue();
            ClientProcessConfig owner = Arrays.stream(clientsConfig).filter(c -> c.getId().equals(accountId)).findAny().get();
            PublicKey publicKey = CryptoUtils.getPublicKey(owner.getPublicKeyPath());

            Account account = nodeService.getLedger().getAccount(publicKey.toString());
            int balance = account.getBalance();

            logger.info(MessageFormat.format("Sending balance response: {0}", balance));

            HDSLedgerMessage response = new HDSLedgerMessageBuilder(nodeService.getConfig().getId(), Message.Type.BALANCE_RESPONSE)
                    .setValue(String.valueOf(balance))
                    .build();

            authenticatedPerfectLink.send(message.getSenderId(), response);
        } catch (Exception e) {
            logger.error(MessageFormat.format("Error retrieving balance: {0}", e.getMessage()));
        }
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

                    new Thread(() -> {
                        switch (ledgerMessage.getType()) {
                            case REGISTER -> uponRegister(ledgerMessage);

                            case BALANCE -> uponBalance(ledgerMessage);

                            case TRANSFER -> uponTransfer(ledgerMessage);

                            case IGNORE -> {/* Do nothing */}

                            default ->
                                    logger.warn(MessageFormat.format("Received unknown message type: {0}", ledgerMessage.getType()));
                        }
                    }).start();

                } catch (Exception e) {
                    logger.error(MessageFormat.format("Error receiving message: {0}", e.getMessage()));
                }
            }
        }).start();
    }
}
