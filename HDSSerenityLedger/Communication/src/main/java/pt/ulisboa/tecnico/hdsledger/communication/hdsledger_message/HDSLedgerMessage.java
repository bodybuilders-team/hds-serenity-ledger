package pt.ulisboa.tecnico.hdsledger.communication.hdsledger_message;

import com.google.gson.Gson;
import pt.ulisboa.tecnico.hdsledger.communication.Message;

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
            case REGISTER -> {
                return "\u001B[REGISTER\u001B[37m";
            }
            case REGISTER_RESPONSE -> {
                return MessageFormat.format("\u001B[32mREGISTER_RESPONSE\u001B[37m(\u001B[33m\"{0}\"\u001B[37m)", this.getValue());
            }
            case BALANCE -> {
                return MessageFormat.format("\u001B[32mBALANCE\u001B[37m(\u001B[33m\"{0}\"\u001B[37m)", this.getValue());
            }
            case BALANCE_RESPONSE -> {
                return MessageFormat.format("\u001B[32mBALANCE_RESPONSE\u001B[37m(\u001B[33m\"{0}\"\u001B[37m)", this.getValue());
            }
            case TRANSFER -> {
                return "\u001B[32mTRANSFER\u001B[37m";
            }
            case TRANSFER_RESPONSE -> {
                return MessageFormat.format("\u001B[32mTRANSFER_RESPONSE\u001B[37m(\u001B[33m\"{0}\"\u001B[37m)", this.getValue());
            }
            default -> {
                return "NO REPRESENTATION";
            }
        }
    }

    public LedgerTransferMessage deserializeLedgerTransferMessage() {
        return new Gson().fromJson(this.getValue(), LedgerTransferMessage.class);
    }
}
