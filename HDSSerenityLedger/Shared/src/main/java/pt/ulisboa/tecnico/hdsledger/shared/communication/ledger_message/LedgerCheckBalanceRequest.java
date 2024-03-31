package pt.ulisboa.tecnico.hdsledger.shared.communication.ledger_message;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import pt.ulisboa.tecnico.hdsledger.shared.config.ClientProcessConfig;
import pt.ulisboa.tecnico.hdsledger.shared.crypto.CryptoUtils;

/**
 * The {@code LedgerCheckBalanceRequest} class represents a request to check the balance of an account.
 */
@Getter
@AllArgsConstructor
@SuperBuilder
@ToString
public class LedgerCheckBalanceRequest extends LedgerRequest {

    private final String accountId;
    private final String requesterId;

    /**
     * Verifies the signature of the request.
     * A signature is valid if it was signed by the requester account.
     * <p>
     * Signature is verified not for safety issues, but to ensure non-repudiation.
     *
     * @param signature     the signature to verify
     * @param clientsConfig the clients configuration
     * @return {@code true} if the signature is valid, {@code false} otherwise
     */
    public boolean verifySignature(byte[] signature, ClientProcessConfig[] clientsConfig) {
        return CryptoUtils.verifySignature(this, this.getRequesterId(), signature, clientsConfig);
    }
}
