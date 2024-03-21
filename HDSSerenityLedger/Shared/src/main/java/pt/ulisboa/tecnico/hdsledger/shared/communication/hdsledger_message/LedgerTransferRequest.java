package pt.ulisboa.tecnico.hdsledger.shared.communication.hdsledger_message;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import pt.ulisboa.tecnico.hdsledger.shared.config.ClientProcessConfig;
import pt.ulisboa.tecnico.hdsledger.shared.crypto.CryptoUtils;

@Getter
@AllArgsConstructor
@SuperBuilder
@ToString
public class LedgerTransferRequest extends LedgerRequest {
    private final String sourceAccountId;
    private final String destinationAccountId;
    private final double amount;
    private final double fee;

    public boolean verifySignature(byte[] signature, ClientProcessConfig[] clientsConfig) {
        return CryptoUtils.verifySignature(this, this.getSourceAccountId(), signature, clientsConfig);
    }
}
