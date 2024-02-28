package pt.ulisboa.tecnico.hdsledger.service.services;

import pt.ulisboa.tecnico.hdsledger.communication.HDSLedgerMessage;
import pt.ulisboa.tecnico.hdsledger.communication.AuthenticatedPerfectLink;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.config.ClientProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.config.ServerProcessConfig;

import java.text.MessageFormat;
import java.util.logging.Level;

/**
 * Service used to interact with the HDSLedger.
 */
public class HDSLedgerService implements UDPService {

    private static final CustomLogger LOGGER = new CustomLogger(HDSLedgerService.class.getName());

    private final ClientProcessConfig[] clientProcessConfigs;
    private final ServerProcessConfig serverProcessConfig;

    // Link to communicate with the clients
    private final AuthenticatedPerfectLink authenticatedPerfectLink;

    private final NodeService nodeService;

    public HDSLedgerService(ClientProcessConfig[] clientProcessConfigs, ServerProcessConfig serverProcessConfig, AuthenticatedPerfectLink authenticatedPerfectLink, NodeService nodeService) {
        this.clientProcessConfigs = clientProcessConfigs;
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
        LOGGER.log(Level.INFO, MessageFormat.format("{0} - Appending: {1}",
                serverProcessConfig.getId(), message.getValue()));

        try {
            // TODO: mandar para o nodeService a mensagem para append (possivelmente meter numa queue ou assim) para ele tratar
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, MessageFormat.format("{0} - Error sending append: {1}",
                    serverProcessConfig.getId(), e.getMessage()));
        }
    }

    @Override
    public void listen() {
        LOGGER.log(Level.INFO, MessageFormat.format("{0} - Listening for messages", serverProcessConfig.getId()));

        new Thread(() -> {
            while (true) {
                try {
                    HDSLedgerMessage message = (HDSLedgerMessage) authenticatedPerfectLink.receive();

                    new Thread(() -> {
                        switch (message.getType()) {
                            case APPEND -> uponAppend(message);

                            default ->
                                    LOGGER.log(Level.WARNING, MessageFormat.format("{0} - Received unknown message type: {1}",
                                            serverProcessConfig.getId(), message.getType()));
                        }
                    }).start();

                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, MessageFormat.format("{0} - Error receiving message: {1}",
                            serverProcessConfig.getId(), e.getMessage()));
                }
            }
        }).start();
    }
}
