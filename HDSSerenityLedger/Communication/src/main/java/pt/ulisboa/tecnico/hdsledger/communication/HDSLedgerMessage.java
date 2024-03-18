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
                return MessageFormat.format("APPEND(\"{0}\")", this.getValue());
            }
            case APPEND_RESPONSE -> {
                return MessageFormat.format("APPEND_RESPONSE(\"{0}\")", this.getValue());
            }
            case READ -> {
                return "READ";
            }
            case READ_RESPONSE -> {
                return MessageFormat.format("READ_RESPONSE(\"{0}\")", this.getValue());
            }
            default -> {
                return "NO REPRESENTATION";
            }
        }
    }
}
