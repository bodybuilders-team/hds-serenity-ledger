package pt.ulisboa.tecnico.hdsledger.shared.communication.hdsledger_message;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import pt.ulisboa.tecnico.hdsledger.shared.communication.Message;
import pt.ulisboa.tecnico.hdsledger.shared.config.ClientProcessConfig;

@SuperBuilder
public class SignedLedgerRequest extends Message {
    @Getter
    @Setter
    private LedgerRequest ledgerRequest;
    @Getter
    @Setter
    private byte[] signature;

    public SignedLedgerRequest(String senderId, Type type) {
        super(senderId, type);
    }

    public boolean verifySignature(ClientProcessConfig[] clientsConfig){
        switch (this.ledgerRequest){
            case LedgerTransferRequest ledgerTransferRequest -> {
                return ledgerTransferRequest.verifySignature(this.signature, clientsConfig);
            }
            case LedgerCheckBalanceRequest ledgerCheckBalanceRequest -> {
                return ledgerCheckBalanceRequest.verifySignature(this.signature, clientsConfig);
            }
        }
    }
}
