package pt.ulisboa.tecnico.hdsledger.shared.communication;

/**
 * The {@code SignedPacket} class represents a packet that contains data and it's signature.
 */
public class SignedPacket {
    private final byte[] data;
    private final byte[] signature;

    public SignedPacket(byte[] data, byte[] signature) {
        this.data = data;
        this.signature = signature;
    }

    public byte[] getData() {
        return data;
    }

    public byte[] getSignature() {
        return signature;
    }
}
