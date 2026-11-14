package com.deadlands.spawning;

import com.deadlands.entity.InfectedEntity;
import com.deadlands.entity.ai.BreachTracker;
import com.deadlands.horde.HordeDirector;
import com.deadlands.horde.MigrationSystem;
import com.deadlands.nest.NestRegistry;
import com.deadlands.noise.NoiseSystem;
import com.deadlands.persistence.DeadlandsWorldData;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Starts every server-side infected system in order and routes deaths: a survivor's kill goes on
 * their record, a horde member's death on the horde's tally, a migrant's on its group's count.
 */
public final class InfectedSystems {
	private InfectedSystems() {
	}

	public static void init() {
		InfectedManager.init();
		NoiseSystem.init();
		com.deadlands.noise.NoiseMeter.init();
		NestRegistry.init();
		BreachTracker.init();
		InfectedSpawner.init();
		HordeDirector.init();
		MigrationSystem.init();
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(entity instanceof InfectedEntity infected) || !(entity.level() instanceof ServerLevel level)) {
				return;
			}
			MinecraftServer server = level.getServer();
			if (source.getEntity() instanceof ServerPlayer player) {
				DeadlandsWorldData data = DeadlandsWorldData.get(server);
				data.player(player.getUUID()).ifPresent(record -> {
					record.addInfectedKill();
					data.setDirty();
				});
				com.deadlands.skill.Skills.award(player, com.deadlands.skill.Skill.SURVIVALIST, com.deadlands.skill.Skills.XP_KILL);
				// Killing the infected near someone's people is noticed.
				for (com.deadlands.npc.NpcEntity witness : level.getEntitiesOfClass(com.deadlands.npc.NpcEntity.class, infected.getBoundingBox().inflate(24.0))) {
					boolean horde = com.deadlands.horde.HordeDirector.active(server) != null;
					com.deadlands.npc.Reputation.change(player, witness.faction(), horde ? com.deadlands.npc.Reputation.DEFENDED_HORDE : com.deadlands.npc.Reputation.DEFENDED);
					if (horde && witness.townKey() != 0L) {
						com.deadlands.npc.Settlements.get(server).defended(witness.townKey());
					}
					break;
				}
				if (com.deadlands.registry.DLWeapons.isWeapon(player.getMainHandItem())) {
					com.deadlands.skill.Skills.award(player, com.deadlands.skill.Skill.GUNSLINGER, com.deadlands.skill.Skills.XP_WEAPON_KILL);
				}
			}
			if (infected.isHordeMember()) {
				HordeDirector.onMemberDeath(server, infected);
			}
			if (infected.migrationId() != 0) {
				MigrationSystem.onMemberDeath(server, infected);
			}
		});
	}
}
