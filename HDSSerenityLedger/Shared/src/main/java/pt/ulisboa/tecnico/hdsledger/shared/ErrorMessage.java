package pt.ulisboa.tecnico.hdsledger.shared;

import lombok.Getter;

/**
 * The {@code ErrorMessage} enum represents different error messages used in the application.
 * Each error message has a corresponding description.
 */
@Getter
public enum ErrorMessage {
    CONFIG_FILE_NOT_FOUND("The configuration file is not available at the path supplied"),
    CONFIG_FILE_FORMAT("The configuration file has wrong syntax"),
    NO_SUCH_NODE("Can't send a message to a non existing node"),
    SOCKET_SENDING_ERROR("Error while sending message"),
    KEY_PAIR_LOAD_ERROR("Error while loading key pair"),
    SIGNATURE_ERROR("Error while signing message"),
    INVALID_SIGNATURE_ERROR("Invalid signature"),
    CANNOT_OPEN_SOCKET("Error while opening socket"),
    PUBLIC_KEY_LOAD_ERROR("Error while loading public key"),
    PRIVATE_KEY_LOAD_ERROR("Error while loading private key"),
    READING_SCRIPT_ERROR("Error while reading script");

    private final String message;

    ErrorMessage(String message) {
        this.message = message;
    }
}
