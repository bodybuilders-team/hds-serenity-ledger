package pt.ulisboa.tecnico.hdsledger.clientlibrary;

import pt.ulisboa.tecnico.hdsledger.communication.AuthenticatedPerfectLink;
import pt.ulisboa.tecnico.hdsledger.communication.HDSLedgerMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.builder.HDSLedgerMessageBuilder;
import pt.ulisboa.tecnico.hdsledger.service.services.UDPService;
import pt.ulisboa.tecnico.hdsledger.utilities.NodeLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.config.ClientProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.config.ServerProcessConfig;

import java.text.MessageFormat;

/**
 * API for the HDSLedger client.
 */
public class ClientLibrary implements UDPService {

    private final NodeLogger LOGGER;

    private final ClientProcessConfig clientConfig;
    private AuthenticatedPerfectLink authenticatedPerfectLink;

    public ClientLibrary(ClientProcessConfig clientConfig, ServerProcessConfig[] nodesConfig) {
        this.clientConfig = clientConfig;
        this.LOGGER = new NodeLogger(ClientLibrary.class.getName(), clientConfig.getId());

        try {
            this.authenticatedPerfectLink = new AuthenticatedPerfectLink(clientConfig, clientConfig.getPort(), nodesConfig, HDSLedgerMessage.class, true, 200, true);
        } catch (Exception e) {
            LOGGER.error(MessageFormat.format("Error creating link: {0}",
                    e.getMessage()));
        }
    }

    /**
     * Appends a value to the ledger.
     *
     * @param value value to append
     */
    public void append(String value) {
        LOGGER.info(MessageFormat.format("Appending: {0}", value));

        try {
            HDSLedgerMessage message = new HDSLedgerMessageBuilder(clientConfig.getId(), Message.Type.APPEND)
                    .setValue(value)
                    .build();

            authenticatedPerfectLink.broadcast(message);
        } catch (Exception e) {
            LOGGER.error(MessageFormat.format("Error sending append: {0}",
                    e.getMessage()));
        }
    }

    /**
     * Reads the ledger.
     */
    public void read() {
        LOGGER.info("Reading...");

        try {
            HDSLedgerMessage message = new HDSLedgerMessageBuilder(clientConfig.getId(), Message.Type.READ).build();

            authenticatedPerfectLink.broadcast(message);
        } catch (Exception e) {
            LOGGER.error(MessageFormat.format("Error sending read: {0}",
                    e.getMessage()));
        }
    }

    @Override
    public void listen() {
        LOGGER.info("Listening for messages...");

        new Thread(() -> {
            while (true) {
                try {
                    Message message = authenticatedPerfectLink.receive();

                    if (!(message instanceof HDSLedgerMessage ledgerMessage))
                        continue;

                    switch (ledgerMessage.getType()) {
                        case APPEND_RESPONSE -> {
                            LOGGER.info(MessageFormat.format("Received append response: {1}",
                                    ledgerMessage.getValue()));
                            System.out.println("Received append response: " + ledgerMessage.getValue());
                        }
                        case READ_RESPONSE -> {
                            LOGGER.info(MessageFormat.format("Received read response: {1}",
                                    clientConfig.getId(), ledgerMessage.getValue()));
                            System.out.println("Received read response: " + ledgerMessage.getValue());
                        }
                        case IGNORE -> {
                        }
                        default -> {
                            LOGGER.warn(MessageFormat.format("{0} - Received unknown message type: {1}",
                                    clientConfig.getId(), ledgerMessage.getType()));
                            System.out.println("Received unknown message type: " + ledgerMessage.getType());
                        }
                    }

                } catch (Exception e) {
                    LOGGER.error(MessageFormat.format("{0} - Error receiving message: {1}",
                            clientConfig.getId(), e.getMessage()));
                }
            }
        }).start();
    }
}
