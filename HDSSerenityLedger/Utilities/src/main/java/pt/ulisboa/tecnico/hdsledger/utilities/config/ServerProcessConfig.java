package pt.ulisboa.tecnico.hdsledger.utilities.config;

/**
 * Configuration of a server process.
 */
public class ServerProcessConfig extends ProcessConfig {
    private final int clientPort; // Receives and sends messages to the clients of the blockchain
    private final int crashTimeout;

    public ServerProcessConfig(
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

    public int getCrashTimeout() {
        return crashTimeout;
    }

    public int getClientPort() {
        return clientPort;
    }
}
