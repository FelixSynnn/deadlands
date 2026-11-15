package vectorwing.farmersdelight.common.registry;

import java.util.function.Supplier;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.MenuType;
import vectorwing.farmersdelight.common.block.entity.container.CookingPotMenu;
import vectorwing.farmersdelight.refabricated.RegUtils;

public class ModMenuTypes {
    public static final Supplier<MenuType<CookingPotMenu>> COOKING_POT = RegUtils.regMenu(
        "cooking_pot", () -> new ExtendedScreenHandlerType<>(CookingPotMenu::new, BlockPos.STREAM_CODEC)
    );

    public static void touch() {
    }
}
