package pt.ulisboa.tecnico.hdsledger.client;

import pt.ulisboa.tecnico.hdsledger.clientlibrary.commands.AppendCommand;
import pt.ulisboa.tecnico.hdsledger.clientlibrary.commands.Command;
import pt.ulisboa.tecnico.hdsledger.clientlibrary.commands.ReadCommand;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/**
 * Reads a script file and provides methods to obtain the next command in the script.
 */
class ScriptReader {
    private BufferedReader reader;

    public ScriptReader(String filePath) {
        try {
            this.reader = new BufferedReader(new FileReader(filePath));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Checks if there are more commands to read.
     *
     * @return true if there are more commands to read, false otherwise
     */
    public boolean hasNext() {
        try {
            return reader.ready();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Returns the next command in the script.
     *
     * @return the next command in the script
     */
    public Command next() {
        try {
            String line = reader.readLine();
            if (line == null) {
                return null;
            }
            line = line.substring(1, line.length() - 1); // Remove the "<" and ">" characters
            String[] parts = line.split(", \""); // Split the line into operation and value
            String operation = parts[0];
            String value = parts.length > 1 ? parts[1].substring(0, parts[1].length() -1) : null;
            if (operation.equals("append")) {
                return new AppendCommand(value);
            } else if (operation.equals("read")) {
                return new ReadCommand();
            }
            // TODO: Add other operations here if needed
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
