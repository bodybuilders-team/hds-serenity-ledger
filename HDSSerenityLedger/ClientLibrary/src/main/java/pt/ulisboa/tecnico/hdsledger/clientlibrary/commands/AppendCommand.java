package pt.ulisboa.tecnico.hdsledger.clientlibrary.commands;

/**
 * A command to append a value to the ledger.
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

