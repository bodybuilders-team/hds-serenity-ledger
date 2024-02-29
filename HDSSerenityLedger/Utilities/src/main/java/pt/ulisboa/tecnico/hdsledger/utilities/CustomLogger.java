package pt.ulisboa.tecnico.hdsledger.utilities;

import java.util.logging.*;

/**
 * The {@code CustomLogger} class provides a customizable logging functionality.
 * <p>
 * It uses the standard Java logging framework and allows customization of log levels and formatting.
 */
public class CustomLogger {

    private static Logger LOGGER;

    /**
     * Constructs a {@code CustomLogger} with the specified name.
     *
     * @param name the name of the logger
     */
    public CustomLogger(String name) {
        LOGGER = Logger.getLogger(name);
        LOGGER.setLevel(Level.ALL);
        LOGGER.setUseParentHandlers(false);
        ConsoleHandler handler = new ConsoleHandler();

        Formatter formatter = new CustomLog();
        handler.setFormatter(formatter);

        LOGGER.addHandler(handler);
    }

    /**
     * Logs a message at the specified level.
     *
     * @param level   the log level
     * @param message the log message
     */
    public void log(Level level, String message) {
        LOGGER.log(level, message);
    }

}

/**
 * The {@code CustomLog} class defines a custom log formatter.
 */
class CustomLog extends Formatter {

    /**
     * Formats the given log record.
     *
     * @param record the log record to format
     * @return a formatted string representing the log record
     */
    @Override
    public String format(LogRecord record) {
        return record.getMessage() + '\n';
    }
}

