package com.deadlands.entity.ai;

import com.deadlands.entity.InfectedEntity;
import java.util.EnumSet;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * Horde members know where the survivors are: they walk toward the nearest one even without
 * seeing them, breaking through barricades on the way, until they get close enough to spot and
 * attack normally.
 */
public class HuntGoal extends Goal {
	private static final double HUNT_RANGE = 160.0;
	private static final int REPATH_TICKS = 40;

	private final InfectedEntity mob;
	private @Nullable Player prey;
	private int repath;

	public HuntGoal(InfectedEntity mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		if (!mob.isHordeMember() || mob.getTarget() != null || (mob.tickCount + mob.getId()) % 20 != 0) {
			return false;
		}
		prey = mob.level().getNearestPlayer(mob.getX(), mob.getY(), mob.getZ(), HUNT_RANGE, EntitySelector.NO_CREATIVE_OR_SPECTATOR);
		return prey != null;
	}

	@Override
	public boolean canContinueToUse() {
		return prey != null && prey.isAlive() && !prey.isSpectator() && !prey.isCreative() && mob.getTarget() == null && mob.isHordeMember()
			&& mob.distanceToSqr(prey) < HUNT_RANGE * HUNT_RANGE;
	}

	@Override
	public void start() {
		repath = 0;
	}

	@Override
	public void stop() {
		prey = null;
	}

	@Override
	public void tick() {
		if (prey == null) {
			return;
		}
		if (--repath <= 0) {
			repath = adjustedTickDelay(REPATH_TICKS);
			InfectedNav.moveToward(mob, prey.position(), 1.05);
		}
	}
}
