package de.maxhenkel.sound_physics_remastered.configbuilder.entry;

import de.maxhenkel.sound_physics_remastered.configbuilder.CommentedPropertyConfig;
import de.maxhenkel.sound_physics_remastered.configbuilder.entry.serializer.ValueSerializer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class FloatConfigEntry extends AbstractRangedConfigEntry<Float> {
    public FloatConfigEntry(
        CommentedPropertyConfig config, ValueSerializer<Float> serializer, String[] comments, String key, Float def, @Nullable Float min, @Nullable Float max
    ) {
        super(config, serializer, comments, key, def, min, max);
        this.reload();
    }

    @Nonnull
    Float minimumPossibleValue() {
        return Float.MIN_VALUE;
    }

    @Nonnull
    Float maximumPossibleValue() {
        return Float.MAX_VALUE;
    }

    Float fixValue(Float value) {
        return Math.max(Math.min(value, this.max), this.min);
    }
}
