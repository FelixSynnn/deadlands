package vectorwing.farmersdelight.refabricated;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;

public class FDRefabricatedTags {
    private static TagKey<Biome> modBiomeTag(String path) {
        return TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("farmersdelight", path));
    }

    private static TagKey<Block> modBlockTag(String path) {
        return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("farmersdelight", path));
    }

    private static TagKey<EntityType<?>> modEntityTag(String path) {
        return TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("farmersdelight", path));
    }

    private static TagKey<Item> modItemTag(String path) {
        return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("farmersdelight", path));
    }

    private static TagKey<MobEffect> modEffectTag(String path) {
        return TagKey.create(Registries.MOB_EFFECT, Identifier.fromNamespaceAndPath("farmersdelight", path));
    }

    public static class Biomes {
        public static final TagKey<Biome> HAS_BROWN_MUSHROOM_COLONY = FDRefabricatedTags.modBiomeTag("has_brown_mushroom_colony");
        public static final TagKey<Biome> HAS_RED_MUSHROOM_COLONY = FDRefabricatedTags.modBiomeTag("has_red_mushroom_colony");
        public static final TagKey<Biome> HAS_WILD_CABBAGE = FDRefabricatedTags.modBiomeTag("has_wild_cabbage");
        public static final TagKey<Biome> HAS_WILD_BEETROOTS = FDRefabricatedTags.modBiomeTag("has_wild_beetroots");
        public static final TagKey<Biome> WILD_CARROTS_WHITELIST = FDRefabricatedTags.modBiomeTag("wild_carrots_whitelist");
        public static final TagKey<Biome> WILD_CARROTS_BLACKLIST = FDRefabricatedTags.modBiomeTag("wild_carrots_blacklist");
        public static final TagKey<Biome> WILD_ONIONS_WHITELIST = FDRefabricatedTags.modBiomeTag("wild_onions_whitelist");
        public static final TagKey<Biome> WILD_ONIONS_BLACKLIST = FDRefabricatedTags.modBiomeTag("wild_onions_blacklist");
        public static final TagKey<Biome> WILD_POTATOES_WHITELIST = FDRefabricatedTags.modBiomeTag("wild_potatoes_whitelist");
        public static final TagKey<Biome> WILD_POTATOES_BLACKLIST = FDRefabricatedTags.modBiomeTag("wild_potatoes_blacklist");
        public static final TagKey<Biome> WILD_RICE_WHITELIST = FDRefabricatedTags.modBiomeTag("wild_rice_whitelist");
        public static final TagKey<Biome> WILD_RICE_BLACKLIST = FDRefabricatedTags.modBiomeTag("wild_rice_blacklist");
        public static final TagKey<Biome> WILD_TOMATOES_WHITELIST = FDRefabricatedTags.modBiomeTag("wild_tomatoes_whitelist");
        public static final TagKey<Biome> WILD_TOMATOES_BLACKLIST = FDRefabricatedTags.modBiomeTag("wild_tomatoes_blacklist");
    }

    public static class Blocks {
        public static final TagKey<Block> KNIFE_INSTANTLY_MINES = FDRefabricatedTags.modBlockTag("knife_instantly_mines");
        public static final TagKey<Block> GROWS_MUSHROOM_COLONIES = FDRefabricatedTags.modBlockTag("grows_mushroom_colonies");
        public static final TagKey<Block> GROWS_WILD_CROPS = FDRefabricatedTags.modBlockTag("grows_wild_crops");
        public static final TagKey<Block> SURVIVES_RICH_SOIL = FDRefabricatedTags.modBlockTag("survives/rich_soil");
        public static final TagKey<Block> DOES_NOT_SURVIVE_RICH_SOIL = FDRefabricatedTags.modBlockTag("does_not_survive/rich_soil");
        public static final TagKey<Block> SURVIVES_RICH_SOIL_FARMLAND = FDRefabricatedTags.modBlockTag("survives/rich_soil_farmland");
        public static final TagKey<Block> DOES_NOT_SURVIVE_RICH_SOIL_FARMLAND = FDRefabricatedTags.modBlockTag("does_not_survive/rich_soil_farmland");
    }

    public static class EntityTypes {
        public static final TagKey<EntityType<?>> DROPS_LEATHER = FDRefabricatedTags.modEntityTag("drops_leather");
    }

    public static class Items {
        public static final TagKey<Item> FLINT_TOOL_MATERIALS = FDRefabricatedTags.modItemTag("flint_tool_materials");
    }

    public static class MobEffects {
        public static final TagKey<MobEffect> MILK_BOTTLE_IGNORED = FDRefabricatedTags.modEffectTag("ignored/milk_bottle");
        public static final TagKey<MobEffect> HOT_COCOA_IGNORED = FDRefabricatedTags.modEffectTag("ignored/hot_cocoa");
    }
}
