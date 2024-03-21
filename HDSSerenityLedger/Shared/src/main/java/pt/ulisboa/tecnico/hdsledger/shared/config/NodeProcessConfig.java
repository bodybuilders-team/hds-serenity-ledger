package pt.ulisboa.tecnico.hdsledger.shared.config;

import lombok.Getter;

/**
 * Configuration of a server process.
 */
@Getter
public class NodeProcessConfig extends ProcessConfig {
    private final int clientPort; // Receives and sends messages to the clients of the blockchain
    private final int crashTimeout;

    public NodeProcessConfig(
            String id,
            String hostname,
            int port,
            int clientPort,
            String privateKeyPath,
            String publicKeyPath,
            ProcessBehavior behavior,
            int crashTimeout
    ) {
        super(id, hostname, port, privateKeyPath, publicKeyPath, behavior);
        this.crashTimeout = crashTimeout;
        this.clientPort = clientPort;
    }

}
