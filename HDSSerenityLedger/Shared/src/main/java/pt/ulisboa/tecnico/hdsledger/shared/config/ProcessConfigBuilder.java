package pt.ulisboa.tecnico.hdsledger.shared.config;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import pt.ulisboa.tecnico.hdsledger.shared.ErrorMessage;
import pt.ulisboa.tecnico.hdsledger.shared.HDSSException;
import pt.ulisboa.tecnico.hdsledger.shared.SerializationUtils;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Builder for ProcessConfig.
 */
public class ProcessConfigBuilder {

    /**
     * Returns the instance of ProcessConfig, read from a file.
     *
     * @param path The path to the file.
     * @return The instance of ProcessConfig.
     * @throws HDSSException If the file is not found or the format is incorrect.
     */
    public ProcessConfig[] fromFile(String path) {
        System.out.println(path);
        try (BufferedInputStream is = new BufferedInputStream(new FileInputStream(path))) {
            String input = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            Gson gson = SerializationUtils.getGson();
            return gson.fromJson(input, ProcessConfig[].class);
        } catch (FileNotFoundException e) {
            throw new HDSSException(ErrorMessage.ConfigFileNotFound);
        } catch (IOException | JsonSyntaxException e) {
            throw new HDSSException(ErrorMessage.ConfigFileFormat);
        }
    }

    /**
     * Returns the instance of ServerProcessConfig, read from a file.
     *
     * @param path The path to the file.
     * @return The instance of ServerProcessConfig.
     * @throws HDSSException If the file is not found or the format is incorrect.
     */
    public ServerProcessConfig[] fromFileServer(String path) {
        try (BufferedInputStream is = new BufferedInputStream(new FileInputStream(path))) {
            String input = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            Gson gson = SerializationUtils.getGson();
            return gson.fromJson(input, ServerProcessConfig[].class);
        } catch (FileNotFoundException e) {
            throw new HDSSException(ErrorMessage.ConfigFileNotFound);
        } catch (IOException | JsonSyntaxException e) {
            throw new HDSSException(ErrorMessage.ConfigFileFormat);
        }
    }

    /**
     * Returns the instance of ClientProcessConfig, read from a file.
     *
     * @param path The path to the file.
     * @return The instance of ClientProcessConfig.
     * @throws HDSSException If the file is not found or the format is incorrect.
     */
    public ClientProcessConfig[] fromFileClient(String path) {
        try (BufferedInputStream is = new BufferedInputStream(new FileInputStream(path))) {
            String input = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            Gson gson = SerializationUtils.getGson();
            return gson.fromJson(input, ClientProcessConfig[].class);
        } catch (FileNotFoundException e) {
            throw new HDSSException(ErrorMessage.ConfigFileNotFound);
        } catch (IOException | JsonSyntaxException e) {
            throw new HDSSException(ErrorMessage.ConfigFileFormat);
        }
    }
}


