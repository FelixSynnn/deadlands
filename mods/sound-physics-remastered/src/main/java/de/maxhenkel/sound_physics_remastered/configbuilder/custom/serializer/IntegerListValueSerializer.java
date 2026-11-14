package de.maxhenkel.sound_physics_remastered.configbuilder.custom.serializer;

import de.maxhenkel.sound_physics_remastered.configbuilder.custom.IntegerList;
import de.maxhenkel.sound_physics_remastered.configbuilder.entry.serializer.ValueSerializer;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

public class IntegerListValueSerializer implements ValueSerializer<IntegerList> {
    public static final IntegerListValueSerializer INSTANCE = new IntegerListValueSerializer();

    @Nullable
    public IntegerList deserialize(String str) {
        List<Integer> resultList = new ArrayList<>();

        for (String s : str.split(",")) {
            try {
                resultList.add(Integer.valueOf(s));
            } catch (NumberFormatException e) {
                return null;
            }
        }

        return IntegerList.of(resultList);
    }

    public String serialize(IntegerList val) {
        List<String> resultList = new ArrayList<>(val.size());

        for (Integer i : val) {
            resultList.add(String.valueOf(i));
        }

        return String.join(",", resultList);
    }
}
