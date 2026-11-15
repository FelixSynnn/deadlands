package de.maxhenkel.sound_physics_remastered.configbuilder.entry;

import de.maxhenkel.sound_physics_remastered.configbuilder.CommentedPropertyConfig;
import de.maxhenkel.sound_physics_remastered.configbuilder.entry.serializer.ValueSerializer;

public class GenericConfigEntry<T> extends AbstractConfigEntry<T> {
    public GenericConfigEntry(CommentedPropertyConfig config, ValueSerializer<T> serializer, String[] comments, String key, T def) {
        super(config, serializer, comments, key, def);
        this.reload();
    }
}
