package com.deadlands.entity.ai;

import com.deadlands.entity.InfectedEntity;
import com.deadlands.horde.MigrationSystem;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Members of a migrating group walk on toward the group's destination town while nothing distracts them. */
public class MigrateGoal extends Goal {
	private final InfectedEntity mob;
	private @Nullable Vec3 destination;
	private int repath;

	public MigrateGoal(InfectedEntity mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		if (mob.migrationId() == 0 || mob.getTarget() != null || mob.investigatePos() != null || (mob.tickCount + mob.getId()) % 20 != 0
			|| !(mob.level() instanceof ServerLevel level)) {
			return false;
		}
		destination = MigrationSystem.destination(level, mob.migrationId());
		return destination != null && mob.distanceToSqr(destination) > 64.0;
	}

	@Override
	public void start() {
		repath = 0;
	}

	@Override
	public boolean canContinueToUse() {
		return destination != null && mob.migrationId() != 0 && mob.getTarget() == null && mob.investigatePos() == null
			&& mob.distanceToSqr(destination) > 36.0;
	}

	@Override
	public void stop() {
		destination = null;
	}

	@Override
	public void tick() {
		if (destination != null && --repath <= 0) {
			repath = adjustedTickDelay(60);
			InfectedNav.moveToward(mob, destination, 0.85);
		}
	}
}
