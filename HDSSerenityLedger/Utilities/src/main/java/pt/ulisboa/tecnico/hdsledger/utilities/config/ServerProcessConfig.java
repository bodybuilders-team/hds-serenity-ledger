package pt.ulisboa.tecnico.hdsledger.utilities.config;

/**
 * Configuration of a server process.
 */
public class ServerProcessConfig extends ProcessConfig {
    private boolean isLeader;

    public ServerProcessConfig(String id, String hostname, int port, int clientPort, boolean isLeader, String privateKeyPath, String publicKeyPath) {
        super(id, hostname, port, clientPort, privateKeyPath, publicKeyPath);
        this.isLeader = isLeader;
    }

    public boolean isLeader() {
        return isLeader;
    }
}
