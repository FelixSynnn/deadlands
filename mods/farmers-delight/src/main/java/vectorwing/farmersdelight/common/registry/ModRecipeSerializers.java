package vectorwing.farmersdelight.common.registry;

import com.mojang.serialization.MapCodec;
import java.util.function.Supplier;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;
import vectorwing.farmersdelight.common.crafting.CuttingBoardRecipe;
import vectorwing.farmersdelight.common.crafting.DoughRecipe;
import vectorwing.farmersdelight.common.crafting.FoodServingRecipe;
import vectorwing.farmersdelight.refabricated.RegUtils;

public class ModRecipeSerializers {
    public static final Supplier<RecipeSerializer<CookingPotRecipe>> COOKING = RegUtils.regRecipeSerializer(
        "cooking", () -> new RecipeSerializer<CookingPotRecipe>() {
            @Override
            public MapCodec<CookingPotRecipe> codec() {
                return CookingPotRecipe.Serializer.codec();
            }

            @Override
            public StreamCodec<RegistryFriendlyByteBuf, CookingPotRecipe> streamCodec() {
                return CookingPotRecipe.Serializer.streamCodec();
            }
        }
    );
    public static final Supplier<RecipeSerializer<CuttingBoardRecipe>> CUTTING = RegUtils.regRecipeSerializer(
        "cutting", () -> new RecipeSerializer<CuttingBoardRecipe>() {
            @Override
            public MapCodec<CuttingBoardRecipe> codec() {
                return CuttingBoardRecipe.Serializer.codec();
            }

            @Override
            public StreamCodec<RegistryFriendlyByteBuf, CuttingBoardRecipe> streamCodec() {
                return CuttingBoardRecipe.Serializer.streamCodec();
            }
        }
    );
    public static final Supplier<RecipeSerializer<FoodServingRecipe>> FOOD_SERVING = RegUtils.regRecipeSerializer(
        "food_serving", () -> FoodServingRecipe.SERIALIZER
    );
    public static final Supplier<RecipeSerializer<DoughRecipe>> DOUGH = RegUtils.regRecipeSerializer("dough", () -> DoughRecipe.SERIALIZER);

    public static void touch() {
    }
}
