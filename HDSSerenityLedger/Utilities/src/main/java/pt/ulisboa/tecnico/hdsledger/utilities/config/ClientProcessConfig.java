package pt.ulisboa.tecnico.hdsledger.utilities.config;

/**
 * Configuration of a client process.
 */
public class ClientProcessConfig extends ProcessConfig {
    private String script;

    public ClientProcessConfig(String id, String hostname, int port, int clientPort, String script, String privateKeyPath, String publicKeyPath) {
        super(id, hostname, port,clientPort, privateKeyPath, publicKeyPath);
        this.script = script;
    }

    public String getScript() {
        return script;
    }
}
