package pt.ulisboa.tecnico.hdsledger.communication.hdsledger_message;

import pt.ulisboa.tecnico.hdsledger.communication.Message;

public class HDSLedgerMessageBuilder {
    private final HDSLedgerMessage instance;

    public HDSLedgerMessageBuilder(String sender, Message.Type type) {
        instance = new HDSLedgerMessage(sender, type);
    }

    public HDSLedgerMessageBuilder setValue(String value) {
        instance.setValue(value);
        return this;
    }

    public HDSLedgerMessage build() {
        return instance;
    }
}
