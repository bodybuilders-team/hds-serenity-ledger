package pt.ulisboa.tecnico.hdsledger.service.services;

import pt.ulisboa.tecnico.hdsledger.communication.AuthenticatedPerfectLink;
import pt.ulisboa.tecnico.hdsledger.communication.HDSLedgerMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.builder.HDSLedgerMessageBuilder;
import pt.ulisboa.tecnico.hdsledger.utilities.NodeLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.config.ServerProcessConfig;

import java.text.MessageFormat;
import java.util.ArrayList;

/**
 * Service used to interact with the HDSLedger.
 */
public class HDSLedgerService implements UDPService {

    private final NodeLogger LOGGER;

    private final NodeService nodeService;

    // Link to communicate with the clients
    private final AuthenticatedPerfectLink authenticatedPerfectLink;

    public HDSLedgerService(
            ServerProcessConfig serverProcessConfig,
            AuthenticatedPerfectLink authenticatedPerfectLink,
            NodeService nodeService
    ) {
        this.nodeService = nodeService;

        this.authenticatedPerfectLink = authenticatedPerfectLink;
        this.LOGGER = new NodeLogger(HDSLedgerService.class.getName(), serverProcessConfig.getId());
    }

    /**
     * Handles an append message.
     *
     * @param message the append message
     */
    public void uponAppend(HDSLedgerMessage message) {
        LOGGER.info(MessageFormat.format("Appending: {0}", message.getValue()));

        try {
            nodeService.startConsensus(message.getValue());

            // Send the response
            HDSLedgerMessage response = new HDSLedgerMessageBuilder(nodeService.getConfig().getId(), Message.Type.APPEND_RESPONSE)
                    .setValue("Value appended successfully")
                    .build();

            authenticatedPerfectLink.send(message.getSenderId(), response);
        } catch (Exception e) {
            LOGGER.error(MessageFormat.format("Error sending append: {0}", e.getMessage()));
        }
    }

    /**
     * Handles a read message.
     *
     * @param message the read message
     */
    public void uponRead(HDSLedgerMessage message) {
        LOGGER.info("Reading from ledger...");

        try {
            ArrayList<String> ledger = nodeService.getLedger();
            String ledgerString = String.join(", ", ledger);

            LOGGER.info(MessageFormat.format("Read from ledger: {0} - Sending response...",
                    ledgerString));

            HDSLedgerMessage response = new HDSLedgerMessageBuilder(nodeService.getConfig().getId(), Message.Type.READ_RESPONSE)
                    .setValue(ledgerString)
                    .build();

            authenticatedPerfectLink.send(message.getSenderId(), response);
        } catch (Exception e) {
            LOGGER.error(MessageFormat.format("Error sending read: {0}",
                    e.getMessage()));
        }
    }


    @Override
    public void listen() {
        LOGGER.info("Listening for messages");

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

                            case IGNORE -> {
                            }

                            default -> LOGGER.warn(MessageFormat.format("Received unknown message type: {0}",
                                    ledgerMessage.getType()));
                        }
                    }).start();

                } catch (Exception e) {
                    LOGGER.error(MessageFormat.format("Error receiving message: {0}",
                            e.getMessage()));
                }
            }
        }).start();
    }
}
