package pt.ulisboa.tecnico.hdsledger.shared.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import pt.ulisboa.tecnico.hdsledger.shared.communication.ledger_message.SignedLedgerRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A block in the blockchain.
 */
@Setter
@Getter
@Builder
@AllArgsConstructor
public class Block {

    private List<SignedLedgerRequest> requests = new ArrayList<>();
    private int consensusInstance;
    private String creatorId;

    public Block() {
        // Empty constructor for serialization
    }

    /**
     * Adds a request to the block.
     *
     * @param request the request to add
     */
    public void addRequest(SignedLedgerRequest request) {
        requests.add(request);
    }

    @Override
    public String toString() {
        return String.valueOf(this.hashCode());
    }

    @Override
    public boolean equals(java.lang.Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Block block = (Block) o;
        return consensusInstance == block.consensusInstance && Objects.equals(requests, block.requests);
    }

    @Override
    public int hashCode() {
        return Objects.hash(consensusInstance, requests);
    }
}
