package vectorwing.farmersdelight.integration.rrv.decomposition;

import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu.SlotDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import vectorwing.farmersdelight.FarmersDelight;
import vectorwing.farmersdelight.common.registry.ModItems;
import vectorwing.farmersdelight.common.utility.TextUtils;

public class DecompositionClientRecipeType implements ReliableClientRecipeType {
    protected static final DecompositionClientRecipeType INSTANCE = new DecompositionClientRecipeType();

    public Component getDisplayName() {
        return TextUtils.JEI("decomposition");
    }

    public int getDisplayWidth() {
        return 118;
    }

    public int getDisplayHeight() {
        return 71;
    }

    public Identifier getGuiTexture() {
        return FarmersDelight.id("textures/gui/eiv/decomposition.png");
    }

    public int getSlotCount() {
        return 3;
    }

    public void placeSlots(SlotDefinition slotDefinition) {
        slotDefinition.addItemSlot(0, 9, 26);
        slotDefinition.addItemSlot(1, 93, 26);
        slotDefinition.addItemSlot(2, 64, 52);
    }

    public Identifier getId() {
        return FarmersDelight.id("decomposition");
    }

    public ItemStack getIcon() {
        return ModItems.ORGANIC_COMPOST.get().getDefaultInstance();
    }
}
