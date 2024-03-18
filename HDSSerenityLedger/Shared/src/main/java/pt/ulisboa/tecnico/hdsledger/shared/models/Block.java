package pt.ulisboa.tecnico.hdsledger.shared.models;

import pt.ulisboa.tecnico.hdsledger.shared.communication.hdsledger_message.LedgerMessageDto;
import pt.ulisboa.tecnico.hdsledger.shared.SerializationUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * A block in the blockchain.
 */
public class Block implements ConsensusValue {

    private final List<LedgerMessageDto> requests = new ArrayList<>();
    private int consensusInstance;

    public Block() {
    }

    public static Block fromJson(String json) {
        return SerializationUtils.getGson().fromJson(json, Block.class);
    }

    public int getConsensusInstance() {
        return consensusInstance;
    }

    public void setConsensusInstance(int consensusInstance) {
        this.consensusInstance = consensusInstance;
    }

    public List<LedgerMessageDto> getRequests() {
        return requests;
    }

    public void addRequest(LedgerMessageDto request) {
        requests.add(request);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Block block = (Block) o;
        return consensusInstance == block.consensusInstance && Objects.equals(requests, block.requests);
    }

    @Override
    public int hashCode() {
        return Objects.hash(consensusInstance, requests);
    }

    public String toJson() {
        return SerializationUtils.getGson().toJson(this);
    }
}
