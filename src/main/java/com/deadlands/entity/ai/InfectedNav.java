package com.deadlands.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Movement helpers shared by the infected goals. */
public final class InfectedNav {
	/** Paths longer than this are walked in steps (the pathfinder's reach is the follow range). */
	private static final double DIRECT_RANGE = 22.0;

	private InfectedNav() {
	}

	/**
	 * Walk toward a point at any distance: directly when it is near, otherwise to a reachable
	 * spot about 20 blocks along the way. Call again every couple of seconds to keep going.
	 */
	public static boolean moveToward(PathfinderMob mob, Vec3 target, double speed) {
		PathNavigation navigation = mob.getNavigation();
		if (mob.distanceToSqr(target) < DIRECT_RANGE * DIRECT_RANGE) {
			return navigation.moveTo(target.x, target.y, target.z, speed);
		}
		Vec3 step = DefaultRandomPos.getPosTowards(mob, 20, 7, target, Math.PI / 3.0);
		if (step == null) {
			step = LandRandomPos.getPosTowards(mob, 16, 7, target);
		}
		if (step != null) {
			return navigation.moveTo(step.x, step.y, step.z, speed);
		}
		return navigation.moveTo(target.x, target.y, target.z, speed);
	}

	/** A spot two blocks tall with a solid floor and no fluid. */
	public static boolean isStandable(Level level, BlockPos pos) {
		if (!level.isLoaded(pos) || !level.getFluidState(pos).isEmpty()) {
			return false;
		}
		BlockPos below = pos.below();
		return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
			&& level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
			&& level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
	}

	/** A random standable spot within the given ranges that has a roof overhead, or null. */
	public static @Nullable BlockPos findShelter(PathfinderMob mob, int horizontal, int vertical, int tries) {
		Level level = mob.level();
		BlockPos origin = mob.blockPosition();
		for (int i = 0; i < tries; i++) {
			BlockPos candidate = origin.offset(
				mob.getRandom().nextInt(horizontal * 2 + 1) - horizontal,
				mob.getRandom().nextInt(vertical * 2 + 1) - vertical,
				mob.getRandom().nextInt(horizontal * 2 + 1) - horizontal);
			if (isStandable(level, candidate) && !level.canSeeSky(candidate)) {
				return candidate;
			}
		}
		return null;
	}
}
