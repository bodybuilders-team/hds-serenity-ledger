package pt.ulisboa.tecnico.hdsledger.shared.communication;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@AllArgsConstructor
@EqualsAndHashCode
public class SignedMessage {
    @Setter
    private Message message;
    @Setter
    private byte[] signature;
}
