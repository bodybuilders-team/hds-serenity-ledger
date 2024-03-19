package pt.ulisboa.tecnico.hdsledger.shared.communication;

import lombok.Getter;

/**
 * The {@code SignedPacket} class represents a packet that contains data and it's signature.
 */
public class SignedPacket {
    @Getter
    private final byte[] data;
    @Getter
    private final byte[] signature;

    public SignedPacket(byte[] data, byte[] signature) {
        this.data = data;
        this.signature = signature;
    }
}
