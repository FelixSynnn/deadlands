package de.maxhenkel.sound_physics_remastered.configbuilder.entry.serializer;

import javax.annotation.Nullable;

public class StringSerializer implements ValueSerializer<String> {
    public static final StringSerializer INSTANCE = new StringSerializer();

    @Nullable
    public String deserialize(String str) {
        return str;
    }

    @Nullable
    public String serialize(String val) {
        return val;
    }
}
