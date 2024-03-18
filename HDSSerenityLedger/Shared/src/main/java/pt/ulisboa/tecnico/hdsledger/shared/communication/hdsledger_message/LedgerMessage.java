package pt.ulisboa.tecnico.hdsledger.shared.communication.hdsledger_message;

import lombok.Getter;
import lombok.Setter;
import pt.ulisboa.tecnico.hdsledger.shared.SerializationUtils;
import pt.ulisboa.tecnico.hdsledger.shared.communication.Message;
import pt.ulisboa.tecnico.hdsledger.shared.config.ClientProcessConfig;
import pt.ulisboa.tecnico.hdsledger.shared.crypto.CryptoUtils;
import pt.ulisboa.tecnico.hdsledger.shared.models.LedgerMessageValue;

import java.text.MessageFormat;
import java.util.Arrays;

public class LedgerMessage extends Message {
    @Getter
    @Setter
    private LedgerMessageValue value;
    @Getter
    @Setter
    private byte[] signature;

    public LedgerMessage(String senderId, Type type) {
        super(senderId, type);
    }

    public String getHDSLedgerMessageRepresentation() {
        switch (this.getType()) {
            case Type.REGISTER -> {
                return "\u001B[REGISTER\u001B[37m";
            }
            case Type.REGISTER_RESPONSE -> {
                return MessageFormat.format("\u001B[32mREGISTER_RESPONSE\u001B[37m(\u001B[33m\"{0}\"\u001B[37m)", this.getValue());
            }
            case Type.BALANCE -> {
                return MessageFormat.format("\u001B[32mBALANCE\u001B[37m(\u001B[33m\"{0}\"\u001B[37m)", this.getValue());
            }
            case Type.BALANCE_RESPONSE -> {
                return MessageFormat.format("\u001B[32mBALANCE_RESPONSE\u001B[37m(\u001B[33m\"{0}\"\u001B[37m)", this.getValue());
            }
            case Type.TRANSFER -> {
                return "\u001B[32mTRANSFER\u001B[37m";
            }
            case Type.TRANSFER_RESPONSE -> {
                return MessageFormat.format("\u001B[32mTRANSFER_RESPONSE\u001B[37m(\u001B[33m\"{0}\"\u001B[37m)", this.getValue());
            }
            default -> {
                return "NO REPRESENTATION";
            }
        }
    }

}
