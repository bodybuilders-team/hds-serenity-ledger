package pt.ulisboa.tecnico.hdsledger.shared.logger;

import java.util.List;
import java.util.Set;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The {@code CustomLogger} class provides a customizable logging functionality.
 * <p>
 * It uses the standard Java logging framework and allows customization of log levels and formatting.
 */
public class CustomLogger {

    private static final boolean ENABLE_COLOR_PARSING = true;
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

        Formatter formatter = new CustomLog(ENABLE_COLOR_PARSING);
        handler.setFormatter(formatter);

        handler.setLevel(Level.ALL);
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
        log(Level.INFO, message);
    }

    public void warn(String message) {
        log(Level.WARNING, message);
    }

    public void error(String message) {
        log(Level.SEVERE, message);
    }

    public void debug(String message) {
        log(Level.FINE, message);
    }

    private String levelToString(Level level) {
        return switch (level.getName()) {
            case "INFO" -> "[\u001B[34mINFO\u001B[37m]";
            case "WARNING" -> "[\u001B[33mWARN\u001B[37m]";
            case "SEVERE" -> "[\u001B[31mERROR\u001B[37m]";
            case "FINE" -> "[\u001B[32mDEBUG\u001B[37m]";
            default -> "[" + level.getName() + "]";
        };
    }
}

/**
 * The {@code CustomLog} class defines a custom log formatter.
 */
class CustomLog extends Formatter {

    private static final Set<String> greenWords = Set.of(
            "PREPARE", "COMMIT", "PRE-PREPARE", "ROUND-CHANGE",
            "TRANSFER", "TRANSFER-RESPONSE", "BALANCE", "BALANCE-RESPONSE",
            "ACK", "LEDGER-ACK"
    );
    private final boolean enableColorParsing;

    CustomLog(boolean enableColorParsing) {
        this.enableColorParsing = enableColorParsing;
    }

    /**
     * Formats the given log record.
     *
     * @param record the log record to format
     * @return a formatted string representing the log record
     */
    @Override
    public String format(LogRecord record) {
        if (!enableColorParsing) {
            return "\u001B[37;1m" + record.getMessage() + '\n';
        }
        String originalMessage = record.getMessage();

        StringBuilder formattedMessage = buildColorFormattedMessaged(originalMessage);

        return "\u001B[37;1m" + formattedMessage + '\n';
    }

    private static StringBuilder buildColorFormattedMessaged(String originalMessage) {
        Pattern wordPattern = Pattern.compile("[a-zA-Z-]+");
        Pattern numberPattern = Pattern.compile("[0-9]+");
        Pattern quotedStringPattern = Pattern.compile("\".*?\"");
        Pattern nodePattern = Pattern.compile("node [0-9a-zA-Z]+");

        Pattern enclosingEscapeColorPattern = Pattern.compile("(\\u001B\\[[0-9]+(;[0-9]+)?m).*?(\\u001B\\[37m)");

        // Define regex pattern to capture all specified tokens
        Pattern pattern = getCombinedPattern(List.of(wordPattern, numberPattern, quotedStringPattern, nodePattern, enclosingEscapeColorPattern));

        Matcher matcher = pattern.matcher(originalMessage);

        StringBuilder formattedMessage = new StringBuilder();

        int lastEnd = 0;

        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            String word = matcher.group();

            // Append the non-matched part between the current match and the previous one
            formattedMessage.append(originalMessage, lastEnd, start);

            // Apply color based on the type of token
            if (word.matches(wordPattern.pattern())) {
                if (greenWords.contains(word)) {
                    word = "\u001B[32m" + word + "\u001B[37m"; // Green
                }
            } else if (word.matches(numberPattern.pattern())) {
                word = "\u001B[34m" + word + "\u001B[37m"; // Blue
            } else if (word.matches(quotedStringPattern.pattern())) {
                word = "\u001B[33m" + word + "\u001B[37m"; // Yellow
            } else if (word.matches(nodePattern.pattern())) {
                word = "node " + "\u001B[33m" + word.substring(5) + "\u001B[37m"; // Yellow
            }

            formattedMessage.append(word);
            lastEnd = end;
        }

        formattedMessage.append(originalMessage, lastEnd, originalMessage.length());
        return formattedMessage;
    }

    public static Pattern getCombinedPattern(List<Pattern> patterns) {
        StringBuilder patternBuilder = new StringBuilder();
        for (int i = 0; i < patterns.size(); i++) {
            patternBuilder.append("(").append(patterns.get(i).pattern()).append(")");
            if (i != patterns.size() - 1) {
                patternBuilder.append("|");
            }
        }
        return Pattern.compile(patternBuilder.toString());
    }
}

