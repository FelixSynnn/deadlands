package de.maxhenkel.sound_physics_remastered.configbuilder.custom;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

public class AbstractValueMap<K, V> implements Map<K, V> {
    protected final Map<K, V> map;

    protected AbstractValueMap(Map<K, V> map) {
        this.map = Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }

    @Override
    public int size() {
        return this.map.size();
    }

    @Override
    public boolean isEmpty() {
        return this.map.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return this.map.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return this.map.containsValue(value);
    }

    @Override
    public V get(Object key) {
        return this.map.get(key);
    }

    @Override
    public V put(K key, V value) {
        return throwException();
    }

    @Override
    public V remove(Object key) {
        return throwException();
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        throwException();
    }

    @Override
    public void clear() {
        throwException();
    }

    @Override
    public Set<K> keySet() {
        return this.map.keySet();
    }

    @Override
    public Collection<V> values() {
        return this.map.values();
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return this.map.entrySet();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o != null && this.getClass() == o.getClass()) {
            AbstractValueMap<?, ?> that = (AbstractValueMap<?, ?>)o;
            return this.map.equals(that.map);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return this.map.hashCode();
    }

    private static <T> T throwException() {
        throw new UnsupportedOperationException("Can't modify config entries");
    }

    public abstract static class Builder<K, V, M extends AbstractValueMap<K, V>> {
        protected final Map<K, V> map = new LinkedHashMap<>();

        protected Builder() {
        }

        public AbstractValueMap.Builder<K, V, M> put(K key, V value) {
            this.map.put(key, value);
            return this;
        }

        public AbstractValueMap.Builder<K, V, M> putAll(Map<K, V> map) {
            this.map.putAll(map);
            return this;
        }

        public abstract M build();
    }
}
