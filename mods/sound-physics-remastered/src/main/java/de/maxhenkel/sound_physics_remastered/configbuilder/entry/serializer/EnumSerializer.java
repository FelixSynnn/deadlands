package de.maxhenkel.sound_physics_remastered.configbuilder.entry.serializer;

import javax.annotation.Nullable;

public class EnumSerializer<E extends Enum<E>> implements ValueSerializer<E> {
    protected Class<E> enumClass;

    public EnumSerializer(Class<E> enumClass) {
        this.enumClass = enumClass;
    }

    @Nullable
    public E deserialize(String str) {
        try {
            return Enum.valueOf(this.enumClass, str);
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    public String serialize(E val) {
        return val.name();
    }
}
