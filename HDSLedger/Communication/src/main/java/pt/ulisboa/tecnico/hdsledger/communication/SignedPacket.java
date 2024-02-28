package pt.ulisboa.tecnico.hdsledger.communication;

public class SignedPacket {
    private final byte[] message;
    private final byte[] signature;

    public SignedPacket(byte[] message, byte[] signature) {
        this.message = message;
        this.signature = signature;
    }

    public byte[] getMessage() {
        return message;
    }

    public byte[] getSignature() {
        return signature;
    }
}
