package pt.ulisboa.tecnico.hdsledger.shared.config;

import lombok.Getter;

/**
 * Configuration of a process.
 */
@Getter
public class ProcessConfig {
    private final String hostname;
    private final String id;
    private final int port;
    private final String privateKeyPath;
    private final String publicKeyPath;
    private final ProcessBehavior behavior;

    public ProcessConfig(
            String id,
            String hostname,
            int port,
            String privateKeyPath,
            String publicKeyPath,
            ProcessBehavior behavior
    ) {
        this.id = id;
        this.hostname = hostname;
        this.port = port;
        this.privateKeyPath = privateKeyPath;
        this.publicKeyPath = publicKeyPath;
        this.behavior = behavior;
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

