package pt.ulisboa.tecnico.hdsledger.shared;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Getter;
import pt.ulisboa.tecnico.hdsledger.shared.communication.Message;
import pt.ulisboa.tecnico.hdsledger.shared.communication.MessageDeserializer;
import pt.ulisboa.tecnico.hdsledger.shared.communication.ledger_message.SignedLedgerRequest;
import pt.ulisboa.tecnico.hdsledger.shared.communication.ledger_message.SignedLedgerRequestDeserializer;

/**
 * The {@code SerializationUtils} class provides utility methods to serialize and deserialize objects.
 */
public class SerializationUtils {

    @Getter
    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(SignedLedgerRequest.class, new SignedLedgerRequestDeserializer())
            .registerTypeAdapter(Message.class, new MessageDeserializer())
            .create();

    private SerializationUtils() {
        // Hides the implicit public constructor
    }

    /**
     * Serializes an object to a byte array.
     *
     * @param object the object to serialize
     * @return the byte array representing the serialized object
     */
    public static byte[] serializeToBytes(Object object) {
        return gson.toJson(object).getBytes();
    }

    /**
     * Serializes an object to a JSON string.
     *
     * @param object the object to serialize
     * @return the JSON string representing the serialized object
     */
    public static String serialize(Object object) {
        return gson.toJson(object);
    }

    /**
     * Deserializes a byte array to an object.
     *
     * @param value the byte array to deserialize
     * @param clazz the class of the object to deserialize
     * @param <T>   the type of the object to deserialize
     * @return the deserialized object
     */
    public static <T> T deserialize(byte[] value, Class<T> clazz) {
        return getGson().fromJson(new String(value), clazz);
    }

    /**
     * Deserializes a JSON string to an object.
     *
     * @param value the JSON string to deserialize
     * @param clazz the class of the object to deserialize
     * @param <T>   the type of the object to deserialize
     * @return the deserialized object
     */
    public static <T> T deserialize(String value, Class<T> clazz) {
        return getGson().fromJson(value, clazz);
    }
}
