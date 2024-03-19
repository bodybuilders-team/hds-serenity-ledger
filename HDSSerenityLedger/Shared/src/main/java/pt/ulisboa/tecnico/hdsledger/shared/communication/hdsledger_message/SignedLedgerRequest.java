package pt.ulisboa.tecnico.hdsledger.shared.communication.hdsledger_message;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import pt.ulisboa.tecnico.hdsledger.shared.communication.Message;
import pt.ulisboa.tecnico.hdsledger.shared.config.ClientProcessConfig;

@Getter
@SuperBuilder
@AllArgsConstructor
@ToString(callSuper = true)
public class SignedLedgerRequest extends Message {
    @Setter
    private LedgerRequest ledgerRequest;

    @Setter
    @ToString.Exclude
    private byte[] signature;

    public boolean verifySignature(ClientProcessConfig[] clientsConfig) {
        switch (this.ledgerRequest) {
            case LedgerTransferRequest ledgerTransferRequest -> {
                return ledgerTransferRequest.verifySignature(this.signature, clientsConfig);
            }
            case LedgerCheckBalanceRequest ledgerCheckBalanceRequest -> {
                return ledgerCheckBalanceRequest.verifySignature(this.signature, clientsConfig);
            }
            default -> throw new IllegalStateException("Unexpected value: " + this.ledgerRequest);
        }
    }
}
