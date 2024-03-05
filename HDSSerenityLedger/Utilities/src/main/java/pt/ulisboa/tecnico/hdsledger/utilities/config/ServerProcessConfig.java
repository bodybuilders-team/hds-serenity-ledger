package pt.ulisboa.tecnico.hdsledger.utilities.config;

/**
 * Configuration of a server process.
 */
public class ServerProcessConfig extends ProcessConfig {
    private final boolean isLeader;
    private final int crashTimeout;

    public ServerProcessConfig(
            String id,
            String hostname,
            int port,
            int clientPort,
            boolean isLeader,
            String privateKeyPath,
            String publicKeyPath,
            String behavior, int crashTimeout
    ) {
        super(id, hostname, port, clientPort, privateKeyPath, publicKeyPath, behavior);
        this.isLeader = isLeader;
        this.crashTimeout = crashTimeout;
    }

    public boolean isLeader() {
        return isLeader;
    }

    public int getCrashTimeout() {
        return crashTimeout;
    }
}
