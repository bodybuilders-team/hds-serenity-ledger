package pt.ulisboa.tecnico.hdsledger.utilities;

import java.text.MessageFormat;
import java.util.logging.Level;

public class NodeLogger extends CustomLogger {

    private final String nodeId;

    public NodeLogger(String name, String nodeId) {
        super(name);
        this.nodeId = nodeId;
    }


    @Override
    public void log(Level level, String message) {
        super.log(level, MessageFormat.format("\u001B[33m{0}\u001B[1m - \u001B[32m{1}\u001B[0m", nodeId, message));
    }

    public void info(String message) {
        super.info(message);
    }

    public void warn(String message) {
        super.warn(message);
    }

    public void error(String message) {
        super.error(message);
    }

    public void debug(String message) {
        super.debug(message);
    }
}
