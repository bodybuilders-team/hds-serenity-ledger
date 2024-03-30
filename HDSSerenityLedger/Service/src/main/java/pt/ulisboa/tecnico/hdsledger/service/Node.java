package pt.ulisboa.tecnico.hdsledger.service;

import pt.ulisboa.tecnico.hdsledger.service.services.LedgerService;
import pt.ulisboa.tecnico.hdsledger.service.services.NodeService;
import pt.ulisboa.tecnico.hdsledger.shared.communication.AuthenticatedPerfectLink;
import pt.ulisboa.tecnico.hdsledger.shared.config.ClientProcessConfig;
import pt.ulisboa.tecnico.hdsledger.shared.config.NodeProcessConfig;
import pt.ulisboa.tecnico.hdsledger.shared.config.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.shared.config.ProcessConfigBuilder;
import pt.ulisboa.tecnico.hdsledger.shared.logger.ProcessLogger;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

/**
 * A node in the system.
 */
public class Node {

    private static final boolean ACTIVATE_AUTHENTICATED_LINK_NODE_LOGGING = true;
    private static final boolean ACTIVATE_AUTHENTICATED_LINK_CLIENT_LOGGING = true;
    // Hardcoded path to files
    private static String nodesConfigPath = "src/main/resources/";
    private static String clientsConfigPath = "../Client/src/main/resources/";

    /**
     * Entry point for the node.
     * Receives the id of the node and the path to the configuration file.
     *
     * @param args Command line arguments.
     */
    public static void main(String[] args) {
        if (args.length != 3)
            throw new IllegalArgumentException("Usage: Node <id> <nodesConfigPath> <clientsConfigPath>");

        // Command line arguments
        String id = args[0];
        nodesConfigPath += args[1];
        clientsConfigPath += args[2];

        ProcessLogger logger = new ProcessLogger(Node.class.getName(), id);

        // Create configuration instances
        NodeProcessConfig[] nodeConfigs = new ProcessConfigBuilder().fromFileNode(nodesConfigPath);
        ClientProcessConfig[] clientConfigs = new ProcessConfigBuilder().fromFileClient(clientsConfigPath);
        NodeProcessConfig nodeConfig = Arrays.stream(nodeConfigs).filter(c -> c.getId().equals(id)).findAny()
                .orElseThrow(() -> new IllegalArgumentException("Node id not found in configuration file"));

        logger.info(MessageFormat.format("Running at \u001B[34m{0}:{1}\u001B[37m", nodeConfig.getHostname(), String.valueOf(nodeConfig.getPort())));

        // Abstraction to send and receive messages
        AuthenticatedPerfectLink authenticatedPerfectLinkToNodes = new AuthenticatedPerfectLink(nodeConfig, nodeConfig.getPort(), nodeConfigs, ACTIVATE_AUTHENTICATED_LINK_NODE_LOGGING);
        AuthenticatedPerfectLink authenticatedPerfectLinkToClients = new AuthenticatedPerfectLink(nodeConfig, nodeConfig.getClientPort(), clientConfigs, ACTIVATE_AUTHENTICATED_LINK_CLIENT_LOGGING);

        if (nodeConfig.getBehavior().equals(ProcessConfig.ProcessBehavior.CRASH_AFTER_FIXED_TIME)) {
            var crashTimeout = nodeConfig.getCrashTimeout();
            new Timer().schedule(new TimerTask() {
                public void run() {
                    System.exit(0);
                }
            }, crashTimeout);
        }
        final MessageAccumulator messageAccumulator = new MessageAccumulator(nodeConfig);

        // Service to handle the node's logic - consensus
        NodeService nodeService = new NodeService(authenticatedPerfectLinkToNodes, authenticatedPerfectLinkToClients, nodeConfig, nodeConfigs, clientConfigs, messageAccumulator);

        // Service to handle the node's logic - ledger
        LedgerService ledgerService = new LedgerService(authenticatedPerfectLinkToClients, nodeService, clientConfigs, messageAccumulator);

        nodeService.listen();
        ledgerService.listen();
    }
}
