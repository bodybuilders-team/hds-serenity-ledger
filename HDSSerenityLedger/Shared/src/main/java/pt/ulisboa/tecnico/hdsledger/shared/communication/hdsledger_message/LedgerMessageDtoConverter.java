package pt.ulisboa.tecnico.hdsledger.shared.communication.hdsledger_message;

import pt.ulisboa.tecnico.hdsledger.shared.SerializationUtils;

public class LedgerMessageDtoConverter {

    public static LedgerRequestDto convert(SignedLedgerRequest signedLedgerRequest) {
        return LedgerRequestDto.builder()
                .senderId(signedLedgerRequest.getSenderId())
                .type(signedLedgerRequest.getType())
                .value(SerializationUtils.serialize(signedLedgerRequest.getValue()))
                .signature(signedLedgerRequest.getSignature())
                .build();
    }

    public static SignedLedgerRequest convert(LedgerRequestDto ledgerRequestDto) {
        final var value = switch (ledgerRequestDto.getType()) {
            case TRANSFER -> SerializationUtils.deserialize(ledgerRequestDto.getValue(), LedgerTransferRequest.class);
            case BALANCE ->
                    SerializationUtils.deserialize(ledgerRequestDto.getValue(), LedgerCheckBalanceRequest.class);
            case BALANCE_RESPONSE, TRANSFER_RESPONSE -> ledgerRequestDto.getValue();
            default ->
                    throw new IllegalArgumentException(String.format("Invalid ledger message type %s", ledgerRequestDto.getType()));
        };

        return SignedLedgerRequest.builder()
                .senderId(ledgerRequestDto.getSenderId())
                .type(ledgerRequestDto.getType())
                .value(value)
                .signature(ledgerRequestDto.getSignature())
                .build();
    }
}
