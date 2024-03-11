package pt.ulisboa.tecnico.hdsledger.service.services;

import pt.ulisboa.tecnico.hdsledger.communication.AuthenticatedPerfectLink;
import pt.ulisboa.tecnico.hdsledger.communication.HDSLedgerMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.builder.HDSLedgerMessageBuilder;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessLogger;

import java.text.MessageFormat;
import java.util.List;

/**
 * Service used to interact with the HDSLedger.
 */
public class HDSLedgerService implements UDPService {

    private final NodeService nodeService;
    private final ProcessLogger logger;

    // Link to communicate with the clients
    private final AuthenticatedPerfectLink authenticatedPerfectLink;

    public HDSLedgerService(
            AuthenticatedPerfectLink authenticatedPerfectLink,
            NodeService nodeService
    ) {
        this.nodeService = nodeService;
        this.authenticatedPerfectLink = authenticatedPerfectLink;
        this.logger = new ProcessLogger(HDSLedgerService.class.getName(), nodeService.getConfig().getId());
    }

    /**
     * Handles an append message.
     *
     * @param message the append message
     */
    public void uponAppend(HDSLedgerMessage message) {
        logger.info(MessageFormat.format("Received {0} from client \u001B[33m{1}\u001B[37m", message.getHDSLedgerMessageRepresentation(), message.getSenderId()));
        logger.info(MessageFormat.format("Proceeding with a consensus trying to append \u001B[33m\"{0}\"\u001B[37m...", message.getValue()));

        try {
            nodeService.startConsensus(message.getValue());

            // Send the response
            HDSLedgerMessage response = new HDSLedgerMessageBuilder(nodeService.getConfig().getId(), Message.Type.APPEND_RESPONSE)
                    .setValue(MessageFormat.format("Received append request. Will try to append \"{0}\"", message.getValue()))
                    .build();

            authenticatedPerfectLink.send(message.getSenderId(), response);
        } catch (Exception e) {
            logger.error(MessageFormat.format("Error sending append: {0}", e.getMessage()));
        }
    }

    /**
     * Handles a read message.
     *
     * @param message the read message
     */
    public void uponRead(HDSLedgerMessage message) {
        logger.info("Reading from ledger...");

        try {
            List<String> ledger = nodeService.getLedger();
            String ledgerString = String.join(", ", ledger);

            logger.info(MessageFormat.format("Read from ledger: \u001B[33m\"{0}\"\u001B[37m - Sending response...", ledgerString));

            HDSLedgerMessage response = new HDSLedgerMessageBuilder(nodeService.getConfig().getId(), Message.Type.READ_RESPONSE)
                    .setValue(ledgerString)
                    .build();

            authenticatedPerfectLink.send(message.getSenderId(), response);
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
                    Message message = authenticatedPerfectLink.receive();

                    if (!(message instanceof HDSLedgerMessage ledgerMessage))
                        continue;

                    new Thread(() -> {
                        switch (ledgerMessage.getType()) {
                            case APPEND -> uponAppend(ledgerMessage);

                            case READ -> uponRead(ledgerMessage);

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
