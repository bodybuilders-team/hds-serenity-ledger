package pt.ulisboa.tecnico.hdsledger.communication.consensus_message;

import com.google.gson.Gson;
import pt.ulisboa.tecnico.hdsledger.communication.Message;

import java.text.MessageFormat;

public class ConsensusMessage extends Message {

    // Consensus instance
    private int consensusInstance;
    // Round
    private int round;
    // Prepared round
    private int preparedRound;
    // Prepared value
    private String preparedValue;
    // Who sent the previous message
    private String replyTo;
    // Id of the previous message
    private int replyToMessageId;
    // Message (PREPREPARE, PREPARE, COMMIT)
    private String message;

    public ConsensusMessage(String senderId, Type type) {
        super(senderId, type);
    }

    public PrePrepareMessage deserializePrePrepareMessage() {
        return new Gson().fromJson(this.message, PrePrepareMessage.class);
    }

    public PrepareMessage deserializePrepareMessage() {
        return new Gson().fromJson(this.message, PrepareMessage.class);
    }

    public CommitMessage deserializeCommitMessage() {
        return new Gson().fromJson(this.message, CommitMessage.class);
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
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

    public String getPreparedValue() {
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
            case PRE_PREPARE -> {
                return MessageFormat.format("PRE-PREPARE({0}, {1}, \"{2}\")",
                        this.getConsensusInstance(), this.getRound(),
                        deserializePrepareMessage().getValue());
            }
            case PREPARE -> {
                return MessageFormat.format("PREPARE({0}, {1}, \"{2}\")",
                        this.getConsensusInstance(), this.getRound(),
                        deserializePrepareMessage().getValue());
            }
            case COMMIT -> {
                return MessageFormat.format("COMMIT({0}, {1}, \"{2}\")",
                        this.getConsensusInstance(), this.getRound(),
                        deserializeCommitMessage().getValue());
            }
            case ROUND_CHANGE -> {
                return MessageFormat.format("ROUND-CHANGE({0}, {1}, {2}, \"{3}\")",
                        this.getConsensusInstance(), this.getRound(), this.getPreparedRound(), this.getPreparedValue());
            }
            default -> {
                return "NO REPRESENTATION";
            }
        }
    }
}

