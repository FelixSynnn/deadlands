package com.deadlands.client.mixin;

import com.deadlands.client.firstperson.FirstPersonMotion;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Phase 4: wraps each first-person hand in {@link FirstPersonMotion}'s pose (pushed at the start, popped at the end). */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
	@Inject(method = "renderArmWithItem", at = @At("HEAD"))
	private void deadlands$motionStart(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand, float swing, ItemStack stack,
		float equip, PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		pose.pushPose();
		FirstPersonMotion.apply(pose, hand, stack, swing, partialTick);
	}

	@Inject(method = "renderArmWithItem", at = @At("RETURN"))
	private void deadlands$motionEnd(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand, float swing, ItemStack stack,
		float equip, PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		pose.popPose();
	}
}
