package com.sonicether.soundphysics.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class ConfigUtils {
    public static <T extends Comparable<T>, U> Map<T, U> sortMap(Map<T, U> map) {
        List<Entry<T, U>> entryList = new ArrayList<>(map.entrySet());
        entryList.sort(Entry.comparingByKey());
        LinkedHashMap<T, U> sorted = new LinkedHashMap<>();

        for (Entry<T, U> entry : entryList) {
            sorted.put(entry.getKey(), entry.getValue());
        }

        return sorted;
    }
}
