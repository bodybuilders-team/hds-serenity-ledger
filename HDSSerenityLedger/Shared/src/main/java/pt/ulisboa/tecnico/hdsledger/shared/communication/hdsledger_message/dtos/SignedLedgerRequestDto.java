package pt.ulisboa.tecnico.hdsledger.shared.communication.hdsledger_message.dtos;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import pt.ulisboa.tecnico.hdsledger.shared.communication.Message;

@Getter
@SuperBuilder
public class SignedLedgerRequestDto extends Message {
    @Setter
    private String ledgerRequest;
    @Setter
    private byte[] signature;

    public SignedLedgerRequestDto(String ledgerRequest, byte[] signature) {
        this.ledgerRequest = ledgerRequest;
        this.signature = signature;
    }
}
