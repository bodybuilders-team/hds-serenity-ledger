package pt.ulisboa.tecnico.hdsledger.utilities.config;

/**
 * Configuration of a process.
 */
public class ProcessConfig {
    private String hostname;
    private String id;
    private int port;

    public ProcessConfig(String id, String hostname, int port) {
        this.id = id;
        this.hostname = hostname;
        this.port = port;
    }

    public ProcessConfig() {
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
}

