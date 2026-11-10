package com.deadlands.entity;

import com.deadlands.entity.ai.InfectedAttackGoal;
import com.deadlands.entity.ai.PounceGoal;
import com.deadlands.entity.ai.StalkGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A rare night hunter: almost silent, climbs walls, freezes while watched ({@link StalkGoal})
 * and leaps at survivors ({@link PounceGoal}). Avoids daylight strongly.
 */
public class StalkerEntity extends InfectedEntity {
	public StalkerEntity(EntityType<? extends StalkerEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		return new WallClimberNavigation(this, level);
	}

	@Override
	protected void addKindGoals() {
		this.goalSelector.addGoal(1, new PounceGoal(this));
		this.goalSelector.addGoal(2, new StalkGoal(this));
	}

	@Override
	protected void addAttackGoal() {
		this.goalSelector.addGoal(3, new InfectedAttackGoal(this, 1.15, 16, 5));
	}

	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide()) {
			setFlag(FLAG_CLIMBING, horizontalCollision);
		}
	}

	@Override
	public boolean onClimbable() {
		return hasFlag(FLAG_CLIMBING);
	}

	/** Stalkers make no footstep sound. */
	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
	}
}
