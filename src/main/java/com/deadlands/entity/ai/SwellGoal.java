package com.deadlands.entity.ai;

import com.deadlands.entity.BloaterEntity;
import com.deadlands.entity.InfectedEntity;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Next to a survivor, a Bloater stops and swells for a second and a half, then bursts into a
 * cloud of sickening gas. Survivors who back off in time are only caught by the edge of the cloud.
 */
public class SwellGoal extends Goal {
	private static final int SWELL_TICKS = 30;
	private static final double TRIGGER_DISTANCE = 2.2;

	private final BloaterEntity mob;
	private int ticks;

	public SwellGoal(BloaterEntity mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
	}

	@Override
	public boolean canUse() {
		LivingEntity target = mob.getTarget();
		return target != null && target.isAlive() && mob.distanceToSqr(target) < TRIGGER_DISTANCE * TRIGGER_DISTANCE;
	}

	@Override
	public void start() {
		ticks = 0;
		mob.getNavigation().stop();
		mob.setFlag(InfectedEntity.FLAG_SWELLING, true);
	}

	@Override
	public boolean canContinueToUse() {
		return mob.isAlive() && ticks <= SWELL_TICKS;
	}

	@Override
	public void stop() {
		mob.setFlag(InfectedEntity.FLAG_SWELLING, false);
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		ticks++;
		LivingEntity target = mob.getTarget();
		if (target != null) {
			mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
		}
		if (ticks == SWELL_TICKS && mob.level() instanceof ServerLevel level) {
			mob.burst(level);
		}
	}
}
