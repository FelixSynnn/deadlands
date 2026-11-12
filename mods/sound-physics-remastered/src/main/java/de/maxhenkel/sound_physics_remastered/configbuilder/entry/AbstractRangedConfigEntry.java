package de.maxhenkel.sound_physics_remastered.configbuilder.entry;

import de.maxhenkel.sound_physics_remastered.configbuilder.CommentedPropertyConfig;
import de.maxhenkel.sound_physics_remastered.configbuilder.entry.serializer.ValueSerializer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public abstract class AbstractRangedConfigEntry<T> extends AbstractConfigEntry<T> implements RangedConfigEntry<T> {
    @Nonnull
    protected final T min;
    @Nonnull
    protected final T max;

    public AbstractRangedConfigEntry(
        CommentedPropertyConfig config, ValueSerializer<T> serializer, String[] comments, String key, T def, @Nullable T min, @Nullable T max
    ) {
        super(config, serializer, comments, key, def);
        if (min != null) {
            this.min = min;
        } else {
            this.min = this.minimumPossibleValue();
        }

        if (max != null) {
            this.max = max;
        } else {
            this.max = this.maximumPossibleValue();
        }
    }

    @Nonnull
    @Override
    public T getMin() {
        return this.min;
    }

    @Nonnull
    @Override
    public T getMax() {
        return this.max;
    }

    @Nonnull
    abstract T minimumPossibleValue();

    @Nonnull
    abstract T maximumPossibleValue();
}
