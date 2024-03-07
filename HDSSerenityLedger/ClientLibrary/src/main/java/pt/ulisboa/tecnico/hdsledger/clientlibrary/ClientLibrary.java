package pt.ulisboa.tecnico.hdsledger.clientlibrary;

import pt.ulisboa.tecnico.hdsledger.communication.AuthenticatedPerfectLink;
import pt.ulisboa.tecnico.hdsledger.communication.HDSLedgerMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.builder.HDSLedgerMessageBuilder;
import pt.ulisboa.tecnico.hdsledger.service.services.UDPService;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.config.ClientProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.config.ServerProcessConfig;

import java.text.MessageFormat;

/**
 * API for the HDSLedger client.
 */
public class ClientLibrary implements UDPService {

    private static final CustomLogger LOGGER = new CustomLogger(ClientLibrary.class.getName());

    private final ClientProcessConfig clientConfig;
    private AuthenticatedPerfectLink authenticatedPerfectLink;

    public ClientLibrary(ClientProcessConfig clientConfig, ServerProcessConfig[] nodesConfig) {
        this.clientConfig = clientConfig;

        try {
            this.authenticatedPerfectLink = new AuthenticatedPerfectLink(clientConfig, clientConfig.getPort(), nodesConfig, HDSLedgerMessage.class, true, 200, true);
        } catch (Exception e) {
            LOGGER.error(MessageFormat.format("{0} - Error creating link: {1}",
                    clientConfig.getId(), e.getMessage()));
        }
    }

    /**
     * Appends a value to the ledger.
     *
     * @param value value to append
     */
    public void append(String value) {
        LOGGER.info(MessageFormat.format("{0} - Appending: {1}",
                clientConfig.getId(), value));

        try {
            HDSLedgerMessage message = new HDSLedgerMessageBuilder(clientConfig.getId(), Message.Type.APPEND)
                    .setValue(value)
                    .build();

            authenticatedPerfectLink.broadcast(message);
        } catch (Exception e) {
            LOGGER.error(MessageFormat.format("{0} - Error sending append: {1}",
                    clientConfig.getId(), e.getMessage()));
        }
    }

    /**
     * Reads the ledger.
     */
    public void read() {
        LOGGER.info(MessageFormat.format("{0} - Reading", clientConfig.getId()));

        try {
            HDSLedgerMessage message = new HDSLedgerMessageBuilder(clientConfig.getId(), Message.Type.READ).build();

            authenticatedPerfectLink.broadcast(message);
        } catch (Exception e) {
            LOGGER.error(MessageFormat.format("{0} - Error sending read: {1}",
                    clientConfig.getId(), e.getMessage()));
        }
    }

    @Override
    public void listen() {
        LOGGER.info(MessageFormat.format("{0} - Listening for messages", clientConfig.getId()));

        new Thread(() -> {
            while (true) {
                try {
                    Message message = authenticatedPerfectLink.receive();

                    if (!(message instanceof HDSLedgerMessage ledgerMessage))
                        continue;

                    switch (ledgerMessage.getType()) {
                        case APPEND_RESPONSE -> {
                            LOGGER.info(MessageFormat.format("{0} - Received append response: {1}",
                                    clientConfig.getId(), ledgerMessage.getValue()));
                            System.out.println("Received append response: " + ledgerMessage.getValue());
                        }
                        case READ_RESPONSE -> {
                            LOGGER.info(MessageFormat.format("{0} - Received read response: {1}",
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
