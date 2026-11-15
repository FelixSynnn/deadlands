package vectorwing.farmersdelight.common.crafting;

import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;
import net.minecraft.core.NonNullList;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import vectorwing.farmersdelight.common.registry.ModItems;
import vectorwing.farmersdelight.common.registry.ModRecipeSerializers;

public class DoughRecipe extends CustomRecipe {
    public static final DoughRecipe INSTANCE = new DoughRecipe(CraftingBookCategory.MISC);
    public static final MapCodec<DoughRecipe> MAP_CODEC = MapCodec.unit(INSTANCE);
    public static final StreamCodec<RegistryFriendlyByteBuf, DoughRecipe> STREAM_CODEC = StreamCodec.unit(INSTANCE);
    public static final RecipeSerializer<DoughRecipe> SERIALIZER = new RecipeSerializer<DoughRecipe>() {
        @Override
        public MapCodec<DoughRecipe> codec() {
            return DoughRecipe.MAP_CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, DoughRecipe> streamCodec() {
            return DoughRecipe.STREAM_CODEC;
        }
    };

    public DoughRecipe(CraftingBookCategory craftingBookCategory) {
        super(craftingBookCategory);
    }

    public boolean matches(CraftingInput container, Level level) {
        ItemStack wheatStack = ItemStack.EMPTY;
        ItemStack waterStack = ItemStack.EMPTY;

        for (int index = 0; index < container.size(); index++) {
            ItemStack selectedStack = container.getItem(index);
            if (!selectedStack.isEmpty()) {
                if (selectedStack.is(Items.WHEAT)) {
                    if (!wheatStack.isEmpty()) {
                        return false;
                    }

                    wheatStack = selectedStack;
                } else {
                    if (!selectedStack.is(ConventionalItemTags.WATER_BUCKETS)) {
                        return false;
                    }

                    waterStack = selectedStack;
                }
            }
        }

        return !wheatStack.isEmpty() && !waterStack.isEmpty();
    }

    public ItemStack assemble(CraftingInput input, Provider provider) {
        return new ItemStack(ModItems.WHEAT_DOUGH.get());
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput container) {
        NonNullList<ItemStack> remainders = NonNullList.withSize(container.size(), ItemStack.EMPTY);

        for (int index = 0; index < remainders.size(); index++) {
            ItemStack selectedStack = container.getItem(index);
            if (selectedStack.is(ConventionalItemTags.WATER_BUCKETS)) {
                remainders.set(index, selectedStack.copy());
            }
        }

        return remainders;
    }

    @Override
    public RecipeSerializer<DoughRecipe> getSerializer() {
        return ModRecipeSerializers.DOUGH.get();
    }
}
