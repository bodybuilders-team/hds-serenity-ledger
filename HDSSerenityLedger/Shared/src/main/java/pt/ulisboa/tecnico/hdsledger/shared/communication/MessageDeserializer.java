package pt.ulisboa.tecnico.hdsledger.shared.communication;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;

public class MessageDeserializer implements JsonDeserializer<Message> {

    @Override
    public Message deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();

        Message.Type messageType = context.deserialize(jsonObject.get("type"), Message.Type.class);

        final var messageClass = messageType.getClassType();

        if (messageClass.equals(Message.class))
            return Message.builder()
                    .type(messageType)
                    .senderId(context.deserialize(jsonObject.get("senderId"), String.class))
                    .messageId(context.deserialize(jsonObject.get("messageId"), int.class))
                    .build();

        return context.deserialize(jsonObject, messageClass);
    }
}