package pt.ulisboa.tecnico.hdsledger.shared.communication.ledger_message;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import pt.ulisboa.tecnico.hdsledger.shared.communication.Message;

import java.lang.reflect.Type;

/**
 * The {@code SignedLedgerRequestDeserializer} class is a custom deserializer for the {@code SignedLedgerRequest} class.
 */
public class SignedLedgerRequestDeserializer implements JsonDeserializer<SignedLedgerRequest> {

    @Override
    public SignedLedgerRequest deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();

        Message.Type messageType = context.deserialize(jsonObject.get("type"), Message.Type.class);

        // Based on the type, deserialize the specific LedgerRequest
        final Class<? extends LedgerRequest> ledgerRequestClazz = switch (messageType) {
            case TRANSFER -> LedgerTransferRequest.class;
            case BALANCE -> LedgerCheckBalanceRequest.class;
            default -> throw new JsonParseException("Unknown type: " + messageType);
        };

        final LedgerRequest ledgerRequest = context.deserialize(jsonObject.get("ledgerRequest"), ledgerRequestClazz);

        return SignedLedgerRequest.builder()
                .type(messageType)
                .senderId(context.deserialize(jsonObject.get("senderId"), String.class))
                .messageId(context.deserialize(jsonObject.get("messageId"), int.class))
                .signature(context.deserialize(jsonObject.get("signature"), byte[].class))
                .ledgerRequest(ledgerRequest)
                .build();
    }
}