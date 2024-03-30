package pt.ulisboa.tecnico.hdsledger.shared.logger;

import java.text.MessageFormat;
import java.util.logging.Level;

/**
 * Logger for a process.
 */
public class ProcessLogger extends CustomLogger {

    private final String nodeId;

    public ProcessLogger(String name, String nodeId) {
        super(name);
        this.nodeId = nodeId;
    }

    @Override
    public void log(Level level, String message) {
        super.log(level, MessageFormat.format("\u001B[33m{0}\u001b[37m - {1}", nodeId, message));
    }
}
