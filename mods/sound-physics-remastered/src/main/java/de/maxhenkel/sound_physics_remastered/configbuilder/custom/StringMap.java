package de.maxhenkel.sound_physics_remastered.configbuilder.custom;

import java.util.Collections;
import java.util.Map;

public class StringMap extends AbstractValueMap<String, String> {
    protected StringMap(Map<String, String> map) {
        super(map);
    }

    public static StringMap of(Map<String, String> map) {
        return new StringMap(map);
    }

    public static StringMap of() {
        return new StringMap(Collections.emptyMap());
    }

    public static StringMap.Builder builder() {
        return new StringMap.Builder();
    }

    public static class Builder extends AbstractValueMap.Builder<String, String, StringMap> {
        public StringMap build() {
            return new StringMap(this.map);
        }
    }
}
