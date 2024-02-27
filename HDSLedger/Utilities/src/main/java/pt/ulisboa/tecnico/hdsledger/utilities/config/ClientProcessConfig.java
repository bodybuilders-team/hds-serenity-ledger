package pt.ulisboa.tecnico.hdsledger.utilities.config;

/**
 * Configuration of a client process.
 */
public class ClientProcessConfig extends ProcessConfig {
    private String script;

    public ClientProcessConfig(String id, String hostname, int port, String script) {
        super(id, hostname, port);
        this.script = script;
    }

    public String getScript() {
        return script;
    }
}
