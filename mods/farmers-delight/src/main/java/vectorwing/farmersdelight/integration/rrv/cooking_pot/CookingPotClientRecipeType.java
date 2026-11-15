package vectorwing.farmersdelight.integration.rrv.cooking_pot;

import cc.cassian.rrv.api.recipe.ReliableClientRecipeType;
import cc.cassian.rrv.common.recipe.inventory.RecipeViewMenu.SlotDefinition;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import vectorwing.farmersdelight.FarmersDelight;
import vectorwing.farmersdelight.common.registry.ModItems;
import vectorwing.farmersdelight.common.utility.TextUtils;

public class CookingPotClientRecipeType implements ReliableClientRecipeType {
    public static final CookingPotClientRecipeType INSTANCE = new CookingPotClientRecipeType();

    public Component getDisplayName() {
        return TextUtils.JEI("cooking");
    }

    public Identifier getId() {
        return FarmersDelight.id("cooking");
    }

    public ItemStack getIcon() {
        return new ItemStack(ModItems.COOKING_POT.get());
    }

    public int getDisplayWidth() {
        return 132;
    }

    public int getDisplayHeight() {
        return 70;
    }

    public Identifier getGuiTexture() {
        return FarmersDelight.id("textures/gui/eiv/cooking_pot.png");
    }

    public int getSlotCount() {
        return 8;
    }

    public void placeSlots(SlotDefinition slotDefinition) {
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 3; x++) {
                slotDefinition.addItemSlot(x + y * 3, 9 + x * 18, 7 + y * 18);
            }
        }

        slotDefinition.addItemSlot(6, 103, 45);
        slotDefinition.addItemSlot(7, 71, 45);
    }

    public List<ItemStack> getCraftReferences() {
        return List.of(new ItemStack(ModItems.COOKING_POT.get()));
    }
}
