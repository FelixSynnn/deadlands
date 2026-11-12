package vectorwing.farmersdelight.common.registry;

import java.util.function.Supplier;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import vectorwing.farmersdelight.refabricated.RegUtils;

public class ModCreativeTabs {
    public static final Supplier<CreativeModeTab> TAB_FARMERS_DELIGHT = RegUtils.regTab(
        "farmersdelight",
        () -> FabricItemGroup.builder()
            .title(Component.translatable("itemGroup.farmersdelight"))
            .icon(() -> new ItemStack(ModBlocks.STOVE.get()))
            .displayItems((parameters, output) -> ModItems.CREATIVE_TAB_ITEMS.forEach(item -> output.accept(item.get())))
            .build()
    );

    public static void touch() {
    }
}
