package pt.ulisboa.tecnico.hdsledger.shared.communication;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * The {@code SignedMessage} class represents a message that contains data and it's signature.
 * It is used to send messages within the network, ensuring authenticity and integrity.
 */
@Setter
@Getter
@AllArgsConstructor
@EqualsAndHashCode
public class SignedMessage {
    private Message message;
    private byte[] signature;
}
