package com.deadlands.entity;

import com.deadlands.entity.ai.InfectedAttackGoal;
import com.deadlands.entity.ai.SlamGoal;
import com.deadlands.registry.DLSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Large, slow and very hard to stop: heavy footsteps you hear coming, crushing punches, a ground
 * slam ({@link SlamGoal}) and barricade-breaking four times faster than a Walker (it can also
 * smash planks, iron bars and iron doors).
 */
public class BruteEntity extends InfectedEntity {
	public BruteEntity(EntityType<? extends BruteEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected void addKindGoals() {
		this.goalSelector.addGoal(1, new SlamGoal(this));
	}

	@Override
	protected void addAttackGoal() {
		this.goalSelector.addGoal(3, new InfectedAttackGoal(this, chaseSpeed(), 32, 10));
	}

	@Override
	protected void onTargetSpotted(LivingEntity target) {
		playSound(DLSounds.INFECTED_BRUTE_ROAR.value(), 2.0F, 0.85F + random.nextFloat() * 0.15F);
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(DLSounds.INFECTED_BRUTE_STEP.value(), 1.0F, 0.85F + random.nextFloat() * 0.2F);
	}

	@Override
	public int getMaxHeadYRot() {
		return 45;
	}
}
