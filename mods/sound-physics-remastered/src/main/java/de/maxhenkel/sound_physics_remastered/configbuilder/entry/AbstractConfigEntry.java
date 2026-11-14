package de.maxhenkel.sound_physics_remastered.configbuilder.entry;

import de.maxhenkel.sound_physics_remastered.configbuilder.CommentedPropertyConfig;
import de.maxhenkel.sound_physics_remastered.configbuilder.Config;
import de.maxhenkel.sound_physics_remastered.configbuilder.entry.serializer.ValueSerializer;
import java.util.Objects;
import javax.annotation.Nullable;

public abstract class AbstractConfigEntry<T> implements ConfigEntry<T> {
    protected final CommentedPropertyConfig config;
    protected final ValueSerializer<T> serializer;
    protected String[] comments;
    protected String key;
    protected T def;
    @Nullable
    protected T value;

    public AbstractConfigEntry(CommentedPropertyConfig config, ValueSerializer<T> serializer, String[] comments, String key, T def) {
        Objects.requireNonNull(config);
        Objects.requireNonNull(serializer);
        Objects.requireNonNull(comments);
        Objects.requireNonNull(key);
        Objects.requireNonNull(def);
        this.config = config;
        this.serializer = serializer;
        this.comments = comments;
        this.key = key;
        this.def = def;
    }

    public void reload() {
        if (this.config.getProperties().containsKey(this.key)) {
            T val = this.serializer.deserialize(this.config.getProperties().get(this.key));
            if (val == null) {
                this.reset();
            } else {
                this.value = this.fixValue(val);
                this.syncEntryToProperties();
            }
        } else {
            this.reset();
        }
    }

    @Override
    public T get() {
        return this.value;
    }

    public AbstractConfigEntry<T> set(T value) {
        if (this.value != null && this.value.equals(value)) {
            return this;
        }

        this.value = this.fixValue(value);
        this.syncEntryToProperties();
        return this;
    }

    @Override
    public String getKey() {
        return this.key;
    }

    public AbstractConfigEntry<T> comment(String... comments) {
        this.comments = comments;
        this.syncEntryToProperties();
        return this;
    }

    @Override
    public String[] getComments() {
        return this.comments;
    }

    @Override
    public ConfigEntry<T> reset() {
        this.value = this.def;
        this.syncEntryToProperties();
        return this;
    }

    private void syncEntryToProperties() {
        String serialized = this.serializer.serialize(this.value);
        if (serialized == null) {
            if (this.value == this.def) {
                throw new IllegalStateException("Failed to serialize default value");
            }

            this.reset();
        } else {
            this.config.getProperties().set(this.key, serialized, this.comments);
        }
    }

    @Override
    public ConfigEntry<T> save() {
        this.config.save();
        return this;
    }

    @Override
    public ConfigEntry<T> saveSync() {
        this.config.saveSync();
        return this;
    }

    T fixValue(T value) {
        return value;
    }

    @Override
    public T getDefault() {
        return this.def;
    }

    @Override
    public Config getConfig() {
        return this.config;
    }

    public ValueSerializer<T> getSerializer() {
        return this.serializer;
    }
}
