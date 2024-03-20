package pt.ulisboa.tecnico.hdsledger.shared.communication.hdsledger_message;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import pt.ulisboa.tecnico.hdsledger.shared.communication.Message;
import pt.ulisboa.tecnico.hdsledger.shared.config.ClientProcessConfig;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Objects;

@Getter
@SuperBuilder
@ToString(callSuper = true)
public class SignedLedgerRequest extends Message {
    @Setter
    private LedgerRequest ledgerRequest;

    @Setter
    @ToString.Exclude
    private byte[] signature;

    public SignedLedgerRequest(String senderId, Type type) {
        super(senderId, type);
    }

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

                return MessageFormat.format("{0}({1}, \u001B[33m{2} HDS²\u001B[37m, {3}, {4})", this.getType(),
                        ledgerTransferRequest.getRequestId(),
                        ledgerTransferRequest.getAmount(),
                        ledgerTransferRequest.getSourceAccountId(),
                        ledgerTransferRequest.getDestinationAccountId()
                );
            }
            case Type.BALANCE -> {
                LedgerCheckBalanceRequest ledgerCheckBalanceRequest = (LedgerCheckBalanceRequest) this.getLedgerRequest();

                return MessageFormat.format("{0}({1}, {2})", this.getType(),
                        ledgerCheckBalanceRequest.getRequestId(),
                        ledgerCheckBalanceRequest.getAccountId());
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
        SignedLedgerRequest that = (SignedLedgerRequest) o;
        return Objects.equals(ledgerRequest, that.ledgerRequest) && Arrays.equals(signature, that.signature);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(ledgerRequest);
        result = 31 * result + Arrays.hashCode(signature);
        return result;
    }
}
