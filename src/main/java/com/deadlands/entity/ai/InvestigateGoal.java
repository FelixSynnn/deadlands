package com.deadlands.entity.ai;

import com.deadlands.entity.InfectedEntity;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Walk to the spot of a noise (or an alert, or a light at night) and look around there. Louder
 * noises make the infected hurry; the spot can be far away (gunfire is heard from ~100 blocks),
 * so the walk is done in steps.
 */
public class InvestigateGoal extends Goal {
	private static final double ARRIVED_DISTANCE = 3.0;
	private static final int REPATH_TICKS = 40;
	private static final int MAX_REPATHS = 8;

	private final InfectedEntity mob;
	private @Nullable Vec3 spot;
	private boolean arrived;
	private int lookTimer;
	private int repath;
	private int repaths;

	public InvestigateGoal(InfectedEntity mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (mob.getTarget() != null) {
			return false;
		}
		spot = mob.investigatePos();
		return spot != null;
	}

	@Override
	public void start() {
		arrived = false;
		repath = 0;
		repaths = 0;
	}

	@Override
	public boolean canContinueToUse() {
		return mob.getTarget() == null && mob.investigatePos() != null;
	}

	@Override
	public void stop() {
		mob.getNavigation().stop();
		spot = null;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		Vec3 current = mob.investigatePos();
		if (current == null) {
			return;
		}
		if (current != spot) {
			// A louder noise replaced the old one: head there instead.
			spot = current;
			arrived = false;
			repath = 0;
			repaths = 0;
		}
		if (!arrived) {
			boolean close = mob.distanceToSqr(spot) < ARRIVED_DISTANCE * ARRIVED_DISTANCE;
			if (close || repaths > MAX_REPATHS) {
				arrived = true;
				lookTimer = 40 + mob.getRandom().nextInt(60);
				mob.getNavigation().stop();
			} else if (--repath <= 0 || mob.getNavigation().isDone()) {
				repath = REPATH_TICKS;
				repaths++;
				InfectedNav.moveToward(mob, spot, mob.investigateSpeed());
			}
			return;
		}
		if (lookTimer % 15 == 0) {
			mob.getLookControl().setLookAt(spot.x + mob.getRandom().nextInt(9) - 4, mob.getEyeY(), spot.z + mob.getRandom().nextInt(9) - 4);
		}
		if (--lookTimer <= 0) {
			mob.clearInvestigation();
		}
	}
}
