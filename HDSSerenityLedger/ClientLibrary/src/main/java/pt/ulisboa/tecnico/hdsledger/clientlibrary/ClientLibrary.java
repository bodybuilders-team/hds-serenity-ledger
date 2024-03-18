package pt.ulisboa.tecnico.hdsledger.clientlibrary;

import pt.ulisboa.tecnico.hdsledger.communication.AuthenticatedPerfectLink;
import pt.ulisboa.tecnico.hdsledger.communication.HDSLedgerMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.builder.HDSLedgerMessageBuilder;
import pt.ulisboa.tecnico.hdsledger.service.services.UDPService;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.config.ClientProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.config.ServerProcessConfig;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;

/**
 * API for the HDSLedger client.
 */
public class ClientLibrary implements UDPService {

    private final ClientProcessConfig clientConfig;
    private final ProcessLogger logger;
    private AuthenticatedPerfectLink authenticatedPerfectLink;

    private static final boolean LOGS_ENABLED = false;

    // Read response -> sender ID -> Message
    private final Map<String, Map<String, HDSLedgerMessage>> readResponses = new HashMap<>();
    private int quorumSize;

    public ClientLibrary(ClientProcessConfig clientConfig, ServerProcessConfig[] nodesConfig) {
        this.clientConfig = clientConfig;
        this.logger = new ProcessLogger(ClientLibrary.class.getName(), clientConfig.getId());

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
     * Appends a value to the ledger.
     *
     * @param value value to append
     */
    public void append(String value) {
        logger.info(MessageFormat.format("Appending: \"{0}\"", value));

        try {
            HDSLedgerMessage message = new HDSLedgerMessageBuilder(clientConfig.getId(), Message.Type.APPEND)
                    .setValue(value)
                    .build();

            authenticatedPerfectLink.broadcast(message);
        } catch (Exception e) {
            logger.error(MessageFormat.format("Error sending append: {0}", e.getMessage()));
        }
    }

    /**
     * Reads the ledger.
     */
    public void read() {
        logger.info("Reading...");

        try {
            HDSLedgerMessage message = new HDSLedgerMessageBuilder(clientConfig.getId(), Message.Type.READ).build();

            authenticatedPerfectLink.broadcast(message);
        } catch (Exception e) {
            logger.error(MessageFormat.format("Error sending read: {0}", e.getMessage()));
        }
    }

    /**
     * Handles a read response.
     */
    private void handleReadResponse(HDSLedgerMessage ledgerMessage) {
        if (ledgerMessage.getType() != Message.Type.READ_RESPONSE)
            return;

        synchronized (readResponses) {
            // TODO: does order of read responses matter? (sending two reads and receiving the responses in different order)
            readResponses.putIfAbsent(ledgerMessage.getValue(), new HashMap<>());
            readResponses.get(ledgerMessage.getValue()).putIfAbsent(ledgerMessage.getSenderId(), ledgerMessage);

            if (readResponses.get(ledgerMessage.getValue()).size() != quorumSize)
                return;
        }

        logger.info(MessageFormat.format("Received read response: \"{0}\"", ledgerMessage.getValue()));
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
                        case APPEND_RESPONSE ->
                                logger.info(MessageFormat.format("Received append response: \"{0}\"", ledgerMessage.getValue()));
                        case READ_RESPONSE -> handleReadResponse(ledgerMessage);
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
