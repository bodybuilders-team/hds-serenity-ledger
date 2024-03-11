package pt.ulisboa.tecnico.hdsledger.service.models;

import com.google.gson.Gson;
import pt.ulisboa.tecnico.hdsledger.communication.hdsledger_message.LedgerTransferMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A block in the blockchain.
 */
public class Block {

    private int consensusInstance;
    private final List<LedgerTransferMessage> transactions = new ArrayList<>();

    public Block() {
    }

    public int getConsensusInstance() {
        return consensusInstance;
    }

    public List<LedgerTransferMessage> getTransactions() {
        return transactions;
    }

    public void setConsensusInstance(int consensusInstance) {
        this.consensusInstance = consensusInstance;
    }

    public void addTransaction(LedgerTransferMessage transaction) {
        transactions.add(transaction);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Block block = (Block) o;
        return consensusInstance == block.consensusInstance && Objects.equals(transactions, block.transactions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(consensusInstance, transactions);
    }

    public String toJson() {
        return new Gson().toJson(this);
    }

    public static Block fromJson(String json) {
        return new Gson().fromJson(json, Block.class);
    }
}
