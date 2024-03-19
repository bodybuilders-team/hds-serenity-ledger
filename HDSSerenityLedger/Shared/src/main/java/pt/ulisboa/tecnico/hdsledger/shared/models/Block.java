package pt.ulisboa.tecnico.hdsledger.shared.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import pt.ulisboa.tecnico.hdsledger.shared.SerializationUtils;
import pt.ulisboa.tecnico.hdsledger.shared.communication.hdsledger_message.SignedLedgerRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A block in the blockchain.
 */
@Getter
@Builder
@AllArgsConstructor
public class Block {

    @Setter
    private List<SignedLedgerRequest> requests = new ArrayList<>();
    @Setter
    private int consensusInstance;

    public Block() {
    }

    public static Block fromJson(String json) {
        return SerializationUtils.getGson().fromJson(json, Block.class);
    }

    public void addRequest(SignedLedgerRequest request) {
        requests.add(request);
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
