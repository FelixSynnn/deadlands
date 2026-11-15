package de.maxhenkel.sound_physics_remastered.configbuilder.custom.serializer;

import de.maxhenkel.sound_physics_remastered.configbuilder.custom.StringMap;
import de.maxhenkel.sound_physics_remastered.configbuilder.entry.serializer.ValueSerializer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

public class StringMapValueSerializer implements ValueSerializer<StringMap> {
    public static final StringMapValueSerializer INSTANCE = new StringMapValueSerializer();
    public static final Pattern QUOTE_ESCAPE_PATTERN = Pattern.compile("\"((?:(?![\"\\\\]).|\\\\.)*)\"\\s*=\\s*\"((?:(?![\"\\\\]).|\\\\.)*)\"");

    @Nullable
    public StringMap deserialize(String str) {
        boolean matches = QUOTE_ESCAPE_PATTERN.splitAsStream(str).allMatch(s -> s.trim().isEmpty() || s.trim().equals(","));
        if (!matches) {
            return null;
        }

        Map<String, String> map = new LinkedHashMap<>();
        Matcher matcher = QUOTE_ESCAPE_PATTERN.matcher(str);

        while (matcher.find()) {
            map.put(unescape(matcher.group(1)), unescape(matcher.group(2)));
        }

        return StringMap.of(map);
    }

    public String serialize(StringMap val) {
        List<String> resultList = new ArrayList<>(val.size());

        for (Entry<String, String> entry : val.entrySet()) {
            resultList.add("\"" + escape(entry.getKey()) + "\"=\"" + escape(entry.getValue()) + "\"");
        }

        return String.join(",", resultList);
    }

    private static String escape(String input) {
        return input.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String unescape(String input) {
        return input.replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
