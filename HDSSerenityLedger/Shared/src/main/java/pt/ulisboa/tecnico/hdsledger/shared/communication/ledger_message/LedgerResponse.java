package pt.ulisboa.tecnico.hdsledger.shared.communication.ledger_message;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import pt.ulisboa.tecnico.hdsledger.shared.communication.Message;

import java.text.MessageFormat;
import java.util.Objects;

/**
 * The {@code LedgerResponse} class represents a response from the ledger.
 */
@Getter
@SuperBuilder
public class LedgerResponse extends Message {
    @Setter
    private long originalRequestId;

    @Setter
    private String originalRequestSenderId;

    private String message;

    @Override
    public String toString() {
        return switch (this.getType()) {
            case Type.TRANSFER_RESPONSE, Type.BALANCE_RESPONSE, Type.LEDGER_ACK ->
                    MessageFormat.format("<{0}({1}, \"{2}\"), messageId={3}>",
                            this.getType(),
                            this.getOriginalRequestId(),
                            this.getMessage(),
                            this.getMessageId()
                    );

            case Type.ACK -> MessageFormat.format("<{0}({1}), messageId={2}>",
                    this.getType(),
                    this.getMessage(),
                    this.getMessageId()
            );

            default -> "NO REPRESENTATION";
        };
    }

    // HashCode and Equals do not consider the sender id nor the message id.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LedgerResponse that = (LedgerResponse) o;
        return type == that.type && originalRequestId == that.originalRequestId && Objects.equals(originalRequestSenderId, that.originalRequestSenderId) && Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, originalRequestId, originalRequestSenderId, message);
    }
}
