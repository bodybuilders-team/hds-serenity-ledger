package pt.ulisboa.tecnico.hdsledger.service.services;

import pt.ulisboa.tecnico.hdsledger.communication.AuthenticatedPerfectLink;
import pt.ulisboa.tecnico.hdsledger.communication.HDSLedgerMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.builder.HDSLedgerMessageBuilder;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.config.ServerProcessConfig;

import java.text.MessageFormat;
import java.util.ArrayList;

/**
 * Service used to interact with the HDSLedger.
 */
public class HDSLedgerService implements UDPService {

    private static final CustomLogger LOGGER = new CustomLogger(HDSLedgerService.class.getName());

    private final ServerProcessConfig serverProcessConfig;
    private final NodeService nodeService;

    // Link to communicate with the clients
    private final AuthenticatedPerfectLink authenticatedPerfectLink;

    public HDSLedgerService(
            ServerProcessConfig serverProcessConfig,
            AuthenticatedPerfectLink authenticatedPerfectLink,
            NodeService nodeService
    ) {
        this.serverProcessConfig = serverProcessConfig;
        this.nodeService = nodeService;

        this.authenticatedPerfectLink = authenticatedPerfectLink;
    }

    /**
     * Handles an append message.
     *
     * @param message the append message
     */
    public void uponAppend(HDSLedgerMessage message) {
        LOGGER.info(MessageFormat.format("{0} - Appending: {1}",
                serverProcessConfig.getId(), message.getValue()));

        try {
            nodeService.startConsensus(message.getValue());

            // Send the response
            HDSLedgerMessage response = new HDSLedgerMessageBuilder(nodeService.getConfig().getId(), Message.Type.APPEND_RESPONSE)
                    .setValue("Value appended successfully")
                    .build();

            authenticatedPerfectLink.send(message.getSenderId(), response);
        } catch (Exception e) {
            LOGGER.error(MessageFormat.format("{0} - Error sending append: {1}",
                    serverProcessConfig.getId(), e.getMessage()));
        }
    }

    /**
     * Handles a read message.
     *
     * @param message the read message
     */
    public void uponRead(HDSLedgerMessage message) {
        LOGGER.info(MessageFormat.format("{0} - Reading from ledger...",
                serverProcessConfig.getId()));

        try {
            ArrayList<String> ledger = nodeService.getLedger();
            String ledgerString = String.join(", ", ledger);

            LOGGER.info(MessageFormat.format("{0} - Read from ledger: {1} - Sending response...",
                    serverProcessConfig.getId(), ledgerString));

            HDSLedgerMessage response = new HDSLedgerMessageBuilder(nodeService.getConfig().getId(), Message.Type.READ_RESPONSE)
                    .setValue(ledgerString)
                    .build();

            authenticatedPerfectLink.send(message.getSenderId(), response);
        } catch (Exception e) {
            LOGGER.error(MessageFormat.format("{0} - Error sending read: {1}",
                    serverProcessConfig.getId(), e.getMessage()));
        }
    }


    @Override
    public void listen() {
        LOGGER.info(MessageFormat.format("{0} - Listening for messages", serverProcessConfig.getId()));

        new Thread(() -> {
            while (true) {
                try {
                    Message message = authenticatedPerfectLink.receive();

                    if (!(message instanceof HDSLedgerMessage ledgerMessage))
                        continue;

                    new Thread(() -> {
                        switch (ledgerMessage.getType()) {
                            case APPEND -> uponAppend(ledgerMessage);

                            case READ -> uponRead(ledgerMessage);

                            default -> LOGGER.warn(MessageFormat.format("{0} - Received unknown message type: {1}",
                                    serverProcessConfig.getId(), ledgerMessage.getType()));
                        }
                    }).start();

                } catch (Exception e) {
                    LOGGER.error(MessageFormat.format("{0} - Error receiving message: {1}",
                            serverProcessConfig.getId(), e.getMessage()));
                }
            }
        }).start();
    }
}
