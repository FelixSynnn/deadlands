package vectorwing.farmersdelight.refabricated.mlconfigs;

import com.google.common.base.Supplier;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import vectorwing.farmersdelight.refabricated.mlconfigs.fabric.ConfigBuilderImpl;

public abstract class ConfigBuilder {
    public static final Logger LOGGER = LogManager.getLogger("FD ML Configs");
    public static final boolean YACL = FabricLoader.getInstance().isModLoaded("yet_another_config_lib_v3");
    public static final boolean CLOTH_CONFIG = FabricLoader.getInstance().isModLoaded("cloth-config");
    protected final Map<String, String> comments = new HashMap<>();
    private String currentComment;
    private String currentKey;
    protected Runnable changeCallback;
    protected boolean usesDataBuddy = true;
    private final Identifier name;
    protected final ConfigType type;
    public static final Predicate<Object> STRING_CHECK = o -> o instanceof String;
    public static final Predicate<Object> LIST_STRING_CHECK = s -> s instanceof List ? ((Collection)s).stream().allMatch(o -> o instanceof String) : false;

    public static ConfigBuilder create(Identifier name, ConfigType type) {
        return new ConfigBuilderImpl(name, type);
    }

    public static ConfigBuilder create(String modId, ConfigType type) {
        return create(Identifier.fromNamespaceAndPath(modId, type.getDefaultName()), type);
    }

    protected ConfigBuilder(Identifier name, ConfigType type) {
        this.name = name;
        this.type = type;
    }

    public abstract ModConfigHolder build();

    public Identifier getName() {
        return this.name;
    }

    public abstract ConfigBuilder push(String var1);

    public abstract ConfigBuilder pop();

    public <T extends ConfigBuilder> T setWriteJsons() {
        this.usesDataBuddy = false;
        return (T)this;
    }

    public abstract java.util.function.Supplier<Boolean> define(String var1, boolean var2);

    public abstract java.util.function.Supplier<Double> define(String var1, double var2, double var4, double var6);

    public abstract java.util.function.Supplier<Float> define(String var1, float var2, float var3, float var4);

    public abstract java.util.function.Supplier<Integer> define(String var1, int var2, int var3, int var4);

    public abstract java.util.function.Supplier<Integer> defineColor(String var1, int var2);

    public abstract java.util.function.Supplier<String> define(String var1, String var2, Predicate<Object> var3);

    public java.util.function.Supplier<String> define(String name, String defaultValue) {
        return this.define(name, defaultValue, STRING_CHECK);
    }

    public <T extends String> java.util.function.Supplier<List<String>> define(String name, List<? extends T> defaultValue) {
        return this.define(name, defaultValue, s -> true);
    }

    public abstract String currentCategory();

    public abstract <T extends String> java.util.function.Supplier<List<String>> define(String var1, List<? extends T> var2, Predicate<Object> var3);

    public abstract <V extends Enum<V>> java.util.function.Supplier<V> define(String var1, V var2);

    public abstract <T> java.util.function.Supplier<T> defineObject(String var1, Supplier<T> var2, Codec<T> var3);

    public <T> java.util.function.Supplier<List<T>> defineObjectList(String name, Supplier<List<T>> defaultSupplier, Codec<T> codec) {
        return this.defineObject(name, defaultSupplier, codec.listOf());
    }

    public java.util.function.Supplier<Map<String, String>> defineMap(String name, Map<String, String> def) {
        return this.defineObject(name, () -> def, Codec.unboundedMap(Codec.STRING, Codec.STRING));
    }

    public java.util.function.Supplier<Map<Identifier, Identifier>> defineIDMap(String name, Map<Identifier, Identifier> def) {
        return this.defineObject(name, () -> def, Codec.unboundedMap(Identifier.CODEC, Identifier.CODEC));
    }

    public abstract java.util.function.Supplier<JsonElement> defineJson(String var1, JsonElement var2);

    public abstract java.util.function.Supplier<JsonElement> defineJson(String var1, java.util.function.Supplier<JsonElement> var2);

    public java.util.function.Supplier<Identifier> define(String name, Identifier defaultValue) {
        return new ConfigBuilder.IdentifierConfigValue(this, name, defaultValue);
    }

    public Component description(String name) {
        return Component.literal(name);
    }

    public Component tooltip(String name) {
        return Component.translatable(this.tooltipKey(name));
    }

    public String tooltipKey(String name) {
        return this.translationKey(name) + ".tooltip";
    }

    public String translationKey(String name) {
        return this.name.getNamespace() + ".configuration." + name;
    }

    public ConfigBuilder comment(String comment) {
        this.currentComment = comment;
        if (this.currentComment != null && this.currentKey != null) {
            this.comments.put(this.currentKey, this.currentComment);
            this.currentComment = null;
            this.currentKey = null;
        }

        return this;
    }

    public ConfigBuilder onChange(Runnable callback) {
        this.changeCallback = callback;
        return this;
    }

    public abstract ConfigBuilder worldReload();

    public abstract ConfigBuilder gameRestart();

    protected void maybeAddTranslationString(String name) {
        this.currentKey = this.tooltipKey(name);
        if (this.currentComment != null && this.currentKey != null) {
            this.comments.put(this.currentKey, this.currentComment);
            this.currentComment = null;
            this.currentKey = null;
        }

        if (this.currentCategory() == null) {
            throw new AssertionError("Current config category was null. How?");
        }
    }

    private static class IdentifierConfigValue implements java.util.function.Supplier<Identifier> {
        private final java.util.function.Supplier<String> inner;
        private Identifier cache;
        private String oldString;

        public IdentifierConfigValue(ConfigBuilder builder, String path, Identifier defaultValue) {
            this.inner = builder.define(path, defaultValue.toString(), s -> s != null && Identifier.tryParse((String)s) != null);
        }

        public Identifier get() {
            String s = this.inner.get();
            if (!s.equals(this.oldString)) {
                this.cache = null;
            }

            this.oldString = s;
            if (this.cache == null) {
                this.cache = Identifier.parse(s);
            }

            return this.cache;
        }
    }
}
