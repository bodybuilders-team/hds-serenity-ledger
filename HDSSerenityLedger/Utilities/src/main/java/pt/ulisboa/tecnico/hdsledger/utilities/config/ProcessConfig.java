package pt.ulisboa.tecnico.hdsledger.utilities.config;

/**
 * Configuration of a process.
 */
public class ProcessConfig {
    private String hostname;
    private String id;
    private int port;
    private int clientPort;
    private String privateKeyPath;
    private String publicKeyPath;

    public ProcessConfig(String id, String hostname, int port, int clientPort, String privateKeyPath, String publicKeyPath) {
        this.id = id;
        this.hostname = hostname;
        this.port = port;
        this.clientPort = clientPort;
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

    public int getClientPort() {
        return clientPort;
    }
}

