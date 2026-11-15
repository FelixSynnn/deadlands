package de.maxhenkel.sound_physics_remastered.configbuilder.entry;

import javax.annotation.Nonnull;

public interface RangedConfigEntry<T> extends ConfigEntry<T> {
    @Nonnull
    T getMin();

    @Nonnull
    T getMax();
}
