package de.maxhenkel.sound_physics_remastered.configbuilder.entry;

import de.maxhenkel.sound_physics_remastered.configbuilder.CommentedPropertyConfig;
import de.maxhenkel.sound_physics_remastered.configbuilder.entry.serializer.ValueSerializer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class IntegerConfigEntry extends AbstractRangedConfigEntry<Integer> {
    public IntegerConfigEntry(
        CommentedPropertyConfig config,
        ValueSerializer<Integer> serializer,
        String[] comments,
        String key,
        Integer def,
        @Nullable Integer min,
        @Nullable Integer max
    ) {
        super(config, serializer, comments, key, def, min, max);
        this.reload();
    }

    @Nonnull
    Integer minimumPossibleValue() {
        return Integer.MIN_VALUE;
    }

    @Nonnull
    Integer maximumPossibleValue() {
        return Integer.MAX_VALUE;
    }

    Integer fixValue(Integer value) {
        return Math.max(Math.min(value, this.max), this.min);
    }
}
