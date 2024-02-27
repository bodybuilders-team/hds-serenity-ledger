package pt.ulisboa.tecnico.hdsledger.clientlibrary.commands;

/**
 * An append command.
 */
public class AppendCommand implements Command {
    private final String value;

    public AppendCommand(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
