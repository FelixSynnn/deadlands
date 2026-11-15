package de.maxhenkel.sound_physics_remastered.configbuilder.entry;

import de.maxhenkel.sound_physics_remastered.configbuilder.Config;

public interface ConfigEntry<T> {
    ConfigEntry<T> comment(String... var1);

    String[] getComments();

    T get();

    ConfigEntry<T> set(T var1);

    String getKey();

    ConfigEntry<T> reset();

    ConfigEntry<T> save();

    ConfigEntry<T> saveSync();

    T getDefault();

    Config getConfig();
}
