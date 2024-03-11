package pt.ulisboa.tecnico.hdsledger.communication.hdsledger_message;

public class LedgerTransferMessage {
    private String sourceAccountId;
    private String destinationAccountId;
    private int amount;

    public LedgerTransferMessage(String sourceAccountId, String destinationAccountId, int amount) {
        this.sourceAccountId = sourceAccountId;
        this.destinationAccountId = destinationAccountId;
        this.amount = amount;
    }

    public String getSourceAccountId() {
        return sourceAccountId;
    }

    public String getDestinationAccountId() {
        return destinationAccountId;
    }

    public int getAmount() {
        return amount;
    }
}
