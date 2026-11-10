package com.deadlands.entity;

import com.deadlands.registry.DLSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Low to the ground (it fits through one-block gaps and hides in rubble and collapsed rooms),
 * slow, and easy to miss in tall grass. It lunges at legs; a bite slows you down.
 */
public class CrawlerEntity extends InfectedEntity {
	public CrawlerEntity(EntityType<? extends CrawlerEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected void addKindGoals() {
		this.goalSelector.addGoal(1, new LeapAtTargetGoal(this, 0.25F));
	}

	@Override
	protected void onHit(ServerLevel level, LivingEntity target) {
		target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 0), this);
	}

	/** Dragging, not walking. */
	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		if (random.nextInt(3) == 0) {
			this.playSound(DLSounds.INFECTED_SCRATCH.value(), 0.25F, 0.7F + random.nextFloat() * 0.2F);
		}
	}
}
