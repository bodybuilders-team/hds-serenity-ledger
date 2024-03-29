package pt.ulisboa.tecnico.hdsledger.shared;

import java.util.Map;
import java.util.stream.Collectors;

public class Utils {
    public static String convertWithStream(Map<?, ?> map) {
        return map.keySet().stream()
                .map(key -> key + "=" + map.get(key))
                .collect(Collectors.joining(", ", "{", "}"));
    }


}
