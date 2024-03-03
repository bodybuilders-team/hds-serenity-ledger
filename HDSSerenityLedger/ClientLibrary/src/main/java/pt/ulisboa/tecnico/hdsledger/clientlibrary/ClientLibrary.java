package pt.ulisboa.tecnico.hdsledger.clientlibrary;

import pt.ulisboa.tecnico.hdsledger.clientlibrary.commands.AppendCommand;
import pt.ulisboa.tecnico.hdsledger.communication.AuthenticatedPerfectLink;
import pt.ulisboa.tecnico.hdsledger.communication.HDSLedgerMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.builder.HDSLedgerMessageBuilder;
import pt.ulisboa.tecnico.hdsledger.service.services.UDPService;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.config.ClientProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.config.ServerProcessConfig;

import java.text.MessageFormat;
import java.util.logging.Level;

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
            LOGGER.log(Level.SEVERE, MessageFormat.format("{0} - Error creating link: {1}",
                    clientConfig.getId(), e.getMessage()));
        }
    }

    /**
     * Appends a value to the ledger.
     *
     * @param command the append command
     */
    public void append(AppendCommand command) {
        LOGGER.log(Level.INFO, MessageFormat.format("{0} - Appending: {1}",
                clientConfig.getId(), command.getValue()));

        try {
            HDSLedgerMessage message = new HDSLedgerMessageBuilder(clientConfig.getId(), Message.Type.APPEND)
                    .setValue(command.getValue())
                    .build();

            authenticatedPerfectLink.broadcast(message);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, MessageFormat.format("{0} - Error sending append: {1}",
                    clientConfig.getId(), e.getMessage()));
        }
    }

    /**
     * Reads the ledger.
     */
    public void read() {
        LOGGER.log(Level.INFO, MessageFormat.format("{0} - Reading", clientConfig.getId()));

        try {
            HDSLedgerMessage message = new HDSLedgerMessageBuilder(clientConfig.getId(), Message.Type.READ)
                    .build();

            authenticatedPerfectLink.broadcast(message);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, MessageFormat.format("{0} - Error sending read: {1}",
                    clientConfig.getId(), e.getMessage()));
        }
    }

    @Override
    public void listen() {
        LOGGER.log(Level.INFO, MessageFormat.format("{0} - Listening for messages", clientConfig.getId()));

        new Thread(() -> {
            while (true) {
                try {
                    Message message = authenticatedPerfectLink.receive();

                    if (!(message instanceof HDSLedgerMessage))
                        continue;

                    HDSLedgerMessage ledgerMessage = (HDSLedgerMessage) message;
                    switch (ledgerMessage.getType()) {
                        case APPEND_RESPONSE ->
                                LOGGER.log(Level.INFO, MessageFormat.format("{0} - Received append response: {1}",
                                        clientConfig.getId(), ledgerMessage.getValue()));

                        case READ_RESPONSE ->
                                LOGGER.log(Level.INFO, MessageFormat.format("{0} - Received read response: {1}",
                                        clientConfig.getId(), ledgerMessage.getValue()));

                        default ->
                                LOGGER.log(Level.WARNING, MessageFormat.format("{0} - Received unknown message type: {1}",
                                        clientConfig.getId(), ledgerMessage.getType()));
                    }

                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, MessageFormat.format("{0} - Error receiving message: {1}",
                            clientConfig.getId(), e.getMessage()));
                }
            }
        }).start();
    }

    // TODO: Implement listening for messages from the server
}
