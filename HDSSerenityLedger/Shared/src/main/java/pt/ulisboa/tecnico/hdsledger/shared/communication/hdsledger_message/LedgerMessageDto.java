package pt.ulisboa.tecnico.hdsledger.shared.communication.hdsledger_message;

import pt.ulisboa.tecnico.hdsledger.shared.SerializationUtils;
import pt.ulisboa.tecnico.hdsledger.shared.communication.Message;
import pt.ulisboa.tecnico.hdsledger.shared.config.ClientProcessConfig;
import pt.ulisboa.tecnico.hdsledger.shared.crypto.CryptoUtils;

import java.text.MessageFormat;
import java.util.Arrays;

public class LedgerMessageDto extends Message {

    private String value;
    private byte[] signature;

    public LedgerMessageDto(String senderId, Type type) {
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

    public byte[] getSignature() {
        return signature;
    }

    public void setSignature(byte[] signature) {
        this.signature = signature;
    }

    public boolean verifySignature(ClientProcessConfig[] clientsConfig) {
        var transferMessage = SerializationUtils.deserialize(this.getValue(), LedgerTransferMessage.class);

        var clientConfig = Arrays.stream(clientsConfig).filter(c -> c.getId().equals(transferMessage.getSourceAccountId())).findAny().orElse(null);
        if (clientConfig == null)
            return false;

        var publicKey = CryptoUtils.getPublicKey(clientConfig.getPublicKeyPath());

        return CryptoUtils.verify(this.getValue().getBytes(), this.getSignature(), publicKey);
    }
}
