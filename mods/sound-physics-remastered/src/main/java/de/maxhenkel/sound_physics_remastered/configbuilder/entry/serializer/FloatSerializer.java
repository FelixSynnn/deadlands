package de.maxhenkel.sound_physics_remastered.configbuilder.entry.serializer;

import javax.annotation.Nullable;

public class FloatSerializer implements ValueSerializer<Float> {
    public static final FloatSerializer INSTANCE = new FloatSerializer();

    @Nullable
    public Float deserialize(String str) {
        try {
            return Float.parseFloat(str);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Nullable
    public String serialize(Float val) {
        return String.valueOf(val);
    }
}
