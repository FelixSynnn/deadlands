package de.maxhenkel.sound_physics_remastered.configbuilder.entry.serializer;

import javax.annotation.Nullable;

public interface ValueSerializer<T> {
    @Nullable
    T deserialize(String var1);

    @Nullable
    String serialize(T var1);
}
