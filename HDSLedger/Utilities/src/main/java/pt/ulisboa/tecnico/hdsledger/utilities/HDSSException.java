package pt.ulisboa.tecnico.hdsledger.utilities;

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
