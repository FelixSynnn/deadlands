package com.deadlands.player;

import com.deadlands.config.ConfigManager;
import com.deadlands.config.DeadlandsConfig;
import com.deadlands.entity.AsesinoEntity;
import com.deadlands.entity.GuestEntity;
import com.deadlands.entity.InfectedEntity;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.phys.AABB;

/**
 * Safe respawn ({@code survival.safeRespawn}): when a survivor comes back after dying, every hostile DEADLANDS
 * creature (the infected, the Aberrant, the Guest) and every vanilla zombie within
 * {@code survival.safeRespawnRadius} blocks of where they come back (bed, anchor or world spawn) is removed
 * in a puff of smoke, before they can act. Removed, not killed: no loot, no experience, so dying can't be
 * used to farm drops. Named creatures are left alone, and so are bosses (an event's boss stays) and
 * anything passive or tamed.
 */
public final class SafeRespawn {
	private SafeRespawn() {
	}

	public static void init() {
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (!alive) {
				clear(newPlayer);
			}
		});
	}

	/** Whether this creature is cleared from around a respawn point. */
	static boolean clears(Entity entity) {
		if (!entity.isAlive() || entity.hasCustomName()) {
			return false;
		}
		return entity instanceof InfectedEntity || entity instanceof AsesinoEntity || entity instanceof GuestEntity
			|| entity instanceof Zombie && !(entity instanceof ZombifiedPiglin);
	}

	private static void clear(ServerPlayer player) {
		DeadlandsConfig.Survival config = ConfigManager.get().survival;
		if (!config.safeRespawn) {
			return;
		}
		ServerLevel level = player.level();
		double r = config.safeRespawnRadius;
		for (Entity entity : level.getEntities((Entity) null, new AABB(player.blockPosition()).inflate(r), SafeRespawn::clears)) {
			if (entity.distanceToSqr(player) > r * r) {
				continue;
			}
			level.sendParticles(ParticleTypes.POOF, entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
				12, entity.getBbWidth() * 0.5, entity.getBbHeight() * 0.4, entity.getBbWidth() * 0.5, 0.02);
			entity.discard();
		}
	}
}
