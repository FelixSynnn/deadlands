package com.deadlands.entity.ai;

import com.deadlands.entity.InfectedEntity;
import com.deadlands.entity.InfectedKind;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * In daylight, idle infected out in the open drift into buildings and other roofed places and
 * stay there ({@link InfectedRoamGoal} keeps them indoors), coming back out at night. Stalkers
 * avoid daylight much more strongly; Runners prefer open ground and rarely bother.
 */
public class SeekShelterGoal extends Goal {
	private final InfectedEntity mob;
	private @Nullable BlockPos shelter;

	public SeekShelterGoal(InfectedEntity mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		Level level = mob.level();
		if (!level.isBrightOutside() || mob.getTarget() != null || mob.investigatePos() != null || mob.isHordeMember() || mob.migrationId() != 0) {
			return false;
		}
		int chance = switch (mob.kind()) {
			case STALKER -> 8;
			case RUNNER -> 400;
			default -> 60;
		};
		if (mob.getRandom().nextInt(chance) != 0 || !level.canSeeSky(mob.blockPosition())) {
			return false;
		}
		shelter = InfectedNav.findShelter(mob, mob.kind() == InfectedKind.STALKER ? 20 : 14, 4, 12);
		return shelter != null;
	}

	@Override
	public void start() {
		if (shelter != null) {
			mob.getNavigation().moveTo(shelter.getX() + 0.5, shelter.getY(), shelter.getZ() + 0.5, 0.9);
		}
	}

	@Override
	public boolean canContinueToUse() {
		return shelter != null && !mob.getNavigation().isDone() && mob.getTarget() == null && mob.level().isBrightOutside();
	}

	@Override
	public void stop() {
		shelter = null;
	}
}
