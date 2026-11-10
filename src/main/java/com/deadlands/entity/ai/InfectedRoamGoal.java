package com.deadlands.entity.ai;

import com.deadlands.entity.InfectedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Aimless wandering. During the day an infected that is indoors only picks spots that are also
 * indoors, so it shuffles from room to room instead of walking out into the sun.
 */
public class InfectedRoamGoal extends WaterAvoidingRandomStrollGoal {
	private final InfectedEntity infected;

	public InfectedRoamGoal(InfectedEntity mob) {
		super(mob, 0.8);
		this.infected = mob;
	}

	@Override
	public boolean canUse() {
		return infected.getTarget() == null && infected.investigatePos() == null && super.canUse();
	}

	@Override
	protected @Nullable Vec3 getPosition() {
		Level level = mob.level();
		if (level.isBrightOutside() && !level.canSeeSky(mob.blockPosition())) {
			for (int i = 0; i < 6; i++) {
				Vec3 spot = LandRandomPos.getPos(mob, 8, 4);
				if (spot != null && !level.canSeeSky(BlockPos.containing(spot))) {
					return spot;
				}
			}
			return null;
		}
		return super.getPosition();
	}
}
