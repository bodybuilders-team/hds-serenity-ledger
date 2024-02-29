package pt.ulisboa.tecnico.hdsledger.utilities.config;

/**
 * Configuration of a process.
 */
public class ProcessConfig {
    private String hostname;
    private String id;
    private int port;
    private String privateKeyPath;
    private String publicKeyPath;

    public ProcessConfig(String id, String hostname, int port, String privateKeyPath, String publicKeyPath) {
        this.id = id;
        this.hostname = hostname;
        this.port = port;
        this.privateKeyPath = privateKeyPath;
        this.publicKeyPath = publicKeyPath;
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
}

