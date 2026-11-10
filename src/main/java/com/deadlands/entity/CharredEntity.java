package com.deadlands.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * Burned in the fires that swept the forests and still smouldering: immune to fire and lava,
 * cracks glowing with embers, and its touch sets survivors alight. Found mostly in burned forests.
 */
public class CharredEntity extends InfectedEntity {
	public CharredEntity(EntityType<? extends CharredEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected void onHit(ServerLevel level, LivingEntity target) {
		target.igniteForSeconds(4.0F);
	}

	@Override
	public double chaseSpeed() {
		return 1.05;
	}
}
