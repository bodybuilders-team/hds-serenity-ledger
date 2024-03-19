package pt.ulisboa.tecnico.hdsledger.shared;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.Getter;
import pt.ulisboa.tecnico.hdsledger.shared.communication.consensus_message.ConsensusMessage;
import pt.ulisboa.tecnico.hdsledger.shared.communication.consensus_message.ConsensusMessageDeserializer;
import pt.ulisboa.tecnico.hdsledger.shared.communication.hdsledger_message.SignedLedgerRequest;
import pt.ulisboa.tecnico.hdsledger.shared.communication.hdsledger_message.SignedLedgerRequestDeserializer;

public class SerializationUtils {

    @Getter
    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(SignedLedgerRequest.class, new SignedLedgerRequestDeserializer())
            .registerTypeAdapter(ConsensusMessage.class, new ConsensusMessageDeserializer())
            .create();

    private SerializationUtils() {
    }

    public static byte[] serializeToBytes(Object object) {
        return gson.toJson(object).getBytes();
    }

    public static String serialize(Object object) {
        return gson.toJson(object);
    }

    public static <T> T deserialize(byte[] value, Class<T> clazz) {
        return getGson().fromJson(new String(value), clazz);
    }

    public static <T> T deserialize(String value, Class<T> clazz) {
        return getGson().fromJson(value, clazz);
    }
}
