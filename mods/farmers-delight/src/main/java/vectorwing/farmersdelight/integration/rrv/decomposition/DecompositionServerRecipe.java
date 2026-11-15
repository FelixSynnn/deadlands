package vectorwing.farmersdelight.integration.rrv.decomposition;

import cc.cassian.rrv.api.recipe.ReliableServerRecipe;
import cc.cassian.rrv.api.recipe.ReliableServerRecipeType;
import net.minecraft.nbt.CompoundTag;
import vectorwing.farmersdelight.FarmersDelight;

public class DecompositionServerRecipe implements ReliableServerRecipe {
    public static final ReliableServerRecipeType<DecompositionServerRecipe> TYPE = ReliableServerRecipeType.register(
        FarmersDelight.id("decomposition"), () -> new DecompositionServerRecipe()
    );

    public void writeToTag(CompoundTag tag) {
    }

    public void loadFromTag(CompoundTag tag) {
    }

    public ReliableServerRecipeType<? extends DecompositionServerRecipe> getRecipeType() {
        return TYPE;
    }
}
