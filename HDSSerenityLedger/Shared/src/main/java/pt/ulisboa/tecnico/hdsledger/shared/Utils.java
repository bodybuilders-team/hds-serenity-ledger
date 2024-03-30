package pt.ulisboa.tecnico.hdsledger.shared;

import java.util.Map;
import java.util.stream.Collectors;

public class Utils {

    private Utils() {
        // Hides the implicit public constructor
    }

    /**
     * Converts a map to a string.
     *
     * @param map the map to convert
     * @return the string representing the map
     */
    public static String convertWithStream(Map<?, ?> map) {
        return map.keySet().stream()
                .map(key -> key + "=" + map.get(key))
                .collect(Collectors.joining(", ", "{", "}"));
    }
}
