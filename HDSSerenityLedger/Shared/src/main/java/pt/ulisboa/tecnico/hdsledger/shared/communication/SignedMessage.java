package pt.ulisboa.tecnico.hdsledger.shared.communication;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import pt.ulisboa.tecnico.hdsledger.shared.SerializationUtils;

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

    @Override
    public String toString() {
        return "SignedMessage{" +
                "message=" + message +
                '}';
    }

    /**
     * Returns a deep copy of this SignedMessage object.
     * <p>
     * Useful when broadcasting almost identical messages but that will be slightly changed.
     * e.g. messageId attributed during AuthenticatedPerfectLink.send() method.
     *
     * @return the copy SignedMessage object
     */
    public SignedMessage deepCopy() {
        return SerializationUtils.deserialize(SerializationUtils.serialize(this), this.getClass());
    }
}
