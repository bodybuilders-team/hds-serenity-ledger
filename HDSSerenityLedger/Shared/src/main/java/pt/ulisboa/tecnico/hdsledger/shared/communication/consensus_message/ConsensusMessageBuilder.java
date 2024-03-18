package pt.ulisboa.tecnico.hdsledger.shared.communication.consensus_message;

import pt.ulisboa.tecnico.hdsledger.shared.communication.Message;

public class ConsensusMessageBuilder {
    private final ConsensusMessageDto instance;

    public ConsensusMessageBuilder(String sender, Message.Type type) {
        instance = new ConsensusMessageDto(sender, type);
    }

    public ConsensusMessageBuilder setMessage(String message) {
        instance.setValue(message);
        return this;
    }

    public ConsensusMessageBuilder setConsensusInstance(int consensusInstance) {
        instance.setConsensusInstance(consensusInstance);
        return this;
    }

    public ConsensusMessageBuilder setRound(int round) {
        instance.setRound(round);
        return this;
    }

    public ConsensusMessageBuilder setPreparedRound(int preparedRound) {
        instance.setPreparedRound(preparedRound);
        return this;
    }

    public ConsensusMessageBuilder setPreparedValue(String preparedValue) {
        instance.setPreparedValue(preparedValue);
        return this;
    }

    public ConsensusMessageBuilder setReplyTo(String replyTo) {
        instance.setReplyTo(replyTo);
        return this;
    }

    public ConsensusMessageBuilder setReplyToMessageId(int replyToMessageId) {
        instance.setReplyToMessageId(replyToMessageId);
        return this;
    }

    public ConsensusMessageDto build() {
        return instance;
    }
}

