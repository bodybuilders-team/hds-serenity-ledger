package pt.ulisboa.tecnico.hdsledger.shared.communication.hdsledger_message;

import pt.ulisboa.tecnico.hdsledger.shared.SerializationUtils;

public class LedgerMessageDtoConverter {

    public static LedgerMessageDto convert(LedgerMessage ledgerMessage) {
        return LedgerMessageDto.builder()
                .senderId(ledgerMessage.getSenderId())
                .type(ledgerMessage.getType())
                .value(SerializationUtils.serialize(ledgerMessage.getValue()))
                .signature(ledgerMessage.getSignature())
                .build();
    }

    public static LedgerMessage convert(LedgerMessageDto ledgerMessageDto) {
        final var value = switch (ledgerMessageDto.getType()) {
            case TRANSFER -> SerializationUtils.deserialize(ledgerMessageDto.getValue(), LedgerTransferMessage.class);
            case BALANCE ->
                    SerializationUtils.deserialize(ledgerMessageDto.getValue(), LedgerCheckBalanceMessage.class);
            case BALANCE_RESPONSE, TRANSFER_RESPONSE -> ledgerMessageDto.getValue();
            default ->
                    throw new IllegalArgumentException(String.format("Invalid ledger message type %s", ledgerMessageDto.getType()));
        };

        return LedgerMessage.builder()
                .senderId(ledgerMessageDto.getSenderId())
                .type(ledgerMessageDto.getType())
                .value(value)
                .signature(ledgerMessageDto.getSignature())
                .build();
    }
}
