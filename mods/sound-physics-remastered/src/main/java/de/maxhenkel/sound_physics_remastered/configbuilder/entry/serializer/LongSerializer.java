package de.maxhenkel.sound_physics_remastered.configbuilder.entry.serializer;

import javax.annotation.Nullable;

public class LongSerializer implements ValueSerializer<Long> {
    public static final LongSerializer INSTANCE = new LongSerializer();

    @Nullable
    public Long deserialize(String str) {
        try {
            return Long.parseLong(str);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Nullable
    public String serialize(Long val) {
        return String.valueOf(val);
    }
}
