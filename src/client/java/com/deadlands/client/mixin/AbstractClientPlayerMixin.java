package com.deadlands.client.mixin;

import com.deadlands.client.ClientCharacters;
import com.deadlands.client.render.NpcRenderer;
import com.deadlands.npc.NpcCharacter;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Phase 6: a survivor who has chosen a character (/deadlands character) is drawn in that character's skin, the same one
 * the story's NPC uses (or the owner's file in config/deadlands/skins). Their own cape and elytra stay.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
	@Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
	private void deadlands$characterSkin(CallbackInfoReturnable<PlayerSkin> cir) {
		NpcCharacter character = ClientCharacters.of(((AbstractClientPlayer) (Object) this).getUUID());
		if (character == null) {
			return;
		}
		PlayerSkin own = cir.getReturnValue();
		Identifier texture = NpcRenderer.characterTexture(character);
		cir.setReturnValue(new PlayerSkin(new ClientAsset.ResourceTexture(texture, texture), own.cape(), own.elytra(),
			NpcRenderer.characterSlim(character) ? PlayerModelType.SLIM : PlayerModelType.WIDE, own.secure()));
	}
}
