package pt.ulisboa.tecnico.hdsledger.shared.communication.hdsledger_message;

import pt.ulisboa.tecnico.hdsledger.shared.communication.Message;

public class HDSLedgerMessageBuilder {
    private final LedgerMessageDto instance;

    public HDSLedgerMessageBuilder(String sender, Message.Type type) {
        instance = new LedgerMessageDto(sender, type);
    }

    public HDSLedgerMessageBuilder setValue(String value) {
        instance.setValue(value);
        return this;
    }

    public HDSLedgerMessageBuilder setSignature(byte[] signature) {
        instance.setSignature(signature);
        return this;
    }

    public LedgerMessageDto build() {
        return instance;
    }
}
