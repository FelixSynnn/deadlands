package de.maxhenkel.sound_physics_remastered.configbuilder.entry;

import de.maxhenkel.sound_physics_remastered.configbuilder.CommentedPropertyConfig;
import de.maxhenkel.sound_physics_remastered.configbuilder.entry.serializer.ValueSerializer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class DoubleConfigEntry extends AbstractRangedConfigEntry<Double> {
    public DoubleConfigEntry(
        CommentedPropertyConfig config,
        ValueSerializer<Double> serializer,
        String[] comments,
        String key,
        Double def,
        @Nullable Double min,
        @Nullable Double max
    ) {
        super(config, serializer, comments, key, def, min, max);
        this.reload();
    }

    @Nonnull
    Double minimumPossibleValue() {
        return Double.MIN_VALUE;
    }

    @Nonnull
    Double maximumPossibleValue() {
        return Double.MAX_VALUE;
    }

    Double fixValue(Double value) {
        return Math.max(Math.min(value, this.max), this.min);
    }
}
