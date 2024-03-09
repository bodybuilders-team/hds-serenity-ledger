package pt.ulisboa.tecnico.hdsledger.client;

import pt.ulisboa.tecnico.hdsledger.clientlibrary.ClientLibrary;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.config.ClientProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.config.ProcessConfigBuilder;
import pt.ulisboa.tecnico.hdsledger.utilities.config.ServerProcessConfig;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Scanner;

/**
 * Client of the HDS Serenity Ledger system.
 */
public class Client {

    // Hardcoded path to files
    private static String clientsConfigPath = "src/main/resources/";
    private static String nodesConfigPath = "../Service/src/main/resources/";
    private static String scriptPath = "src/main/resources/";

    private static boolean running = true;
    private static ClientProcessConfig clientConfig;
    private static ClientLibrary clientLibrary;

    public static void main(String[] args) throws InterruptedException {
        if (args.length > 4 || args.length < 3) {
            System.out.println("Usage: java Client <clientID> <clientConfig> <nodesConfig> [-script]");
            return;
        }

        String clientID = args[0];
        clientsConfigPath += args[1];
        nodesConfigPath += args[2];
        ProcessLogger logger = new ProcessLogger(Client.class.getName(), clientID);

        ClientProcessConfig[] clientsConfig = new ProcessConfigBuilder().fromFileClient(clientsConfigPath);
        ServerProcessConfig[] nodesConfig = new ProcessConfigBuilder().fromFileServer(nodesConfigPath);

        clientConfig = Arrays.stream(clientsConfig).filter(c -> c.getId().equals(clientID)).findAny().get();
        logger.info(MessageFormat.format("Running at \u001B[34m{0}:{1}\u001B[37m",
                clientConfig.getHostname(), String.valueOf(clientConfig.getPort())));

        clientLibrary = new ClientLibrary(clientConfig, nodesConfig);
        clientLibrary.listen();

        if (args.length == 4 && args[3].equals("-script"))
            runScript();

        runCLI();
    }

    /**
     * Runs the client in a loop, reading commands from the command line interface.
     *
     * @throws InterruptedException if the thread is interrupted
     */
    private static void runCLI() throws InterruptedException {
        printWelcomeMessage();
        Scanner in = new Scanner(System.in);

        while (running) {
            String command = in.nextLine().trim();
            executeCommand(command, false);
        }
    }

    /**
     * Runs the client in a loop, reading commands from a script.
     *
     * @throws RuntimeException if an I/O error occurs
     */
    private static void runScript() throws InterruptedException {
        scriptPath += clientConfig.getScriptPath();

        try (BufferedReader reader = new BufferedReader(new FileReader(scriptPath))) {
            while (running) {
                String line = reader.readLine();
                if (line == null)
                    break;

                String command = line.trim().substring(1, line.length() - 1); // Remove < and >

                System.out.println("Executing: " + command);
                executeCommand(command, true);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Parses a line and executes the parsed command.
     *
     * @param line     line to be parsed
     * @param isScript true if the command is being executed from a script; false if its from the command line interface
     * @throws InterruptedException if the thread is interrupted
     */
    private static void executeCommand(String line, boolean isScript) throws InterruptedException {
        String[] parts = line.trim().split(isScript ? ", " : " ", 2);
        String command = parts[0];
        String params = parts.length > 1 ? parts[1] : null;

        switch (command) {
            case "exit" -> running = false;
            case "read" -> clientLibrary.read();
            case "append" -> {
                if (params == null) {
                    System.out.println("Invalid command: append \"<message>\"");
                    return;
                }
                clientLibrary.append(params.substring(1, params.length() - 1));
            }
            case "sleep" -> {
                if (params == null) {
                    System.out.println("Invalid command: sleep <time>");
                    return;
                }
                Thread.sleep(Integer.parseInt(params));
            }
            case "help" -> printMenu();
            default -> {
                System.out.println("\u001B[31mInvalid command: " + command + "\u001B[0m");
                printMenu();
            }
        }
    }

    /**
     * Prints the welcome message.
     */
    private static void printWelcomeMessage() {
        System.out.print("""
                \u001B[34m\u001B[1m                    \s
                           __ _____  ____   ____                     __          __          __           \s
                          / // / _ \\/ __/  / __/__ _______ ___  ___ / /___ __   / /  ___ ___/ /__ ____ ____
                         / _  / // /\\ \\   _\\ \\/ -_) __/ -_) _ \\/ -_) __/ // /  / /__/ -_) _  / _ `/ -_) __/
                        /_//_/____/___/  /___/\\__/_/  \\__/_//_/\\__/\\__/\\_, /  /____/\\__/\\_,_/\\_, /\\__/_/  \s
                                                                      /___/                 /___/         \s
                                                                      
                Welcome to the HDS Serenity Ledger!
                \u001B[33mUsage:
                  \u001B[0mcommand [arguments]
                \u001B[0m\u001B[21m\u001B[24m
                """
        );
        printMenu();
    }

    /**
     * Prints the menu.
     */
    private static void printMenu() {
        System.out.println("""
                \u001B[33m\u001B[1mAvailable commands:\u001B[21m\u001B[24m
                    \u001B[32mread\u001B[0m                 Read the ledger
                    \u001B[32mappend "<message>"\u001B[0m   Append a message to the ledger
                    \u001B[32mexit\u001B[0m                 Exit the client
                    \u001B[32mhelp\u001B[0m                 Show this help message
                """
        );
        System.out.print("> ");
    }
}
