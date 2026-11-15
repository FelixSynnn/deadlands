package com.deadlands.player;

import com.deadlands.config.ConfigManager;
import com.deadlands.entity.AsesinoEntity;
import com.deadlands.entity.GuestEntity;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * No sleep while a killer is near ({@code horror.noSleepRadius}, 48 blocks): with the Aberrant or the Guest
 * within that distance a bed refuses you ("You can't sleep. Something is out there."), and anyone already
 * asleep is woken the moment one comes that close. Walls don't help: it knows you're there. And with
 * {@code horror.noSleepAtNight} nobody sleeps at night at all: the night has to be lived through (the bed still sets
 * your spawn point).
 */
public final class RestlessNights {
	private static final Component REFUSED = Component.translatable("message.deadlands.sleep.killer_near");
	private static final Component WOKEN = Component.translatable("message.deadlands.sleep.woken");
	private static final Component NIGHT = Component.translatable("message.deadlands.sleep.night");

	private RestlessNights() {
	}

	public static void init() {
		EntitySleepEvents.ALLOW_SLEEPING.register((player, pos) -> {
			if (killerNear(player)) {
				return new Player.BedSleepingProblem(REFUSED.copy().withStyle(ChatFormatting.RED));
			}
			if (ConfigManager.get().horror.noSleepAtNight && player.level().isDarkOutside()) {
				// No sleeping the night away: it has to be lived through. The bed still keeps your spawn point.
				if (player instanceof ServerPlayer server) {
					server.setRespawnPosition(new ServerPlayer.RespawnConfig(
						net.minecraft.world.level.storage.LevelData.RespawnData.of(server.level().dimension(), pos, server.getYRot(), server.getXRot()), false), true);
				}
				return new Player.BedSleepingProblem(NIGHT.copy().withStyle(ChatFormatting.GRAY));
			}
			return null;
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 != 11) {
				return;
			}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (player.isSleeping() && killerNear(player)) {
					player.stopSleepInBed(true, true);
					player.displayClientMessage(WOKEN.copy().withStyle(ChatFormatting.RED), true);
				}
			}
		});
	}

	/** Whether the Aberrant or the Guest is within the no-sleep radius of this survivor. */
	public static boolean killerNear(Player player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return false;
		}
		double radius = ConfigManager.get().horror.noSleepRadius;
		if (radius <= 0.0) {
			return false;
		}
		AABB box = player.getBoundingBox().inflate(radius);
		return !level.getEntitiesOfClass(Entity.class, box,
			e -> (e instanceof AsesinoEntity || e instanceof GuestEntity) && e.isAlive() && e.distanceToSqr(player) <= radius * radius).isEmpty();
	}
}
