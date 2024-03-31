package pt.ulisboa.tecnico.hdsledger.shared.communication.ledger_message;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import pt.ulisboa.tecnico.hdsledger.shared.config.ClientProcessConfig;
import pt.ulisboa.tecnico.hdsledger.shared.crypto.CryptoUtils;

/**
 * The {@code LedgerTransferRequest} class represents a request to transfer money between two accounts.
 */
@Getter
@Setter
@AllArgsConstructor
@SuperBuilder
@ToString
public class LedgerTransferRequest extends LedgerRequest {
    private String sourceAccountId;
    private String destinationAccountId;
    private double amount;

    /**
     * Verifies the signature of the request.
     * A signature is valid if it was signed by the source account.
     * <p>
     * Signature is verified for safety, since a transfer can only be made by the source account owner.
     *
     * @param signature     the signature to verify
     * @param clientsConfig the clients configuration
     * @return {@code true} if the signature is valid, {@code false} otherwise
     */
    public boolean verifySignature(byte[] signature, ClientProcessConfig[] clientsConfig) {
        return CryptoUtils.verifySignature(this, this.getSourceAccountId(), signature, clientsConfig);
    }
}
