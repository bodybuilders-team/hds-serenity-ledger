package pt.ulisboa.tecnico.hdsledger.communication;

import java.text.MessageFormat;

public class HDSLedgerMessage extends Message {

    private String value;

    public HDSLedgerMessage(String senderId, Type type) {
        super(senderId, type);
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getHDSLedgerMessageRepresentation() {
        switch (this.getType()) {
            case APPEND -> {
                return MessageFormat.format("\u001B[32mAPPEND\u001B[37m(\u001B[33m\"{0}\"\u001B[37m)", this.getValue());
            }
            case APPEND_RESPONSE -> {
                return MessageFormat.format("\u001B[32mAPPEND_RESPONSE\u001B[37m(\u001B[33m\"{0}\"\u001B[37m)", this.getValue());
            }
            case READ -> {
                return "\u001B[32mREAD\u001B[37m";
            }
            case READ_RESPONSE -> {
                return MessageFormat.format("\u001B[32mREAD_RESPONSE\u001B[37m(\u001B[33m\"{0}\"\u001B[37m)", this.getValue());
            }
            default -> {
                return "NO REPRESENTATION";
            }
        }
    }
}
