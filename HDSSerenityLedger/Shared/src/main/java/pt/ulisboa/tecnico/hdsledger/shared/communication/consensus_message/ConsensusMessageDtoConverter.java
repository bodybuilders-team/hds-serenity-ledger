package pt.ulisboa.tecnico.hdsledger.shared.communication.consensus_message;

import pt.ulisboa.tecnico.hdsledger.shared.SerializationUtils;
import pt.ulisboa.tecnico.hdsledger.shared.communication.Message.Type;
import pt.ulisboa.tecnico.hdsledger.shared.models.Block;

public class ConsensusMessageDtoConverter {

    public static ConsensusMessageDto convert(ConsensusMessage consensusMessage) {

        final var preparedValue = switch (consensusMessage.getType()) {
            case Type.PRE_PREPARE, Type.PREPARE, Type.COMMIT ->
                    SerializationUtils.serialize(consensusMessage.getValue());
            case Type.ROUND_CHANGE -> null;
            default -> throw new IllegalArgumentException("Invalid consensus message type");
        };

        final var value = switch (consensusMessage.getType()) {
            case Type.PRE_PREPARE, Type.PREPARE, Type.COMMIT ->
                    SerializationUtils.serialize(consensusMessage.getValue());
            case Type.ROUND_CHANGE -> null;
            default -> throw new IllegalArgumentException("Invalid consensus message type");
        };

        return ConsensusMessageDto.builder()
                .senderId(consensusMessage.getSenderId())
                .type(consensusMessage.getType())
                .consensusInstance(consensusMessage.getConsensusInstance())
                .round(consensusMessage.getRound())
                .preparedRound(consensusMessage.getPreparedRound())
                .preparedValue(preparedValue)
                .replyTo(consensusMessage.getReplyTo())
                .replyToMessageId(consensusMessage.getReplyToMessageId())
                .value(value)
                .build();
    }

    public static ConsensusMessage convert(ConsensusMessageDto consensusMessageDto) {
        final Object preparedValue = switch (consensusMessageDto.getType()) {
            case Type.PRE_PREPARE, Type.PREPARE, Type.COMMIT ->
                    SerializationUtils.deserialize(consensusMessageDto.getValue(), Block.class);
            case Type.ROUND_CHANGE -> null;
            default -> throw new IllegalArgumentException("Invalid consensus message type");
        };

        final Object value = switch (consensusMessageDto.getType()) {
            case Type.PRE_PREPARE, Type.PREPARE, Type.COMMIT ->
                    SerializationUtils.deserialize(consensusMessageDto.getValue(), Block.class);
            case Type.ROUND_CHANGE -> null;
            default -> throw new IllegalArgumentException("Invalid consensus message type");
        };

        return ConsensusMessage.builder()
                .senderId(consensusMessageDto.getSenderId())
                .type(consensusMessageDto.getType())
                .consensusInstance(consensusMessageDto.getConsensusInstance())
                .round(consensusMessageDto.getRound())
                .preparedRound(consensusMessageDto.getPreparedRound())
                .preparedValue(preparedValue)
                .replyTo(consensusMessageDto.getReplyTo())
                .replyToMessageId(consensusMessageDto.getReplyToMessageId())
                .value(value)
                .build();
    }


}
