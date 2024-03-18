package pt.ulisboa.tecnico.hdsledger.shared.communication.consensus_message;

import pt.ulisboa.tecnico.hdsledger.shared.SerializationUtils;
import pt.ulisboa.tecnico.hdsledger.shared.communication.Message.Type;

public class ConsensusMessageDtoConverter {

    public static ConsensusMessageDto convertToDto(ConsensusMessage consensusMessage) {
        final var consensusMessageDto = new ConsensusMessageDto(consensusMessage.getSenderId(), consensusMessage.getType());

        final var preparedValue = switch (consensusMessage.getType()) {
            case Type.PRE_PREPARE, Type.PREPARE, Type.COMMIT -> SerializationUtils.serialize(consensusMessage.getValue());
            case Type.ROUND_CHANGE -> null;
            default -> throw new IllegalArgumentException("Invalid consensus message type");
        };

        final var value = switch (consensusMessage.getType()) {
            case Type.PRE_PREPARE, Type.PREPARE, Type.COMMIT -> SerializationUtils.serialize(consensusMessage.getValue());
            case Type.ROUND_CHANGE -> null;
            default -> throw new IllegalArgumentException("Invalid consensus message type");
        };


        consensusMessageDto.setConsensusInstance(consensusMessage.getConsensusInstance());
        consensusMessageDto.setRound(consensusMessage.getRound());
        consensusMessageDto.setPreparedRound(consensusMessage.getPreparedRound());
        consensusMessageDto.setPreparedValue(preparedValue);
        consensusMessageDto.setReplyTo(consensusMessage.getReplyTo());
        consensusMessageDto.setReplyToMessageId(consensusMessage.getReplyToMessageId());
        consensusMessageDto.setValue(value);

        return consensusMessageDto;
    }




}
