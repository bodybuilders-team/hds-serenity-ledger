package pt.ulisboa.tecnico.hdsledger.utilities.config;

/**
 * Configuration of a client process.
 */
public class ClientProcessConfig extends ProcessConfig {
    private final String scriptPath;

    public ClientProcessConfig(
            String id,
            String hostname,
            int port,
            int clientPort,
            String scriptPath,
            String privateKeyPath,
            String publicKeyPath,
            ProcessBehavior behavior
    ) {
        super(id, hostname, port, clientPort, privateKeyPath, publicKeyPath, behavior);
        this.scriptPath = scriptPath;
    }

    public String getScriptPath() {
        return scriptPath;
    }
}
