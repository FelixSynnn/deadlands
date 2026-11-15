package com.huaxiaosheng.spapacks.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public final class Json {
    private Json() {
    }

    public static String string(JsonObject o, String key, String fallback) {
        return o.get(key) instanceof JsonPrimitive p ? p.getAsString() : fallback;
    }

    public static String string(JsonObject o, String key) {
        return string(o, key, null);
    }

    public static double number(JsonObject o, String key, double fallback) {
        return o.get(key) instanceof JsonPrimitive p && p.isNumber() ? p.getAsDouble() : fallback;
    }

    public static boolean bool(JsonObject o, String key, boolean fallback) {
        return o.get(key) instanceof JsonPrimitive p && p.isBoolean() ? p.getAsBoolean() : fallback;
    }

    public static JsonObject object(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    public static JsonArray array(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : null;
    }

    public static String[] strings(JsonObject o, String key, String[] fallback) {
        JsonArray a = array(o, key);
        if (a == null) {
            return fallback;
        }

        String[] s = new String[a.size()];

        for (int i = 0; i < s.length; i++) {
            s[i] = a.get(i).getAsString();
        }

        return s;
    }
}
