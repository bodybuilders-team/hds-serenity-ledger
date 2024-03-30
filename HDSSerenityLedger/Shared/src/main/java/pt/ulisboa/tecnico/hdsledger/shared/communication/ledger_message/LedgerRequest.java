package pt.ulisboa.tecnico.hdsledger.shared.communication.ledger_message;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.Objects;

/**
 * The {@code LedgerRequest} class represents a request that clients submit to the ledger.
 */
@Setter
@Getter
@SuperBuilder
@ToString(callSuper = true)
public abstract class LedgerRequest {

    private long requestId;

    public LedgerRequest() {
        // Empty constructor for serialization
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LedgerRequest that = (LedgerRequest) o;
        return requestId == that.requestId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(requestId);
    }
}
