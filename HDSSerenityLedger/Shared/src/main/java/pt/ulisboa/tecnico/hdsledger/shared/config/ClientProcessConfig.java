package pt.ulisboa.tecnico.hdsledger.shared.config;

/**
 * Configuration of a client process.
 */
public class ClientProcessConfig extends ProcessConfig {
    private final String scriptPath;
    private final boolean useScript;

    public ClientProcessConfig(
            String id,
            String hostname,
            int port, // Receives and sends messages to the blockchain servers
            String scriptPath,
            boolean useScript,
            String privateKeyPath,
            String publicKeyPath,
            ProcessBehavior behavior
    ) {
        super(id, hostname, port, privateKeyPath, publicKeyPath, behavior);
        this.scriptPath = scriptPath;
        this.useScript = useScript;
    }

    public String getScriptPath() {
        return scriptPath;
    }
}
