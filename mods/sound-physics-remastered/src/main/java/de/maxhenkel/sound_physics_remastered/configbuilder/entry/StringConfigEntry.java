package de.maxhenkel.sound_physics_remastered.configbuilder.entry;

import de.maxhenkel.sound_physics_remastered.configbuilder.CommentedPropertyConfig;
import de.maxhenkel.sound_physics_remastered.configbuilder.entry.serializer.ValueSerializer;

public class StringConfigEntry extends AbstractConfigEntry<String> {
    public StringConfigEntry(CommentedPropertyConfig config, ValueSerializer<String> serializer, String[] comments, String key, String def) {
        super(config, serializer, comments, key, def);
        this.reload();
    }
}
