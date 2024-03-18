package pt.ulisboa.tecnico.hdsledger.shared.communication.hdsledger_message;

import lombok.Getter;
import pt.ulisboa.tecnico.hdsledger.shared.SerializationUtils;
import pt.ulisboa.tecnico.hdsledger.shared.config.ClientProcessConfig;
import pt.ulisboa.tecnico.hdsledger.shared.crypto.CryptoUtils;

import java.util.Arrays;

@Getter
public class LedgerTransferMessage {
    private final String sourceAccountId;
    private final String destinationAccountId;
    private final int amount;

    public LedgerTransferMessage(String sourceAccountId, String destinationAccountId, int amount) {
        this.sourceAccountId = sourceAccountId;
        this.destinationAccountId = destinationAccountId;
        this.amount = amount;
    }

    public static boolean verifySignature(LedgerMessage message, ClientProcessConfig[] clientsConfig) {
        final var transferMessage = (LedgerTransferMessage) message.getValue();
        final var clientConfig = Arrays.stream(clientsConfig).filter(c -> c.getId().equals(transferMessage.getSourceAccountId())).findAny().orElse(null);
        if (clientConfig == null)
            return false;

        var publicKey = CryptoUtils.getPublicKey(clientConfig.getPublicKeyPath());
        final var serializedTransferMessage = SerializationUtils.serialize(transferMessage);

        return CryptoUtils.verify(serializedTransferMessage.getBytes(), message.getSignature(), publicKey);
    }
}
