package pt.ulisboa.tecnico.hdsledger.utilities;

/**
 * Enum to store error messages of the system.
 */
public enum ErrorMessage {
    ConfigFileNotFound("The configuration file is not available at the path supplied"),
    ConfigFileFormat("The configuration file has wrong syntax"),
    NoSuchNode("Can't send a message to a non existing node"),
    SocketSendingError("Error while sending message"),
    KeyPairLoadError("Error while loading key pair"),
    KeyStoreLoadError("Error while loading keystore"),
    SignatureError("Error while signing message"),
    InvalidSignatureError("Invalid signature"),
    CannotOpenSocket("Error while opening socket"),
    PublicKeyLoadError("Error while loading public key"),
    PrivateKeyLoadError("Error while loading private key");

    private final String message;

    ErrorMessage(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
