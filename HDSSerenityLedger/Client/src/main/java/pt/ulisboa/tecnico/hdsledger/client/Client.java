package pt.ulisboa.tecnico.hdsledger.client;

import pt.ulisboa.tecnico.hdsledger.clientlibrary.ClientLibrary;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.config.ClientProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.config.ProcessConfigBuilder;
import pt.ulisboa.tecnico.hdsledger.utilities.config.ServerProcessConfig;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Objects;
import java.util.Scanner;
import java.util.logging.LogManager;

/**
 * Client of the HDSLedger system.
 */
public class Client {

    private static final CustomLogger LOGGER = new CustomLogger(Client.class.getName());

    // Hardcoded path to files
    private static String clientsConfigPath = "src/main/resources/";
    private static String nodesConfigPath = "../Service/src/main/resources/";
    private static String scriptPath = "src/main/resources/";

    private static boolean running = true;
    private static ClientLibrary clientLibrary;

    public static void main(String[] args) throws InterruptedException {
        if (args.length > 4 || args.length < 3) {
            System.out.println("Usage: java Client <clientID> <clientConfig> <nodesConfig> [<script>]");
            return;
        }
        // Disable Logging
        CustomLogger.disableLogging();

        String clientID = args[0];
        clientsConfigPath += args[1];
        nodesConfigPath += args[2];

        ClientProcessConfig[] clientsConfig = new ProcessConfigBuilder().fromFileClient(clientsConfigPath);
        ServerProcessConfig[] nodesConfig = new ProcessConfigBuilder().fromFileServer(nodesConfigPath);

        ClientProcessConfig clientConfig = Arrays.stream(clientsConfig).filter(c -> c.getId().equals(clientID)).findAny().get();

        LOGGER.info(MessageFormat.format("{0} - Running at {1}:{2};", clientConfig.getId(),
                clientConfig.getHostname(), String.valueOf(clientConfig.getPort())));

        clientLibrary = new ClientLibrary(clientConfig, nodesConfig);
        LOGGER.info(MessageFormat.format("{0} - Running at {1}:{2};", clientConfig.getId(),
                clientConfig.getHostname(), String.valueOf(clientConfig.getPort())));

        clientLibrary.listen();

        // If no script is provided, start the command line interface
        if (args.length == 3) {
            printWelcomeMessage();
            Scanner in = new Scanner(System.in);

            while (running) {
                printMenu();

                String command = in.nextLine().trim();
                executeCommand(command);
            }
            return;
        }

        String scriptFilePath = args[3];
        scriptPath += scriptFilePath;

        try (BufferedReader reader = new BufferedReader(new FileReader(scriptPath))) {
            while (running) {
                String line = reader.readLine();
                if (line == null)
                    break;

                String command = line.trim().substring(1, line.length() - 1);
                executeCommand(command);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Parses a line and executes the parsed command.
     *
     * @param line line to be parsed
     */
    private static void executeCommand(String line) throws InterruptedException {
        System.out.println("Executing: " + line);
        String[] parts = line.split(", \"");
        String command = parts[0];
        String params = parts.length > 1 ? parts[1].substring(0, parts[1].length() - 1) : null;

        switch (command) {
            case "exit" -> running = false;
            case "read" -> clientLibrary.read();
            case "append" -> clientLibrary.append(Objects.requireNonNull(params));
            case "sleep" -> Thread.sleep(Integer.parseInt(Objects.requireNonNull(params)));
            case "kill" -> clientLibrary.kill();
            default -> System.out.println("Unknown command");
        }
    }

    /**
     * Prints the welcome message.
     */
    private static void printWelcomeMessage() {
        System.out.println("""
                ########################################################
                #                                                      #
                #           Welcome to HDS Serenity Ledger!            #
                #                                                      #
                ########################################################"""
        );
    }

    /**
     * Prints the menu.
     */
    private static void printMenu() {
        System.out.println("""
                ########## Menu ###########
                # 1. read                 #
                # 2. append, "<message>"  #
                # 3. exit                 #
                ###########################"""
        );
        System.out.print("Enter your choice: ");
    }
}
