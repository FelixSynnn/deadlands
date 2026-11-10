package com.deadlands.entity;

import com.deadlands.entity.ai.SwellGoal;
import com.deadlands.registry.DLSounds;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Swollen with gas. Next to a survivor it swells and bursts ({@link SwellGoal}); killed any
 * other way it bursts too. The burst leaves a lingering cloud that poisons and sickens anything
 * that stays in it, the infected included. Shoot it from a distance.
 */
public class BloaterEntity extends InfectedEntity {
	private static final int CLOUD_COLOUR = 0x8A9A3C;

	private boolean burst;

	public BloaterEntity(EntityType<? extends BloaterEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected void addKindGoals() {
		this.goalSelector.addGoal(1, new SwellGoal(this));
	}

	/** Burst now (from swelling up). Kills the Bloater, which spawns the cloud in {@link #die}. */
	public void burst(ServerLevel level) {
		if (isAlive()) {
			hurtServer(level, damageSources().generic(), Float.MAX_VALUE);
		}
	}

	@Override
	public void die(DamageSource source) {
		if (!burst && level() instanceof ServerLevel level) {
			burst = true;
			AreaEffectCloud cloud = new AreaEffectCloud(level, getX(), getY() + 0.4, getZ());
			cloud.setRadius(3.5F);
			cloud.setRadiusOnUse(-0.3F);
			cloud.setWaitTime(5);
			cloud.setDuration(160);
			cloud.setRadiusPerTick(-cloud.getRadius() / cloud.getDuration());
			cloud.setCustomParticle(new DustParticleOptions(CLOUD_COLOUR, 1.4F));
			cloud.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0));
			cloud.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 160, 0));
			cloud.setOwner(this);
			level.addFreshEntity(cloud);
			level.sendParticles(new DustParticleOptions(CLOUD_COLOUR, 2.0F), getX(), getY() + 1.0, getZ(), 60, 1.2, 0.8, 1.2, 0.05);
			playSound(DLSounds.INFECTED_BLOATER_BURST.value(), 2.0F, 0.9F + random.nextFloat() * 0.2F);
		}
		super.die(source);
	}
}
