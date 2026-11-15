package de.maxhenkel.sound_physics_remastered.configbuilder.custom;

import java.util.List;

public class StringList extends AbstractValueList<String> {
    protected StringList(String... values) {
        super(values);
    }

    protected StringList(List<String> values) {
        super(values);
    }

    public static StringList of(String... values) {
        return new StringList(values);
    }

    public static StringList of(List<String> values) {
        return new StringList(values);
    }
}
