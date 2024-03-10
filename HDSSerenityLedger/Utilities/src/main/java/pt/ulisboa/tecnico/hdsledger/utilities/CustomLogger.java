package pt.ulisboa.tecnico.hdsledger.utilities;

import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * The {@code CustomLogger} class provides a customizable logging functionality.
 * <p>
 * It uses the standard Java logging framework and allows customization of log levels and formatting.
 */
public class CustomLogger {

    private static Logger logger;
    private boolean enabled = true;

    /**
     * Constructs a {@code CustomLogger} with the specified name.
     *
     * @param name the name of the logger
     */
    public CustomLogger(String name) {
        logger = Logger.getLogger(name);
        logger.setLevel(Level.ALL);
        logger.setUseParentHandlers(false);
        ConsoleHandler handler = new ConsoleHandler();

        Formatter formatter = new CustomLog();
        handler.setFormatter(formatter);

        logger.addHandler(handler);
    }

    /**
     * Disables logging.
     */
    public void disableLogging() {
        enabled = false;
    }

    /**
     * Logs a message at the specified level.
     *
     * @param level   the log level
     * @param message the log message
     */
    public void log(Level level, String message) {
        if (enabled)
            logger.log(level, levelToString(level) + " " + message);
    }

    public void info(String message) {
        if (enabled)
            log(Level.INFO, message);
    }

    public void warn(String message) {
        if (enabled)
            log(Level.WARNING, message);
    }

    public void error(String message) {
        if (enabled)
            log(Level.SEVERE, message);
    }

    public void debug(String message) {
        if (enabled)
            log(Level.FINE, message);
    }

    private String levelToString(Level level) {
        return switch (level.getName()) {
            case "INFO" -> "[\u001B[34m\u001B[1mINFO\u001B[37m]";
            case "WARNING" -> "[\u001B[33m\u001B[1mWARN\u001B[37m]";
            case "SEVERE" -> "[\u001B[31m\u001B[1mERROR\u001B[37m]";
            case "FINE" -> "[\u001B[32m\u001B[1mDEBUG\u001B[37m]";
            default -> "[" + level.getName() + "]";
        };
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
        return "\u001B[37;1m" + record.getMessage() + '\n';
    }
}

