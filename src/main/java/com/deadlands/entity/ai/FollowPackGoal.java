package com.deadlands.entity.ai;

import com.deadlands.entity.InfectedEntity;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jspecify.annotations.Nullable;

/**
 * Infected that spawned together move as a group: the pack member with the lowest entity id
 * nearby leads, the rest keep within a few blocks of it while idle. If the leader dies, the next
 * one takes over automatically.
 */
public class FollowPackGoal extends Goal {
	private static final double PACK_RANGE = 24.0;
	private static final double LOOSE_DISTANCE = 7.0;
	private static final double CLOSE_DISTANCE = 4.0;

	private final InfectedEntity mob;
	private @Nullable InfectedEntity leader;
	private int repath;

	public FollowPackGoal(InfectedEntity mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		if (mob.packId() == 0 || mob.getTarget() != null || mob.investigatePos() != null || (mob.tickCount + mob.getId()) % 40 != 0) {
			return false;
		}
		leader = findLeader();
		return leader != null && mob.distanceToSqr(leader) > LOOSE_DISTANCE * LOOSE_DISTANCE;
	}

	private @Nullable InfectedEntity findLeader() {
		InfectedEntity best = mob;
		for (InfectedEntity other : mob.level().getEntitiesOfClass(InfectedEntity.class, mob.getBoundingBox().inflate(PACK_RANGE, 8.0, PACK_RANGE),
			e -> e.isAlive() && e.packId() == mob.packId())) {
			if (other.getId() < best.getId()) {
				best = other;
			}
		}
		return best == mob ? null : best;
	}

	@Override
	public void start() {
		repath = 0;
	}

	@Override
	public boolean canContinueToUse() {
		return leader != null && leader.isAlive() && mob.getTarget() == null && mob.investigatePos() == null
			&& mob.distanceToSqr(leader) > CLOSE_DISTANCE * CLOSE_DISTANCE && mob.distanceToSqr(leader) < PACK_RANGE * PACK_RANGE * 4;
	}

	@Override
	public void stop() {
		leader = null;
		mob.getNavigation().stop();
	}

	@Override
	public void tick() {
		if (leader != null && --repath <= 0) {
			repath = adjustedTickDelay(20);
			mob.getNavigation().moveTo(leader, 1.0);
		}
	}
}
