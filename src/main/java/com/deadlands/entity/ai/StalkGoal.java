package com.deadlands.entity.ai;

import com.deadlands.entity.StalkerEntity;
import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * While the survivor it hunts is looking straight at it, a Stalker freezes and stares back; the
 * moment they look away it closes in. Up close (5 blocks) it stops caring and attacks.
 */
public class StalkGoal extends Goal {
	private static final double WATCHED_DOT = 0.94;
	private static final double MAX_RANGE = 28.0;
	private static final double CLOSE_RANGE = 5.0;

	private final StalkerEntity mob;

	public StalkGoal(StalkerEntity mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP));
	}

	@Override
	public boolean canUse() {
		return isWatched();
	}

	@Override
	public boolean canContinueToUse() {
		return isWatched();
	}

	private boolean isWatched() {
		LivingEntity target = mob.getTarget();
		if (!(target instanceof Player player) || !target.isAlive()) {
			return false;
		}
		double distanceSqr = mob.distanceToSqr(player);
		if (distanceSqr < CLOSE_RANGE * CLOSE_RANGE || distanceSqr > MAX_RANGE * MAX_RANGE) {
			return false;
		}
		Vec3 look = player.getViewVector(1.0F).normalize();
		Vec3 toMe = mob.getEyePosition().subtract(player.getEyePosition()).normalize();
		return look.dot(toMe) > WATCHED_DOT && player.hasLineOfSight(mob);
	}

	@Override
	public void start() {
		mob.getNavigation().stop();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		LivingEntity target = mob.getTarget();
		if (target != null) {
			mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
		}
	}
}
