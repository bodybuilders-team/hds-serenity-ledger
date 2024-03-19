package pt.ulisboa.tecnico.hdsledger.shared.communication.hdsledger_message;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.SuperBuilder;
import pt.ulisboa.tecnico.hdsledger.shared.SerializationUtils;
import pt.ulisboa.tecnico.hdsledger.shared.config.ClientProcessConfig;
import pt.ulisboa.tecnico.hdsledger.shared.crypto.CryptoUtils;

import java.util.Arrays;

@Getter
@AllArgsConstructor
@SuperBuilder
@ToString
public class LedgerTransferRequest extends LedgerRequest {
    private final String sourceAccountId;
    private final String destinationAccountId;
    private final int amount;

    public boolean verifySignature(byte[] signature, ClientProcessConfig[] clientsConfig) {
        final var clientConfig = Arrays.stream(clientsConfig).filter(c -> c.getId().equals(this.getSourceAccountId())).findAny().orElse(null);
        if (clientConfig == null)
            return false;

        var publicKey = CryptoUtils.getPublicKey(clientConfig.getPublicKeyPath());
        final var serializedTransferRequest = SerializationUtils.serialize(this);

        return CryptoUtils.verify(serializedTransferRequest.getBytes(), signature, publicKey);
    }
}
