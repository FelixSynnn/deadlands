package vectorwing.farmersdelight.common.tag;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public class CommonTags {
    private static TagKey<Block> commonBlockTag(String path) {
        return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("c", path));
    }

    private static TagKey<Item> commonItemTag(String path) {
        return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", path));
    }

    public static class Blocks {
        public static final TagKey<Block> MINEABLE_WITH_KNIFE = CommonTags.commonBlockTag("mineable/knife");
        public static final TagKey<Block> STORAGE_BLOCKS_CARROT = CommonTags.commonBlockTag("storage_blocks/carrot");
        public static final TagKey<Block> STORAGE_BLOCKS_POTATO = CommonTags.commonBlockTag("storage_blocks/potato");
        public static final TagKey<Block> STORAGE_BLOCKS_BEETROOT = CommonTags.commonBlockTag("storage_blocks/beetroot");
        public static final TagKey<Block> STORAGE_BLOCKS_CABBAGE = CommonTags.commonBlockTag("storage_blocks/cabbage");
        public static final TagKey<Block> STORAGE_BLOCKS_TOMATO = CommonTags.commonBlockTag("storage_blocks/tomato");
        public static final TagKey<Block> STORAGE_BLOCKS_ONION = CommonTags.commonBlockTag("storage_blocks/onion");
        public static final TagKey<Block> STORAGE_BLOCKS_RICE = CommonTags.commonBlockTag("storage_blocks/rice");
        public static final TagKey<Block> STORAGE_BLOCKS_RICE_PANICLE = CommonTags.commonBlockTag("storage_blocks/rice_panicle");
        public static final TagKey<Block> STORAGE_BLOCKS_STRAW = CommonTags.commonBlockTag("storage_blocks/straw");
    }

    public static class Items {
        public static final TagKey<Item> CROPS_CABBAGE = CommonTags.commonItemTag("crops/cabbage");
        public static final TagKey<Item> CROPS_TOMATO = CommonTags.commonItemTag("crops/tomato");
        public static final TagKey<Item> CROPS_ONION = CommonTags.commonItemTag("crops/onion");
        public static final TagKey<Item> CROPS_RICE = CommonTags.commonItemTag("crops/rice");
        public static final TagKey<Item> CROPS_GRAIN = CommonTags.commonItemTag("crops/grain");
        public static final TagKey<Item> FOODS_CABBAGE = CommonTags.commonItemTag("foods/cabbage");
        public static final TagKey<Item> FOODS_TOMATO = CommonTags.commonItemTag("foods/tomato");
        public static final TagKey<Item> FOODS_ONION = CommonTags.commonItemTag("foods/onion");
        public static final TagKey<Item> FOODS_LEAFY_GREEN = CommonTags.commonItemTag("foods/leafy_green");
        public static final TagKey<Item> FOODS_DOUGH = CommonTags.commonItemTag("foods/dough");
        public static final TagKey<Item> FOODS_DOUGH_WHEAT = CommonTags.commonItemTag("foods/dough/wheat");
        public static final TagKey<Item> FOODS_PASTA = CommonTags.commonItemTag("foods/pasta");
        public static final TagKey<Item> FOODS_RAW_BACON = CommonTags.commonItemTag("foods/raw_bacon");
        public static final TagKey<Item> FOODS_RAW_BEEF = CommonTags.commonItemTag("foods/raw_beef");
        public static final TagKey<Item> FOODS_RAW_CHICKEN = CommonTags.commonItemTag("foods/raw_chicken");
        public static final TagKey<Item> FOODS_RAW_PORK = CommonTags.commonItemTag("foods/raw_pork");
        public static final TagKey<Item> FOODS_RAW_MUTTON = CommonTags.commonItemTag("foods/raw_mutton");
        public static final TagKey<Item> FOODS_SAFE_RAW_FISH = CommonTags.commonItemTag("foods/safe_raw_fish");
        public static final TagKey<Item> FOODS_RAW_COD = CommonTags.commonItemTag("foods/raw_cod");
        public static final TagKey<Item> FOODS_RAW_SALMON = CommonTags.commonItemTag("foods/raw_salmon");
        public static final TagKey<Item> FOODS_COOKED_BACON = CommonTags.commonItemTag("foods/cooked_bacon");
        public static final TagKey<Item> FOODS_COOKED_BEEF = CommonTags.commonItemTag("foods/cooked_beef");
        public static final TagKey<Item> FOODS_COOKED_CHICKEN = CommonTags.commonItemTag("foods/cooked_chicken");
        public static final TagKey<Item> FOODS_COOKED_PORK = CommonTags.commonItemTag("foods/cooked_pork");
        public static final TagKey<Item> FOODS_COOKED_MUTTON = CommonTags.commonItemTag("foods/cooked_mutton");
        public static final TagKey<Item> FOODS_COOKED_EGG = CommonTags.commonItemTag("foods/cooked_egg");
        public static final TagKey<Item> FOODS_COOKED_COD = CommonTags.commonItemTag("foods/cooked_cod");
        public static final TagKey<Item> FOODS_COOKED_SALMON = CommonTags.commonItemTag("foods/cooked_salmon");
        public static final TagKey<Item> TOOLS_KNIFE = CommonTags.commonItemTag("tools/knife");
        public static final TagKey<Item> STORAGE_BLOCKS_CARROT = CommonTags.commonItemTag("storage_blocks/carrot");
        public static final TagKey<Item> STORAGE_BLOCKS_POTATO = CommonTags.commonItemTag("storage_blocks/potato");
        public static final TagKey<Item> STORAGE_BLOCKS_BEETROOT = CommonTags.commonItemTag("storage_blocks/beetroot");
        public static final TagKey<Item> STORAGE_BLOCKS_CABBAGE = CommonTags.commonItemTag("storage_blocks/cabbage");
        public static final TagKey<Item> STORAGE_BLOCKS_TOMATO = CommonTags.commonItemTag("storage_blocks/tomato");
        public static final TagKey<Item> STORAGE_BLOCKS_ONION = CommonTags.commonItemTag("storage_blocks/onion");
        public static final TagKey<Item> STORAGE_BLOCKS_RICE = CommonTags.commonItemTag("storage_blocks/rice");
        public static final TagKey<Item> STORAGE_BLOCKS_RICE_PANICLE = CommonTags.commonItemTag("storage_blocks/rice_panicle");
        public static final TagKey<Item> STORAGE_BLOCKS_STRAW = CommonTags.commonItemTag("storage_blocks/straw");
    }
}
