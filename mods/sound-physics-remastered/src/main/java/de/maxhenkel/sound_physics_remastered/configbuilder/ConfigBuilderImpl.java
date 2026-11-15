package de.maxhenkel.sound_physics_remastered.configbuilder;

import de.maxhenkel.sound_physics_remastered.configbuilder.custom.IntegerList;
import de.maxhenkel.sound_physics_remastered.configbuilder.custom.StringList;
import de.maxhenkel.sound_physics_remastered.configbuilder.custom.StringMap;
import de.maxhenkel.sound_physics_remastered.configbuilder.custom.serializer.IntegerListValueSerializer;
import de.maxhenkel.sound_physics_remastered.configbuilder.custom.serializer.StringListValueSerializer;
import de.maxhenkel.sound_physics_remastered.configbuilder.custom.serializer.StringMapValueSerializer;
import de.maxhenkel.sound_physics_remastered.configbuilder.custom.serializer.UUIDSerializer;
import de.maxhenkel.sound_physics_remastered.configbuilder.entry.AbstractConfigEntry;
import de.maxhenkel.sound_physics_remastered.configbuilder.entry.BooleanConfigEntry;
import de.maxhenkel.sound_physics_remastered.configbuilder.entry.ConfigEntry;
import de.maxhenkel.sound_physics_remastered.configbuilder.entry.DoubleConfigEntry;
import de.maxhenkel.sound_physics_remastered.configbuilder.entry.EnumConfigEntry;
import de.maxhenkel.sound_physics_remastered.configbuilder.entry.FloatConfigEntry;
import de.maxhenkel.sound_physics_remastered.configbuilder.entry.GenericConfigEntry;
import de.maxhenkel.sound_physics_remastered.configbuilder.entry.IntegerConfigEntry;
import de.maxhenkel.sound_physics_remastered.configbuilder.entry.LongConfigEntry;
import de.maxhenkel.sound_physics_remastered.configbuilder.entry.StringConfigEntry;
import de.maxhenkel.sound_physics_remastered.configbuilder.entry.serializer.BooleanSerializer;
import de.maxhenkel.sound_physics_remastered.configbuilder.entry.serializer.DoubleSerializer;
import de.maxhenkel.sound_physics_remastered.configbuilder.entry.serializer.EnumSerializer;
import de.maxhenkel.sound_physics_remastered.configbuilder.entry.serializer.FloatSerializer;
import de.maxhenkel.sound_physics_remastered.configbuilder.entry.serializer.IntegerSerializer;
import de.maxhenkel.sound_physics_remastered.configbuilder.entry.serializer.LongSerializer;
import de.maxhenkel.sound_physics_remastered.configbuilder.entry.serializer.StringSerializer;
import de.maxhenkel.sound_physics_remastered.configbuilder.entry.serializer.ValueSerializable;
import de.maxhenkel.sound_physics_remastered.configbuilder.entry.serializer.ValueSerializer;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

public class ConfigBuilderImpl implements ConfigBuilder {
    protected final CommentedPropertyConfig config;
    protected final Map<Class<?>, ValueSerializer<?>> valueSerializers;
    protected List<AbstractConfigEntry<?>> entries;
    protected boolean frozen;

    public ConfigBuilderImpl(CommentedPropertyConfig config) {
        this(config, null);
    }

    public ConfigBuilderImpl(CommentedPropertyConfig config, @Nullable Map<Class<?>, ValueSerializer<?>> customValueSerializers) {
        this.config = config;
        this.valueSerializers = getDefaultValueSerializers();
        if (customValueSerializers != null) {
            this.valueSerializers.putAll(customValueSerializers);
        }

        this.entries = new ArrayList<>();
    }

    void removeUnused() {
        List<String> existingKeys = this.entries.stream().map(AbstractConfigEntry::getKey).collect(Collectors.toList());

        for (String key : this.config.getProperties().keySet().stream().filter(s -> !existingKeys.contains(s)).collect(Collectors.toList())) {
            this.config.getProperties().remove(key);
        }
    }

    void sortEntries() {
        Map<String, Integer> map = new HashMap<>();

        for (int i = 0; i < this.entries.size(); i++) {
            map.put(this.entries.get(i).getKey(), i);
        }

        this.config.getProperties().sort(Comparator.comparingInt(o -> map.getOrDefault(o, Integer.MAX_VALUE)));
    }

    void reloadFromDisk() {
        this.config.reload();
        this.entries.forEach(AbstractConfigEntry::reload);
    }

    void freeze() {
        this.frozen = true;
    }

    void checkFrozen() {
        if (this.frozen) {
            throw new IllegalStateException("ConfigBuilder is frozen");
        }
    }

    public ConfigBuilderImpl header(String... header) {
        this.checkFrozen();
        this.config.getProperties().setHeaderComments(Arrays.asList(header));
        return this;
    }

    @Override
    public BooleanConfigEntry booleanEntry(String key, Boolean def, String... comments) {
        this.checkFrozen();
        BooleanConfigEntry entry = new BooleanConfigEntry(this.config, BooleanSerializer.INSTANCE, comments, key, def);
        this.entries.add(entry);
        return entry;
    }

    @Override
    public IntegerConfigEntry integerEntry(String key, Integer def, Integer min, Integer max, String... comments) {
        this.checkFrozen();
        IntegerConfigEntry entry = new IntegerConfigEntry(this.config, IntegerSerializer.INSTANCE, comments, key, def, min, max);
        this.entries.add(entry);
        return entry;
    }

    @Override
    public LongConfigEntry longEntry(String key, Long def, Long min, Long max, String... comments) {
        this.checkFrozen();
        LongConfigEntry entry = new LongConfigEntry(this.config, LongSerializer.INSTANCE, comments, key, def, min, max);
        this.entries.add(entry);
        return entry;
    }

    @Override
    public DoubleConfigEntry doubleEntry(String key, Double def, Double min, Double max, String... comments) {
        this.checkFrozen();
        DoubleConfigEntry entry = new DoubleConfigEntry(this.config, DoubleSerializer.INSTANCE, comments, key, def, min, max);
        this.entries.add(entry);
        return entry;
    }

    @Override
    public FloatConfigEntry floatEntry(String key, Float def, Float min, Float max, String... comments) {
        this.checkFrozen();
        FloatConfigEntry entry = new FloatConfigEntry(this.config, FloatSerializer.INSTANCE, comments, key, def, min, max);
        this.entries.add(entry);
        return entry;
    }

    @Override
    public StringConfigEntry stringEntry(String key, String def, String... comments) {
        this.checkFrozen();
        StringConfigEntry entry = new StringConfigEntry(this.config, StringSerializer.INSTANCE, comments, key, def);
        this.entries.add(entry);
        return entry;
    }

    @Override
    public <E extends Enum<E>> EnumConfigEntry<E> enumEntry(String key, E def, String... comments) {
        this.checkFrozen();
        EnumConfigEntry<E> entry = new EnumConfigEntry<>(this.config, new EnumSerializer<>((Class<E>)def.getClass()), comments, key, def);
        this.entries.add(entry);
        return entry;
    }

    @Override
    public <T> ConfigEntry<T> entry(String key, T def, String... comments) {
        this.checkFrozen();
        AbstractConfigEntry<T> entry = this.entryInternal(key, def, comments);
        this.entries.add(entry);
        return entry;
    }

    private <T> AbstractConfigEntry<T> entryInternal(String key, T def, String... comments) {
        ValueSerializer<?> valueSerializer = this.valueSerializers.get(def.getClass());
        if (valueSerializer != null) {
            return new GenericConfigEntry<>(this.config, (ValueSerializer<T>)valueSerializer, comments, key, def);
        }

        if (def instanceof Enum) {
            return this.enumEntry(key, (Enum)def, comments);
        }

        try {
            ValueSerializable annotation = def.getClass().getDeclaredAnnotation(ValueSerializable.class);
            if (annotation == null) {
                throw new IllegalArgumentException(String.format("Unsupported data type: %s", def.getClass().getName()));
            }

            Class<? extends ValueSerializer<?>> entryConverterClass = annotation.value();
            Constructor<? extends ValueSerializer<?>> constructor = entryConverterClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            constructor.newInstance();
            ValueSerializer<T> converter = (ValueSerializer<T>)constructor.newInstance();
            return new GenericConfigEntry<>(this.config, converter, comments, key, def);
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException | NoSuchMethodException e) {
            throw new IllegalArgumentException("Could not instantiate value serializer", e);
        }
    }

    protected static Map<Class<?>, ValueSerializer<?>> getDefaultValueSerializers() {
        Map<Class<?>, ValueSerializer<?>> valueSerializers = new HashMap<>();
        valueSerializers.put(Boolean.class, new BooleanSerializer());
        valueSerializers.put(Integer.class, new IntegerSerializer());
        valueSerializers.put(Long.class, new LongSerializer());
        valueSerializers.put(Float.class, new FloatSerializer());
        valueSerializers.put(Double.class, new DoubleSerializer());
        valueSerializers.put(String.class, new StringSerializer());
        valueSerializers.put(UUID.class, UUIDSerializer.INSTANCE);
        valueSerializers.put(StringList.class, StringListValueSerializer.INSTANCE);
        valueSerializers.put(IntegerList.class, IntegerListValueSerializer.INSTANCE);
        valueSerializers.put(StringMap.class, StringMapValueSerializer.INSTANCE);
        return valueSerializers;
    }
}
