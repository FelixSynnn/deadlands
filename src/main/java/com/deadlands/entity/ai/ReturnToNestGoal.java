package com.deadlands.entity.ai;

import com.deadlands.entity.InfectedEntity;
import com.deadlands.nest.NestBlockEntity;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Infected released by a nest drift back to it once things have been quiet for a minute and
 * crawl back in, returning to the nest's population. A nest that was destroyed is forgotten.
 */
public class ReturnToNestGoal extends Goal {
	private static final long IDLE_BEFORE_RETURN = 1200L;
	private static final double MAX_DISTANCE = 64.0;
	private static final double ENTER_DISTANCE = 2.2;

	private final InfectedEntity mob;
	private int repath;
	private int attempts;

	public ReturnToNestGoal(InfectedEntity mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		BlockPos nest = mob.homeNest();
		if (nest == null || mob.getTarget() != null || mob.investigatePos() != null || (mob.tickCount + mob.getId()) % 40 != 0
			|| mob.idleTicks() < IDLE_BEFORE_RETURN) {
			return false;
		}
		if (!mob.level().isLoaded(nest)) {
			return false;
		}
		if (!(mob.level().getBlockEntity(nest) instanceof NestBlockEntity)) {
			mob.setHomeNest(null);
			return false;
		}
		return mob.distanceToSqr(Vec3.atCenterOf(nest)) < MAX_DISTANCE * MAX_DISTANCE;
	}

	@Override
	public void start() {
		repath = 0;
		attempts = 0;
	}

	@Override
	public boolean canContinueToUse() {
		return mob.homeNest() != null && mob.getTarget() == null && mob.investigatePos() == null && attempts < 12 && mob.isAlive();
	}

	@Override
	public void tick() {
		BlockPos nest = mob.homeNest();
		if (nest == null) {
			return;
		}
		Vec3 center = Vec3.atBottomCenterOf(nest);
		if (mob.distanceToSqr(center) < ENTER_DISTANCE * ENTER_DISTANCE) {
			BlockEntity be = mob.level().getBlockEntity(nest);
			if (be instanceof NestBlockEntity home && home.absorb(mob)) {
				return;
			}
			mob.setHomeNest(null);
			return;
		}
		if (--repath <= 0) {
			repath = adjustedTickDelay(40);
			attempts++;
			InfectedNav.moveToward(mob, center, 0.9);
		}
	}
}
