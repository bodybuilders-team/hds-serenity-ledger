package pt.ulisboa.tecnico.hdsledger.shared.communication.hdsledger_message;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import pt.ulisboa.tecnico.hdsledger.shared.config.ClientProcessConfig;
import pt.ulisboa.tecnico.hdsledger.shared.crypto.CryptoUtils;

@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class LedgerCheckBalanceRequest extends LedgerRequest {
    @Getter
    private final String accountId;

    public boolean verifySignature(byte[] signature, ClientProcessConfig[] clientsConfig) {
        return CryptoUtils.verifySignature(this, accountId, signature, clientsConfig);
    }
}
