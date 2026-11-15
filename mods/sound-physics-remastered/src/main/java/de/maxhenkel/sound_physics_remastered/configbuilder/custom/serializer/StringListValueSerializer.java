package de.maxhenkel.sound_physics_remastered.configbuilder.custom.serializer;

import de.maxhenkel.sound_physics_remastered.configbuilder.custom.StringList;
import de.maxhenkel.sound_physics_remastered.configbuilder.entry.serializer.ValueSerializer;
import java.util.ArrayList;
import java.util.List;

public class StringListValueSerializer implements ValueSerializer<StringList> {
    public static final StringListValueSerializer INSTANCE = new StringListValueSerializer();

    public StringList deserialize(String str) {
        List<String> resultList = new ArrayList<>();

        for (String s : str.split("(?<!\\\\),")) {
            resultList.add(s.replace("\\,", ","));
        }

        return StringList.of(resultList);
    }

    public String serialize(StringList val) {
        List<String> resultList = new ArrayList<>(val.size());

        for (String str : val) {
            resultList.add(str.replace(",", "\\,"));
        }

        return String.join(",", resultList);
    }
}
