package pt.ulisboa.tecnico.hdsledger.client;

import pt.ulisboa.tecnico.hdsledger.clientlibrary.ClientLibrary;
import pt.ulisboa.tecnico.hdsledger.clientlibrary.commands.AppendCommand;
import pt.ulisboa.tecnico.hdsledger.clientlibrary.commands.Command;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.config.ClientProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.config.ProcessConfigBuilder;
import pt.ulisboa.tecnico.hdsledger.utilities.config.ServerProcessConfig;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Objects;
import java.util.logging.Level;

/**
 * Client of the HDSLedger system.
 */
public class Client {

    private static final CustomLogger LOGGER = new CustomLogger(Client.class.getName());

    // Hardcoded path to files
    private static String clientsConfigPath = "src/main/resources/";
    private static String nodesConfigPath = "../Service/src/main/resources/";

    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("Usage: java Client <clientID> <clientConfig> <nodesConfig>");
            return;
        }

        String clientID = args[0];
        clientsConfigPath += args[1];
        nodesConfigPath += args[2];

        ClientProcessConfig[] clientsConfig = new ProcessConfigBuilder().fromFileClient(clientsConfigPath);
        ServerProcessConfig[] nodesConfig = new ProcessConfigBuilder().fromFileServer(nodesConfigPath);

        ClientProcessConfig clientConfig = Arrays.stream(clientsConfig).filter(c -> c.getId().equals(clientID)).findAny().get();

        LOGGER.log(Level.INFO, MessageFormat.format("{0} - Running at {1}:{2};", clientConfig.getId(),
                clientConfig.getHostname(), clientConfig.getPort()));

        ClientLibrary clientLibrary = new ClientLibrary(clientConfig, nodesConfig);

        String scriptFilePath = args[3];
        ScriptReader scriptReader = new ScriptReader(scriptFilePath);

        while (scriptReader.hasNext()) {
            Command command = scriptReader.next();

            if (Objects.requireNonNull(command) instanceof AppendCommand appendCommand) {
                clientLibrary.append(appendCommand);
            } else {
                LOGGER.log(Level.WARNING, "Unknown command: " + command);
            }
        }
    }
}
