package com.deadlands.survival;

import com.deadlands.Deadlands;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.world.entity.player.Player;

/**
 * Where survival state lives: a Fabric data attachment on the player entity. It is saved with the
 * player and deliberately not copied on death, so a respawned survivor starts healthy.
 */
public final class SurvivalAttachments {
	public static final AttachmentType<SurvivalData> SURVIVAL = AttachmentRegistry.create(Deadlands.id("survival"),
		builder -> builder.persistent(SurvivalData.CODEC).initializer(SurvivalData::new));

	private SurvivalAttachments() {
	}

	public static SurvivalData get(Player player) {
		return player.getAttachedOrCreate(SURVIVAL);
	}

	public static void init() {
		// Class loading registers the attachment.
	}
}
