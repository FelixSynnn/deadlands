package vectorwing.farmersdelight.refabricated.mlconfigs.fabric;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import vectorwing.farmersdelight.refabricated.mlconfigs.ConfigBuilder;
import vectorwing.farmersdelight.refabricated.mlconfigs.ConfigType;
import vectorwing.farmersdelight.refabricated.mlconfigs.ModConfigHolder;
import vectorwing.farmersdelight.refabricated.mlconfigs.fabric.values.BoolConfigValue;
import vectorwing.farmersdelight.refabricated.mlconfigs.fabric.values.ColorConfigValue;
import vectorwing.farmersdelight.refabricated.mlconfigs.fabric.values.ConfigValue;
import vectorwing.farmersdelight.refabricated.mlconfigs.fabric.values.DoubleConfigValue;
import vectorwing.farmersdelight.refabricated.mlconfigs.fabric.values.EnumConfigValue;
import vectorwing.farmersdelight.refabricated.mlconfigs.fabric.values.FloatConfigValue;
import vectorwing.farmersdelight.refabricated.mlconfigs.fabric.values.IntConfigValue;
import vectorwing.farmersdelight.refabricated.mlconfigs.fabric.values.JsonConfigValue;
import vectorwing.farmersdelight.refabricated.mlconfigs.fabric.values.ListStringConfigValue;
import vectorwing.farmersdelight.refabricated.mlconfigs.fabric.values.ObjectConfigValue;
import vectorwing.farmersdelight.refabricated.mlconfigs.fabric.values.StringConfigValue;

public class ConfigBuilderImpl extends ConfigBuilder {
    private final ConfigSubCategory mainCategory = new ConfigSubCategory(this.getName().getNamespace());
    private final Deque<ConfigSubCategory> categoryStack = new ArrayDeque<>();

    public static ConfigBuilder create(Identifier name, ConfigType type) {
        return new ConfigBuilderImpl(name, type);
    }

    public ConfigBuilderImpl(Identifier name, ConfigType type) {
        super(name, type);
        this.categoryStack.push(this.mainCategory);
    }

    public @NonNull FabricConfigHolder build() {
        assert this.categoryStack.size() == 1;
        return new FabricConfigHolder(this.getName(), this.mainCategory, this.type, this.changeCallback);
    }

    @Override
    public String currentCategory() {
        return this.categoryStack.peek().getName();
    }

    public ConfigBuilderImpl push(String translation) {
        ConfigSubCategory cat = new ConfigSubCategory(translation);
        this.categoryStack.peek().addEntry(cat);
        this.categoryStack.push(cat);
        return this;
    }

    public ConfigBuilderImpl pop() {
        assert this.categoryStack.size() != 1;
        this.categoryStack.pop();
        return this;
    }

    private void doAddConfig(String name, ConfigValue<?> config) {
        config.setTranslationKey(this.translationKey(name));
        this.maybeAddTranslationString(name);
        String tooltipKey = this.tooltipKey(name);
        if (this.comments.containsKey(tooltipKey)) {
            config.setDescriptionKey(tooltipKey);
        }

        this.categoryStack.peek().addEntry(config);
        if (this.categoryStack.size() <= 1) {
            throw new AssertionError();
        }
    }

    @Override
    public Supplier<Boolean> define(String name, boolean defaultValue) {
        BoolConfigValue config = new BoolConfigValue(name, defaultValue);
        this.doAddConfig(name, config);
        return config;
    }

    @Override
    public Supplier<Double> define(String name, double defaultValue, double min, double max) {
        DoubleConfigValue config = new DoubleConfigValue(name, defaultValue, min, max);
        this.doAddConfig(name, config);
        return config;
    }

    @Override
    public Supplier<Float> define(String name, float defaultValue, float min, float max) {
        FloatConfigValue config = new FloatConfigValue(name, defaultValue, min, max);
        this.doAddConfig(name, config);
        return config;
    }

    @Override
    public Supplier<Integer> define(String name, int defaultValue, int min, int max) {
        IntConfigValue config = new IntConfigValue(name, defaultValue, min, max);
        this.doAddConfig(name, config);
        return config;
    }

    @Override
    public Supplier<Integer> defineColor(String name, int defaultValue) {
        ColorConfigValue config = new ColorConfigValue(name, defaultValue);
        this.doAddConfig(name, config);
        return config;
    }

    @Override
    public Supplier<String> define(String name, String defaultValue, Predicate<Object> validator) {
        StringConfigValue config = new StringConfigValue(name, defaultValue, validator);
        this.doAddConfig(name, config);
        return config;
    }

    @Override
    public <T extends String> Supplier<List<String>> define(String name, List<? extends T> defaultValue, Predicate<Object> predicate) {
        ListStringConfigValue<String> config = new ListStringConfigValue<>(name, (List<String>)(List)defaultValue, predicate);
        this.doAddConfig(name, config);
        return config;
    }

    @Override
    public <V extends Enum<V>> Supplier<V> define(String name, V defaultValue) {
        EnumConfigValue<V> config = new EnumConfigValue<>(name, defaultValue);
        this.doAddConfig(name, config);
        return config;
    }

    @Override
    public Supplier<JsonElement> defineJson(String name, Supplier<JsonElement> defaultValue) {
        JsonConfigValue config = new JsonConfigValue(name, defaultValue);
        this.doAddConfig(name, config);
        return config;
    }

    @Override
    public Supplier<JsonElement> defineJson(String name, JsonElement defaultValue) {
        JsonConfigValue config = new JsonConfigValue(name, () -> defaultValue);
        this.doAddConfig(name, config);
        return config;
    }

    @Override
    public <T> Supplier<T> defineObject(String name, com.google.common.base.Supplier<T> defaultValue, Codec<T> codec) {
        ObjectConfigValue<T> config = new ObjectConfigValue<>(name, defaultValue, codec);
        this.doAddConfig(name, config);
        return config;
    }

    @Override
    protected void maybeAddTranslationString(String name) {
        this.comments.put(this.translationKey(name), ModConfigHolder.getReadableName(name));
        super.maybeAddTranslationString(name);
    }

    @Override
    public ConfigBuilder gameRestart() {
        return this;
    }

    @Override
    public ConfigBuilder worldReload() {
        return this;
    }

    @Override
    public ConfigBuilder comment(String comment) {
        return super.comment(comment);
    }
}
