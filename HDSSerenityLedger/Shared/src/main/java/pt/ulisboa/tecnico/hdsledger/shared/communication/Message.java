package pt.ulisboa.tecnico.hdsledger.shared.communication;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import pt.ulisboa.tecnico.hdsledger.shared.SerializationUtils;
import pt.ulisboa.tecnico.hdsledger.shared.communication.consensus_message.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.shared.communication.hdsledger_message.LedgerResponse;
import pt.ulisboa.tecnico.hdsledger.shared.communication.hdsledger_message.SignedLedgerRequest;

import java.io.Serializable;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;

@SuperBuilder
public class Message implements Serializable {

    // Sender identifier
    @Getter
    @Setter
    private String senderId;
    // Message identifier
    @Getter
    @Setter
    private int messageId = -1;
    // Message type
    @Getter
    @Setter
    private Type type;

    public Message() {

    }

    public Message(String senderId, Type type) {
        this.senderId = senderId;
        this.type = type;
    }

    /**
     * Returns a deep copy of this Message object.
     * <p>
     * Useful when broadcasting almost identical messages but that will be slightly changed.
     * e.g. messageId attributed during AuthenticatedPerfectLink.send() method.
     *
     * @return the copy Message object
     */
    public Message deepCopy() {
        return SerializationUtils.deserialize(SerializationUtils.serialize(this), this.getClass());
    }

    @Override
    public String toString() {
        if (this.getType() == Type.ACK) {
            return MessageFormat.format("ACK({0})", this.getMessageId());
        } else if (this.getType() == Type.IGNORE) {
            return MessageFormat.format("IGNORE({0})", this.getMessageId());
        } else return "NO REPRESENTATION";
    }

    public enum Type {
        // Messages for consensus (node to node)
        PRE_PREPARE, PREPARE, COMMIT, ROUND_CHANGE,

        // Messages for the library (client to node)
        BALANCE, BALANCE_RESPONSE, TRANSFER, TRANSFER_RESPONSE,

        // Others
        ACK, IGNORE;

        public static List<Type> consensusTypes() {
            return Arrays.asList(PRE_PREPARE, PREPARE, COMMIT, ROUND_CHANGE);
        }

        public static List<Type> clientRequestTypes() {
            return Arrays.asList(BALANCE, TRANSFER);
        }

        public static List<Type> clientResponseTypes() {
            return Arrays.asList(BALANCE_RESPONSE, TRANSFER_RESPONSE);
        }

        @Override
        public String toString() {
            return switch (this) {
                case PRE_PREPARE -> "PRE-PREPARE";
                case ROUND_CHANGE -> "ROUND-CHANGE";
                case BALANCE_RESPONSE -> "BALANCE-RESPONSE";
                case TRANSFER_RESPONSE -> "TRANSFER-RESPONSE";
                default -> super.toString();
            };
        }

        public Class<? extends Message> getClassType() {
            final Class<? extends Message> clazz;

            if (consensusTypes().contains(this))
                clazz = ConsensusMessage.class;
            else if (clientRequestTypes().contains(this))
                clazz = SignedLedgerRequest.class;
            else if (clientResponseTypes().contains(this))
                clazz = LedgerResponse.class;
            else if (this == ACK || this == IGNORE)
                clazz = Message.class;
            else
                throw new IllegalStateException("Unexpected value: " + this);

            return clazz;
        }
    }
}

