package com.sonicether.soundphysics.config.blocksound;

import com.sonicether.soundphysics.Loggers;
import com.sonicether.soundphysics.config.ConfigUtils;
import de.maxhenkel.sound_physics_remastered.configbuilder.CommentedProperties;
import de.maxhenkel.sound_physics_remastered.configbuilder.CommentedPropertyConfig;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

public abstract class BlockSoundConfigBase extends CommentedPropertyConfig {
    private Map<BlockDefinition, Float> configMap;
    @Nullable
    private Map<TagKey<Block>, Float> blockTagCache;
    @Nullable
    private Map<Block, Float> blockCache;
    @Nullable
    private Map<SoundType, Float> soundTypeCache;

    public BlockSoundConfigBase(Path path) {
        super(new CommentedProperties(false));
        this.path = path;
        this.reload();
    }

    @Override
    public void load() throws IOException {
        Map<BlockDefinition, Float> map = new HashMap<>();
        this.addDefaults(map);
        super.load();

        for (String key : this.properties.keySet()) {
            String valueString = this.properties.get(key);
            if (valueString != null) {
                float value;
                try {
                    value = Float.parseFloat(valueString);
                } catch (NumberFormatException e) {
                    Loggers.warn("Failed to parse value of {}", key);
                    continue;
                }

                BlockDefinition blockDefinition = loadBlockDefinition(key);
                if (blockDefinition == null) {
                    Loggers.warn("Block definition {} not found", key);
                } else {
                    map.put(blockDefinition, value);
                }
            }
        }

        this.configMap = ConfigUtils.sortMap(map);
        this.invalidateCaches();
        this.saveSync();
    }

    public static BlockDefinition loadBlockDefinition(String configString) {
        BlockDefinition blockDefinition = BlockTagDefinition.fromConfigString(configString);
        if (blockDefinition != null) {
            return blockDefinition;
        }

        blockDefinition = BlockIdDefinition.fromConfigString(configString);
        return blockDefinition != null ? blockDefinition : BlockSoundTypeDefinition.fromConfigString(configString);
    }

    @Override
    public void saveSync() {
        this.properties.clear();
        this.properties
            .setHeaderComments(
                List.of(
                    "Values for blocks can be defined as follows:",
                    "",
                    "By sound type:",
                    "WOOD=1.0",
                    "",
                    "By block tag:",
                    "\\#minecraft\\:logs=1.0",
                    "",
                    "By block ID:",
                    "minecraft\\:oak_log=1.0"
                )
            );

        for (Entry<BlockDefinition, Float> entry : this.configMap.entrySet()) {
            String configKey = entry.getKey().getConfigString();
            this.properties.set(configKey, String.valueOf(entry.getValue()));
            String configComment = entry.getKey().getConfigComment();
            if (configComment != null) {
                this.properties.setComments(configKey, Collections.singletonList(configComment));
            } else {
                this.properties.setComments(configKey, Collections.emptyList());
            }
        }

        super.saveSync();
    }

    public Map<BlockDefinition, Float> getBlockDefinitions() {
        return Collections.unmodifiableMap(this.configMap);
    }

    public float getBlockDefinitionValue(BlockState blockState) {
        Float value = this.getBlocks().get(blockState.getBlock());
        if (value != null) {
            return value;
        }

        for (Entry<TagKey<Block>, Float> entry : this.getBlockTags().entrySet()) {
            if (isTagIn(entry.getKey(), blockState.getBlock())) {
                return entry.getValue();
            }
        }

        value = this.getSoundTypes().get(blockState.getSoundType());
        return value != null ? value : this.getDefaultValue();
    }

    public static <T> boolean isTagIn(TagKey<T> tagKey, T entry) {
        Optional<? extends Registry<?>> registryOptional = BuiltInRegistries.REGISTRY.getOptional(tagKey.registry().identifier());
        if (registryOptional.isPresent() && tagKey.isFor(registryOptional.get().key())) {
            Registry<T> registry = (Registry<T>)registryOptional.get();
            Optional<ResourceKey<T>> maybeKey = registry.getResourceKey(entry);
            if (maybeKey.isPresent()) {
                return registry.getOrThrow(maybeKey.get()).is(tagKey);
            }
        }

        return false;
    }

    private void invalidateCaches() {
        this.blockTagCache = null;
        this.blockCache = null;
        this.soundTypeCache = null;
    }

    private Map<TagKey<Block>, Float> getBlockTags() {
        if (this.blockTagCache == null) {
            this.blockTagCache = new LinkedHashMap<>();

            for (Entry<BlockDefinition, Float> entry : this.configMap.entrySet()) {
                if (entry.getKey() instanceof BlockTagDefinition def) {
                    this.blockTagCache.put(def.getBlockTag(), entry.getValue());
                }
            }
        }

        return this.blockTagCache;
    }

    private Map<Block, Float> getBlocks() {
        if (this.blockCache == null) {
            this.blockCache = new LinkedHashMap<>();

            for (Entry<BlockDefinition, Float> entry : this.configMap.entrySet()) {
                if (entry.getKey() instanceof BlockIdDefinition def) {
                    this.blockCache.put(def.getBlock(), entry.getValue());
                }
            }
        }

        return this.blockCache;
    }

    private Map<SoundType, Float> getSoundTypes() {
        if (this.soundTypeCache == null) {
            this.soundTypeCache = new LinkedHashMap<>();

            for (Entry<BlockDefinition, Float> entry : this.configMap.entrySet()) {
                if (entry.getKey() instanceof BlockSoundTypeDefinition def) {
                    this.soundTypeCache.put(def.getSoundType(), entry.getValue());
                }
            }
        }

        return this.soundTypeCache;
    }

    public BlockSoundConfigBase setBlockDefinitionValue(BlockDefinition blockDefinition, float value) {
        this.configMap.put(blockDefinition, value);
        this.invalidateCaches();
        return this;
    }

    public abstract void addDefaults(Map<BlockDefinition, Float> var1);

    public abstract Float getDefaultValue();

    protected static void putSoundType(Map<BlockDefinition, Float> map, SoundType soundType, float value) {
        map.put(new BlockSoundTypeDefinition(soundType), value);
    }
}
