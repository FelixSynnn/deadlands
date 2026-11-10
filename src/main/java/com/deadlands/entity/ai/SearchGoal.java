package com.deadlands.entity.ai;

import com.deadlands.entity.InfectedEntity;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * After losing a target, go to where it was last seen and search around that spot for a while
 * before giving up. Survivors who break line of sight are not forgotten instantly.
 */
public class SearchGoal extends Goal {
	private static final int SEARCH_TICKS = 320;
	private static final int SEARCH_POINTS = 4;

	private final InfectedEntity mob;
	private @Nullable Vec3 center;
	private int timer;
	private int points;

	public SearchGoal(InfectedEntity mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		if (mob.getTarget() != null || (mob.tickCount + mob.getId()) % 10 != 0) {
			return false;
		}
		center = mob.lastKnownTargetPos();
		return center != null;
	}

	@Override
	public void start() {
		timer = SEARCH_TICKS;
		points = 0;
		if (center != null) {
			InfectedNav.moveToward(mob, center, 1.0);
		}
	}

	@Override
	public boolean canContinueToUse() {
		return center != null && timer > 0 && mob.getTarget() == null && points <= SEARCH_POINTS;
	}

	@Override
	public void stop() {
		mob.forgetLastKnownTarget();
		center = null;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		timer--;
		if (center == null || !mob.getNavigation().isDone()) {
			return;
		}
		points++;
		if (points <= SEARCH_POINTS) {
			Vec3 around = center.add(mob.getRandom().nextInt(13) - 6, 0, mob.getRandom().nextInt(13) - 6);
			mob.getNavigation().moveTo(around.x, around.y, around.z, 0.85);
		}
	}
}
