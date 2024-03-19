package pt.ulisboa.tecnico.hdsledger.shared.communication.hdsledger_message.dtos;

import pt.ulisboa.tecnico.hdsledger.shared.SerializationUtils;
import pt.ulisboa.tecnico.hdsledger.shared.communication.hdsledger_message.LedgerCheckBalanceRequest;
import pt.ulisboa.tecnico.hdsledger.shared.communication.hdsledger_message.LedgerTransferRequest;
import pt.ulisboa.tecnico.hdsledger.shared.communication.hdsledger_message.SignedLedgerRequest;

public class SignedLedgerRequestDtoConverter {

    public static SignedLedgerRequestDto convert(SignedLedgerRequest signedLedgerRequest) {
        return SignedLedgerRequestDto.builder()
                .senderId(signedLedgerRequest.getSenderId())
                .type(signedLedgerRequest.getType())
                .ledgerRequest(SerializationUtils.serialize(signedLedgerRequest.getLedgerRequest()))
                .signature(signedLedgerRequest.getSignature())
                .build();
    }

    public static SignedLedgerRequest convert(SignedLedgerRequestDto signedLedgerRequestDto) {
        final var value = switch (signedLedgerRequestDto.getType()) {
            case TRANSFER ->
                    SerializationUtils.deserialize(signedLedgerRequestDto.getLedgerRequest(), LedgerTransferRequest.class);
            case BALANCE ->
                    SerializationUtils.deserialize(signedLedgerRequestDto.getLedgerRequest(), LedgerCheckBalanceRequest.class);
            default ->
                    throw new IllegalArgumentException(String.format("Invalid ledger message type %s", signedLedgerRequestDto.getType()));
        };

        return SignedLedgerRequest.builder()
                .senderId(signedLedgerRequestDto.getSenderId())
                .type(signedLedgerRequestDto.getType())
                .ledgerRequest(value)
                .signature(signedLedgerRequestDto.getSignature())
                .build();
    }
}
