package vectorwing.farmersdelight.refabricated;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import net.minecraft.tags.TagLoader.EntryWithSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import vectorwing.farmersdelight.common.tag.ModTags;
import vectorwing.farmersdelight.refabricated.duck.TagLoaderSpecificResourceDuck;

public class RefabricatedEarlyTagUtils {
    private static ResourceManager resourceManager;
    private static Collection<Holder<Block>> earlyDropsCakeTag;
    private static Collection<Holder<EntityType<?>>> earlyDropsLeatherTag;

    public static boolean isCandleDropsCakeSliceTag(Holder<Block> block, HolderLookup<Block> lookup) {
        if (earlyDropsCakeTag == null) {
            TagLoader<Holder<Block>> loader = new TagLoader<>((rl, bl) -> lookup.get(ResourceKey.create(Registries.BLOCK, rl)), "tags/block");
            Map<Identifier, List<Holder<Block>>> loadedTag = loadTagEarly(loader, ModTags.Blocks.DROPS_CAKE_SLICE);
            earlyDropsCakeTag = loadedTag.get(ModTags.Blocks.DROPS_CAKE_SLICE.location());
            if (earlyDropsCakeTag == null) {
                earlyDropsCakeTag = Collections.emptySet();
            }
        }

        return earlyDropsCakeTag.contains(block);
    }

    public static boolean isDropsLeatherTag(Holder<EntityType<?>> entityType, HolderLookup<EntityType<?>> lookup) {
        if (earlyDropsLeatherTag == null) {
            TagLoader<Holder<EntityType<?>>> loader = new TagLoader<>(
                (id, required) -> lookup.get(ResourceKey.create(Registries.ENTITY_TYPE, id)), "tags/entity_type"
            );
            Map<Identifier, List<Holder<EntityType<?>>>> loadedTag = loadTagEarly(loader, FDRefabricatedTags.EntityTypes.DROPS_LEATHER);
            earlyDropsLeatherTag = loadedTag.get(FDRefabricatedTags.EntityTypes.DROPS_LEATHER.location());
            if (earlyDropsCakeTag == null) {
                earlyDropsCakeTag = Collections.emptySet();
            }
        }

        return earlyDropsLeatherTag.contains(entityType);
    }

    private static <T> Map<Identifier, List<Holder<T>>> loadTagEarly(TagLoader<Holder<T>> tagLoader, TagKey<T> tagKey) {
        String tagRegistryLocation = (
                tagKey.registry().identifier().getNamespace().equals("minecraft") ? "" : tagKey.registry().identifier().getNamespace() + "/"
            )
            + tagKey.registry().identifier().getPath();
        FileToIdConverter converter = FileToIdConverter.json("tags/" + tagRegistryLocation);
        Identifier tagPath = converter.idToFile(tagKey.location());
        ((TagLoaderSpecificResourceDuck)tagLoader).fdrf$setSingleTag(tagPath);
        Map<Identifier, List<EntryWithSource>> loaded = tagLoader.load(resourceManager);
        return tagLoader.build(loaded);
    }

    public static void setLootTableResourceManager(ResourceManager manager) {
        resourceManager = manager;
    }

    public static void resetEarlyTagCollections() {
        resourceManager = null;
        earlyDropsCakeTag = null;
        earlyDropsLeatherTag = null;
    }
}
