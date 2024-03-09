package pt.ulisboa.tecnico.hdsledger.service;

import pt.ulisboa.tecnico.hdsledger.communication.AuthenticatedPerfectLink;
import pt.ulisboa.tecnico.hdsledger.communication.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.communication.HDSLedgerMessage;
import pt.ulisboa.tecnico.hdsledger.service.services.HDSLedgerService;
import pt.ulisboa.tecnico.hdsledger.service.services.NodeService;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.config.ClientProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.config.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.config.ProcessConfigBuilder;
import pt.ulisboa.tecnico.hdsledger.utilities.config.ServerProcessConfig;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

/**
 * A node in the system.
 */
public class Node {

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
        try {
            if (args.length != 3)
                throw new IllegalArgumentException("Usage: Node <id> <nodesConfigPath> <clientsConfigPath>");

            // Command line arguments
            String id = args[0];
            nodesConfigPath += args[1];
            clientsConfigPath += args[2];

            ProcessLogger logger = new ProcessLogger(Node.class.getName(), id);

            // Create configuration instances
            ServerProcessConfig[] nodeConfigs = new ProcessConfigBuilder().fromFileServer(nodesConfigPath);
            ClientProcessConfig[] clientConfigs = new ProcessConfigBuilder().fromFileClient(clientsConfigPath);
            ServerProcessConfig nodeConfig = Arrays.stream(nodeConfigs).filter(c -> c.getId().equals(id)).findAny().get();

            logger.info(MessageFormat.format("Running at \u001B[34m{0}:{1}\u001B[37m", nodeConfig.getHostname(), String.valueOf(nodeConfig.getPort())));

            // Abstraction to send and receive messages
            AuthenticatedPerfectLink authenticatedPerfectLinkToNodes = new AuthenticatedPerfectLink(nodeConfig, nodeConfig.getPort(), nodeConfigs, ConsensusMessage.class);
            AuthenticatedPerfectLink authenticatedPerfectLinkToClients = new AuthenticatedPerfectLink(nodeConfig, nodeConfig.getClientPort(), clientConfigs, HDSLedgerMessage.class);

            if (nodeConfig.getBehavior().equals(ProcessConfig.ProcessBehavior.CRASH_AFTER_FIXED_TIME)) {
                var crashTimeout = nodeConfig.getCrashTimeout();
                new Timer().schedule(new TimerTask() {
                    public void run() {
                        System.exit(0);
                    }
                }, crashTimeout);
            }

            // Service to handle the node's logic - consensus
            NodeService nodeService = new NodeService(authenticatedPerfectLinkToNodes, nodeConfig, nodeConfigs);

            // Service to handle the node's logic - ledger
            HDSLedgerService hdsLedgerService = new HDSLedgerService(authenticatedPerfectLinkToClients, nodeService);

            nodeService.listen();
            hdsLedgerService.listen();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
