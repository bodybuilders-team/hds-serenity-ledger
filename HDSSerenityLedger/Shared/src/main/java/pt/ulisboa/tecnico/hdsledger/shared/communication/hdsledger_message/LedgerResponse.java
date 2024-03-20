package pt.ulisboa.tecnico.hdsledger.shared.communication.hdsledger_message;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import pt.ulisboa.tecnico.hdsledger.shared.communication.Message;

import java.text.MessageFormat;

@SuperBuilder
public class LedgerResponse extends Message {
    @Getter
    @Setter
    private long originalRequestId;

    @Getter
    private String message;

    @Override
    public String toString() {
        switch (this.getType()) {
            case Type.TRANSFER_RESPONSE, Type.BALANCE_RESPONSE -> {
                return MessageFormat.format("<{0}({1}, \"{2}\"), messageId={3}>",
                        this.getType(),
                        this.getOriginalRequestId(),
                        this.getMessage(),
                        this.getMessageId()
                );
            }
            default -> {
                return "NO REPRESENTATION";
            }
        }
    }
}
