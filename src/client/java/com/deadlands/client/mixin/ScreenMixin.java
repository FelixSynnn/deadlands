package com.deadlands.client.mixin;

import com.deadlands.client.ui.TitleBackdrop;
import com.deadlands.config.ConfigManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pre-game menus (world select, options...) show the DEADLANDS backdrop instead of the panorama. */
@Mixin(Screen.class)
public abstract class ScreenMixin {
	@Inject(method = "renderPanorama", at = @At("HEAD"), cancellable = true)
	private void deadlands$renderBackdrop(GuiGraphics graphics, float partialTick, CallbackInfo ci) {
		if (ConfigManager.get().ui.menuBackdrop) {
			Screen self = (Screen) (Object) this;
			TitleBackdrop.render(graphics, self.width, self.height, 0.0F, 0.0F, false);
			ci.cancel();
		}
	}
}
