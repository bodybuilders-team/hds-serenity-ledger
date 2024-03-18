package pt.ulisboa.tecnico.hdsledger.shared.communication.consensus_message;

import pt.ulisboa.tecnico.hdsledger.shared.communication.Message;
import pt.ulisboa.tecnico.hdsledger.shared.models.Block;

import java.text.MessageFormat;

public class ConsensusMessage extends Message {

    // Consensus instance
    private int consensusInstance;
    // Round
    private int round;
    // Prepared round
    private int preparedRound;
    // Prepared value
    private Object preparedValue;
    // Who sent the previous message
    private String replyTo;
    // Id of the previous message
    private int replyToMessageId;
    // Value
    private Block value;

    public ConsensusMessage(String senderId, Type type) {
        super(senderId, type);
    }

    public Block getValue() {
        return value;
    }

    public void setValue(Block value) {
        this.value = value;
    }

    public int getConsensusInstance() {
        return consensusInstance;
    }

    public void setConsensusInstance(int consensusInstance) {
        this.consensusInstance = consensusInstance;
    }

    public int getRound() {
        return round;
    }

    public void setRound(int round) {
        this.round = round;
    }

    public int getPreparedRound() {
        return preparedRound;
    }

    public void setPreparedRound(int preparedRound) {
        this.preparedRound = preparedRound;
    }

    public Object getPreparedValue() {
        return preparedValue;
    }

    public void setPreparedValue(String preparedValue) {
        this.preparedValue = preparedValue;
    }

    public String getReplyTo() {
        return replyTo;
    }

    public void setReplyTo(String replyTo) {
        this.replyTo = replyTo;
    }

    public int getReplyToMessageId() {
        return replyToMessageId;
    }

    public void setReplyToMessageId(int replyToMessageId) {
        this.replyToMessageId = replyToMessageId;
    }

    public String getConsensusMessageRepresentation() {
        switch (this.getType()) {
            case Type.PRE_PREPARE -> {
                return MessageFormat.format("PRE-PREPARE({0}, {1}, \"{2}\")",
                        this.getConsensusInstance(), this.getRound(),
                        this.getValue());
            }
            case Type.ROUND_CHANGE -> {
                return MessageFormat.format("ROUND-CHANGE({0}, {1}, {2}, \"{3}\")",
                        this.getConsensusInstance(), this.getRound(), this.getPreparedRound(), this.getPreparedValue());
            }
            default -> {
                return "NO REPRESENTATION";
            }
        }
    }
}

