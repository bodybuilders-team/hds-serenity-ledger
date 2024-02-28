package pt.ulisboa.tecnico.hdsledger.clientlibrary;

import pt.ulisboa.tecnico.hdsledger.clientlibrary.commands.AppendCommand;
import pt.ulisboa.tecnico.hdsledger.communication.HDSLedgerMessage;
import pt.ulisboa.tecnico.hdsledger.communication.AuthenticatedPerfectLink;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.communication.builder.HDSLedgerMessageBuilder;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.config.ClientProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.config.ServerProcessConfig;

import java.text.MessageFormat;
import java.util.logging.Level;

/**
 * API for the HDSLedger client.
 */
public class ClientLibrary {

    private static final CustomLogger LOGGER = new CustomLogger(ClientLibrary.class.getName());

    private final ClientProcessConfig clientConfig;
    private AuthenticatedPerfectLink authenticatedPerfectLink;

    public ClientLibrary(ClientProcessConfig clientConfig, ServerProcessConfig[] nodesConfig) {
        this.clientConfig = clientConfig;

        try {
            this.authenticatedPerfectLink = new AuthenticatedPerfectLink(clientConfig, clientConfig.getPort(), nodesConfig, HDSLedgerMessage.class);
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

            authenticatedPerfectLink.send(clientConfig.getId(), message);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, MessageFormat.format("{0} - Error sending append: {1}",
                    clientConfig.getId(), e.getMessage()));
        }
    }
}
