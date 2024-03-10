package pt.ulisboa.tecnico.hdsledger.communication;

import java.io.Serializable;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;

public class Message implements Serializable {

    // Sender identifier
    private String senderId;
    // Message identifier
    private int messageId;
    // Message type
    private Type type;

    public Message(String senderId, Type type) {
        this.senderId = senderId;
        this.type = type;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public int getMessageId() {
        return messageId;
    }

    public void setMessageId(int messageId) {
        this.messageId = messageId;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public String getMessageRepresentation() {
        if (Type.consensusTypes().contains(this.getType())) {
            return ((ConsensusMessage) this).getConsensusMessageRepresentation();
        } else if (Type.clientLibraryTypes().contains(this.getType())) {
            return ((HDSLedgerMessage) this).getHDSLedgerMessageRepresentation();
        } else if (this.getType() == Type.ACK) {
            return MessageFormat.format("\u001B[32mACK\u001B[37m(\u001B[34m{0}\u001B[37m)", this.getMessageId());
        }
        else if (this.getType() == Type.IGNORE) {
            return MessageFormat.format("\u001B[32mIGNORE\u001B[37m(\u001B[34m{0}\u001B[37m)", this.getMessageId());
        } else return "NO REPRESENTATION";
    }

    public enum Type {
        // Messages for consensus (node to node)
        PRE_PREPARE, PREPARE, COMMIT, ROUND_CHANGE,

        // Messages for the library (client to node)
        APPEND, APPEND_RESPONSE, READ, READ_RESPONSE,

        // Others
        ACK, IGNORE;

        public static List<Type> consensusTypes() {
            return Arrays.asList(PRE_PREPARE, PREPARE, COMMIT, ROUND_CHANGE);
        }

        public static List<Type> clientLibraryTypes() {
            return Arrays.asList(APPEND, APPEND_RESPONSE, READ, READ_RESPONSE);
        }
    }
}
