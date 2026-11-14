package vectorwing.farmersdelight.integration.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.network.chat.Component;
import vectorwing.farmersdelight.common.Configuration;
import vectorwing.farmersdelight.common.registry.ModItems;
import vectorwing.farmersdelight.refabricated.mlconfigs.fabric.FabricConfigListScreen;

public class ModMenuCompat implements ModMenuApi {
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new FabricConfigListScreen(
            "farmersdelight",
            ModItems.STOVE.get().getDefaultInstance(),
            Component.translatable("farmersdelight"),
            null,
            parent,
            Configuration.CLIENT_CONFIG,
            Configuration.COMMON_CONFIG
        );
    }
}
