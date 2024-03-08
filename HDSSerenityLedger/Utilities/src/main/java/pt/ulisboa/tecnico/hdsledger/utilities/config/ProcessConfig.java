package pt.ulisboa.tecnico.hdsledger.utilities.config;

/**
 * Configuration of a process.
 */
public class ProcessConfig {
    private final String hostname;
    private final String id;
    private final int port;
    private final int clientPort;
    private final String privateKeyPath;
    private final String publicKeyPath;
    private final ProcessBehavior behavior;

    public ProcessConfig(
            String id,
            String hostname,
            int port,
            int clientPort,
            String privateKeyPath,
            String publicKeyPath,
            String behavior
    ) {
        this.id = id;
        this.hostname = hostname;
        this.port = port;
        this.clientPort = clientPort;
        this.privateKeyPath = privateKeyPath;
        this.publicKeyPath = publicKeyPath;

        switch (behavior) { // TODO: Unnecessary switch statement?
            case "NON_LEADER_CONSENSUS_INITIATION":
                this.behavior = ProcessBehavior.NON_LEADER_CONSENSUS_INITIATION;
                break;
            case "LEADER_IMPERSONATION":
                this.behavior = ProcessBehavior.LEADER_IMPERSONATION;
                break;
            case "CORRUPT_BROADCAST":
                this.behavior = ProcessBehavior.CORRUPT_BROADCAST;
                break;
            default:
                this.behavior = ProcessBehavior.REGULAR;
        }
    }

    public int getPort() {
        return port;
    }

    public String getId() {
        return id;
    }

    public String getHostname() {
        return hostname;
    }

    public String getPrivateKeyPath() {
        return privateKeyPath;
    }

    public String getPublicKeyPath() {
        return publicKeyPath;
    }

    public int getClientPort() {
        return clientPort;
    }

    public ProcessBehavior getBehavior() {
        return behavior;
    }

    public enum ProcessBehavior {
        REGULAR,
        CORRUPT_BROADCAST,                  // During broadcast, send different messages to different nodes

        // Nodes
        NON_LEADER_CONSENSUS_INITIATION,    // Initiate consensus without being the leader
        LEADER_IMPERSONATION,               // Send messages with leader ID
        CRASH_AFTER_FIXED_TIME,             // Crash after a fixed time
        CORRUPT_LEADER,                     // Leader sends different messages to different nodes

        // Clients
        // TODO: Add bad behaviors for clients, ... No Need?? supposedly clients are not byzantine
    }
}

