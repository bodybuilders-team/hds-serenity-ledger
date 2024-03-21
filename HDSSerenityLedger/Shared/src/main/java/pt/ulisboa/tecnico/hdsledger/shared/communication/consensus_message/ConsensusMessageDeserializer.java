package pt.ulisboa.tecnico.hdsledger.shared.communication.consensus_message;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import pt.ulisboa.tecnico.hdsledger.shared.communication.Message;
import pt.ulisboa.tecnico.hdsledger.shared.models.Block;

import java.lang.reflect.Type;

/**
 * Deserializer for the ConsensusMessage class.
 */
public class ConsensusMessageDeserializer implements JsonDeserializer<ConsensusMessage> {

    @Override
    public ConsensusMessage deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();

        Message.Type messageType = context.deserialize(jsonObject.get("type"), Message.Type.class);

        // Based on the type, deserialize the specific LedgerRequest
        final Class<?> valuesClazz = Block.class;

        return ConsensusMessage.builder()
                .type(messageType)
                .senderId(context.deserialize(jsonObject.get("senderId"), String.class))
                .messageId(context.deserialize(jsonObject.get("messageId"), int.class))
                .consensusInstance(context.deserialize(jsonObject.get("consensusInstance"), int.class))
                .round(context.deserialize(jsonObject.get("round"), int.class))
                .preparedRound(context.deserialize(jsonObject.get("preparedRound"), int.class))
                .preparedValue(context.deserialize(jsonObject.get("preparedValue"), valuesClazz))
                .replyTo(context.deserialize(jsonObject.get("replyTo"), String.class))
                .replyToMessageId(context.deserialize(jsonObject.get("replyToMessageId"), int.class))
                .value(context.deserialize(jsonObject.get("value"), valuesClazz))
                .build();
    }
}