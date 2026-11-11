package com.deadlands.client.mixin;

import com.deadlands.client.achievement.DLAchievementToast;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.client.multiplayer.ClientAdvancements;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Earned achievements toast in the DEADLANDS style ({@link DLAchievementToast}) instead of vanilla's. */
@Mixin(ClientAdvancements.class)
public abstract class ClientAdvancementsMixin {
	@WrapOperation(method = "update", at = @At(value = "NEW", target = "net/minecraft/client/gui/components/toasts/AdvancementToast"))
	private AdvancementToast deadlands$achievementToast(AdvancementHolder holder, Operation<AdvancementToast> original) {
		return new DLAchievementToast(holder);
	}
}
