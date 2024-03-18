package pt.ulisboa.tecnico.hdsledger.shared;

import com.google.gson.Gson;

public class SerializationUtils {

    private static final Gson gson = new Gson();

    private SerializationUtils() {
    }

    public static Gson getGson() {
        return gson;
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
