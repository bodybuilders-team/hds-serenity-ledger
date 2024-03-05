package pt.ulisboa.tecnico.hdsledger.communication;

public class HDSLedgerMessage extends Message {
    private String value;

    public HDSLedgerMessage(String senderId, Type type) {
        super(senderId, type);
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
