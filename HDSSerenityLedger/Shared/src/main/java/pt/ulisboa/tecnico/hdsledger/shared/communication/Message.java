package pt.ulisboa.tecnico.hdsledger.shared.communication;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;

@SuperBuilder
@ToString
public class Message implements Serializable {

    // Sender identifier
    @Getter
    @Setter
    private String senderId;
    // Message identifier
    @Getter
    @Setter
    private int messageId;
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

//    @Override
//    public String toString() {
//        if (this.getType() == Type.ACK) {
//            return MessageFormat.format("ACK({0})", this.getMessageId());
//        } else if (this.getType() == Type.IGNORE) {
//            return MessageFormat.format("IGNORE({0})", this.getMessageId());
//        } else return "NO REPRESENTATION";
//    }

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

        public static List<Type> clientLibraryTypes() {
            return Arrays.asList(BALANCE, BALANCE_RESPONSE, TRANSFER, TRANSFER_RESPONSE);
        }
    }
}
