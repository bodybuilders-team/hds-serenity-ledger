package pt.ulisboa.tecnico.hdsledger.utilities.config;

/**
 * Configuration of a server process.
 */
public class ServerProcessConfig extends ProcessConfig {
    private boolean isLeader;

    public ServerProcessConfig(String id, String hostname, int port, boolean isLeader, String privateKeyPath, String publicKeyPath) {
        super(id, hostname, port, privateKeyPath, publicKeyPath);
        this.isLeader = isLeader;
    }

    public boolean isLeader() {
        return isLeader;
    }
}
