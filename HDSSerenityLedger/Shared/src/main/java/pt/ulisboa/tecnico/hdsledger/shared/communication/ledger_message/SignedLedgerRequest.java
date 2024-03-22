package pt.ulisboa.tecnico.hdsledger.shared.communication.ledger_message;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import pt.ulisboa.tecnico.hdsledger.shared.communication.Message;
import pt.ulisboa.tecnico.hdsledger.shared.config.ClientProcessConfig;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Objects;

/**
 * The {@code SignedLedgerRequest} class represents a signed request that clients submit to the ledger.
 */
@Setter
@Getter
@SuperBuilder
public class SignedLedgerRequest extends Message {
    private LedgerRequest ledgerRequest;

    @ToString.Exclude
    private byte[] signature;

    public SignedLedgerRequest(String senderId, Type type) {
        super(senderId, type);
    }

    /**
     * Verifies the signature of the request.
     *
     * @param clientsConfig the clients configuration
     * @return true if the signature is valid, false otherwise
     */
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

    @Override
    public String toString() {
        switch (this.getType()) {
            case Type.TRANSFER -> {
                LedgerTransferRequest ledgerTransferRequest = (LedgerTransferRequest) this.getLedgerRequest();

                return MessageFormat.format("<{0}(\u001B[33m{1} HDC\u001B[37m, {2}, {3}), requestId={4}, messageId={5}>",
                        this.getType(),
                        ledgerTransferRequest.getAmount(),
                        ledgerTransferRequest.getSourceAccountId(),
                        ledgerTransferRequest.getDestinationAccountId(),
                        ledgerTransferRequest.getRequestId(),
                        this.getMessageId()
                );
            }
            case Type.BALANCE -> {
                LedgerCheckBalanceRequest ledgerCheckBalanceRequest = (LedgerCheckBalanceRequest) this.getLedgerRequest();

                return MessageFormat.format("<{0}({1}), requesterId={2}, requestId={3}, messageId={4}>",
                        this.getType(),
                        ledgerCheckBalanceRequest.getAccountId(),
                        ledgerCheckBalanceRequest.getRequesterId(),
                        ledgerCheckBalanceRequest.getRequestId(),
                        this.getMessageId()
                );
            }
            default -> {
                return "NO REPRESENTATION";
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        SignedLedgerRequest that = (SignedLedgerRequest) o;
        return Objects.equals(ledgerRequest, that.ledgerRequest) && Arrays.equals(signature, that.signature);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(super.hashCode(), ledgerRequest);
        result = 31 * result + Arrays.hashCode(signature);
        return result;
    }
}
