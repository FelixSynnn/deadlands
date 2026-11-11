package com.deadlands.client.mixin;

import com.deadlands.client.ui.DeadlandsTitleScreen;
import com.deadlands.config.ConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Swaps Minecraft's title screen for the DEADLANDS menu, and its inventory, crafting and chest
 * screens for the DEADLANDS ones (each unless disabled in settings). The menus behind the screens
 * are untouched, so nothing about how items move changes.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	/** Minecraft's game music never plays in a world: DEADLANDS music is chosen by {@code MusicDirector}. */
	@org.spongepowered.asm.mixin.injection.Inject(method = "getSituationalMusic", at = @At("HEAD"), cancellable = true)
	private void deadlands$noVanillaMusic(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<net.minecraft.sounds.Music> cir) {
		if (((Minecraft) (Object) this).level != null) {
			cir.setReturnValue(null);
		}
	}

	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen deadlands$useDeadlandsTitleScreen(Screen screen) {
		// Vanilla's advancements screen becomes the DEADLANDS Achievements screen.
		if (screen instanceof net.minecraft.client.gui.screens.advancements.AdvancementsScreen) {
			return new com.deadlands.client.achievement.DLAchievementsScreen(((Minecraft) (Object) this).screen);
		}
		if (screen instanceof TitleScreen && !(screen instanceof DeadlandsTitleScreen) && ConfigManager.get().ui.replaceTitleScreen) {
			return new DeadlandsTitleScreen();
		}
		if (!ConfigManager.get().ui.inventoryScreens) {
			return screen;
		}
		Minecraft self = (Minecraft) (Object) this;
		if (self.player != null && self.player.isCreative()) {
			return screen; // creative mode keeps Minecraft's normal menus
		}
		if (screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen && self.player != null) {
			return new com.deadlands.client.ui.DLInventoryScreen(self.player);
		}
		if (screen instanceof net.minecraft.client.gui.screens.inventory.CraftingScreen crafting && self.player != null) {
			return new com.deadlands.client.ui.DLCraftingScreen(crafting.getMenu(), self.player.getInventory(), crafting.getTitle());
		}
		if (screen instanceof net.minecraft.client.gui.screens.inventory.ContainerScreen chest && self.player != null) {
			return new com.deadlands.client.ui.DLChestScreen(chest.getMenu(), self.player.getInventory(), chest.getTitle());
		}
		return screen;
	}
}
