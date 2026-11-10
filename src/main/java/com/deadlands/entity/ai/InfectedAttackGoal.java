package com.deadlands.entity.ai;

import com.deadlands.entity.InfectedEntity;
import com.deadlands.registry.DLSounds;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Path;

/**
 * Chase and hit the target. Unlike vanilla melee, a swing has a wind-up: the attack animation
 * starts, and the hit lands a few ticks later only if the target is still in reach, so survivors
 * can see a swing coming and step back.
 */
public class InfectedAttackGoal extends Goal {
	protected final InfectedEntity mob;
	private final double speed;
	private final int interval;
	private final int windup;
	private int cooldown;
	private int windupLeft = -1;
	private int repath;
	private long lastCanUseCheck;

	/**
	 * @param interval ticks between swings
	 * @param windup   ticks from the start of a swing until it hits
	 */
	public InfectedAttackGoal(InfectedEntity mob, double speed, int interval, int windup) {
		this.mob = mob;
		this.speed = speed;
		this.interval = interval;
		this.windup = Math.max(1, windup);
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		long now = mob.level().getGameTime();
		if (now - lastCanUseCheck < 10L) {
			return false;
		}
		lastCanUseCheck = now;
		LivingEntity target = mob.getTarget();
		if (target == null || !target.isAlive() || !EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(target)) {
			return false;
		}
		Path path = mob.getNavigation().createPath(target, 0);
		return path != null || mob.isWithinMeleeAttackRange(target) || mob.distanceToSqr(target) > 400.0;
	}

	@Override
	public boolean canContinueToUse() {
		LivingEntity target = mob.getTarget();
		if (target == null || !target.isAlive() || !EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(target)) {
			return false;
		}
		double range = mob.getAttributeValue(Attributes.FOLLOW_RANGE) * 1.6;
		return mob.distanceToSqr(target) < range * range;
	}

	@Override
	public void start() {
		LivingEntity target = mob.getTarget();
		if (target != null) {
			mob.getNavigation().moveTo(target, speed);
		}
		mob.setAggressive(true);
		repath = 0;
		cooldown = 8;
		windupLeft = -1;
	}

	@Override
	public void stop() {
		LivingEntity target = mob.getTarget();
		if (target != null && !EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(target)) {
			mob.setTarget(null);
		}
		mob.setAggressive(false);
		mob.getNavigation().stop();
		windupLeft = -1;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		LivingEntity target = mob.getTarget();
		if (target == null) {
			return;
		}
		mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
		double distanceSqr = mob.distanceToSqr(target);
		if (--repath <= 0) {
			repath = 4 + mob.getRandom().nextInt(7) + (distanceSqr > 1024.0 ? 10 : distanceSqr > 256.0 ? 5 : 0);
			if (!mob.getNavigation().moveTo(target, speed)) {
				InfectedNav.moveToward(mob, target.position(), speed);
				repath += 15;
			}
		}

		cooldown = Math.max(0, cooldown - 1);
		if (windupLeft > 0) {
			if (--windupLeft == 0) {
				windupLeft = -1;
				if (mob.isWithinMeleeAttackRange(target) && mob.getSensing().hasLineOfSight(target)) {
					mob.doHurtTarget((ServerLevel) mob.level(), target);
				}
			}
		} else if (cooldown <= 0 && mob.isWithinMeleeAttackRange(target) && mob.getSensing().hasLineOfSight(target)) {
			cooldown = interval;
			windupLeft = windup;
			mob.swing(InteractionHand.MAIN_HAND);
			mob.startAttackAnimation();
			mob.playSound(DLSounds.INFECTED_ATTACK.value(), 0.8F, 0.85F + mob.getRandom().nextFloat() * 0.3F);
		}
	}
}
