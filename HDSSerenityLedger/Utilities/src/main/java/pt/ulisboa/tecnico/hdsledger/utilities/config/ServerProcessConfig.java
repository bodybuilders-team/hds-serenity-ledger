package pt.ulisboa.tecnico.hdsledger.utilities.config;

/**
 * Configuration of a server process.
 */
public class ServerProcessConfig extends ProcessConfig {
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
        super(id, hostname, port, clientPort, privateKeyPath, publicKeyPath, behavior);
        this.crashTimeout = crashTimeout;
    }

    public int getCrashTimeout() {
        return crashTimeout;
    }
}
