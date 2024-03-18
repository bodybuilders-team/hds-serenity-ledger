package pt.ulisboa.tecnico.hdsledger.shared.communication.hdsledger_message;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import pt.ulisboa.tecnico.hdsledger.shared.communication.Message;

import java.text.MessageFormat;

@SuperBuilder
public class LedgerRequestDto extends Message {
    @Getter
    @Setter
    private String value;
    @Getter
    @Setter
    private long requestId;
    @Getter
    @Setter
    private byte[] signature;

    public LedgerRequestDto(String senderId, Type type) {
        super(senderId, type);
    }


    public String toString() {
        switch (this.getType()) {
            case Type.BALANCE -> {
                return MessageFormat.format("BALANCE(\"{0}\")", this.getValue());
            }
            case Type.BALANCE_RESPONSE -> {
                return MessageFormat.format("BALANCE_RESPONSE(\"{0}\")", this.getValue());
            }
            case Type.TRANSFER -> {
                return "TRANSFER";
            }
            case Type.TRANSFER_RESPONSE -> {
                return MessageFormat.format("TRANSFER_RESPONSE(\"{0}\")", this.getValue());
            }
            default -> {
                return "NO REPRESENTATION";
            }
        }
    }

}
