package pt.ulisboa.tecnico.hdsledger.utilities;

/**
 * The {@code HDSSException} class represents an exception specific to the HDSS application.
 * It extends the {@link RuntimeException} class.
 */
public class HDSSException extends RuntimeException {

    private final ErrorMessage errorMessage;

    public HDSSException(ErrorMessage message) {
        errorMessage = message;
    }

    @Override
    public String getMessage() {
        return errorMessage.getMessage();
    }
}
