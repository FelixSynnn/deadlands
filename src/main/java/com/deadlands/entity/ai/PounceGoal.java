package com.deadlands.entity.ai;

import com.deadlands.entity.InfectedEntity;
import com.deadlands.entity.StalkerEntity;
import com.deadlands.registry.DLSounds;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * The Stalker crouches and leaps at a survivor 3 to 7 blocks away; landing on them hurts and
 * slows them. One leap every six seconds at most.
 */
public class PounceGoal extends Goal {
	private static final int CROUCH_TICKS = 8;
	private static final int MAX_TICKS = 30;
	private static final int COOLDOWN = 120;

	private final StalkerEntity mob;
	private int ticks;
	private boolean leapt;
	private boolean landedHit;
	private long readyAt;

	public PounceGoal(StalkerEntity mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
	}

	@Override
	public boolean canUse() {
		LivingEntity target = mob.getTarget();
		if (target == null || !target.isAlive() || !mob.onGround() || mob.level().getGameTime() < readyAt) {
			return false;
		}
		double distanceSqr = mob.distanceToSqr(target);
		return distanceSqr > 9.0 && distanceSqr < 49.0 && mob.getSensing().hasLineOfSight(target);
	}

	@Override
	public void start() {
		ticks = 0;
		leapt = false;
		landedHit = false;
		mob.getNavigation().stop();
		mob.level().broadcastEntityEvent(mob, InfectedEntity.EVENT_POUNCE);
	}

	@Override
	public boolean canContinueToUse() {
		return ticks < MAX_TICKS && mob.isAlive() && mob.getTarget() != null && !(leapt && mob.onGround() && ticks > CROUCH_TICKS + 4);
	}

	@Override
	public void stop() {
		readyAt = mob.level().getGameTime() + COOLDOWN;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		ticks++;
		LivingEntity target = mob.getTarget();
		if (target == null) {
			return;
		}
		mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
		if (!leapt && ticks >= CROUCH_TICKS) {
			leapt = true;
			Vec3 dir = target.position().subtract(mob.position());
			Vec3 flat = dir.multiply(1.0, 0.0, 1.0);
			double length = Math.max(0.01, flat.length());
			mob.setDeltaMovement(flat.x / length * 1.15, 0.42 + Math.min(0.3, Math.max(0.0, dir.y) * 0.1), flat.z / length * 1.15);
			mob.playSound(DLSounds.INFECTED_STALKER_POUNCE.value(), 1.2F, 0.9F + mob.getRandom().nextFloat() * 0.2F);
		}
		if (leapt && !landedHit && mob.distanceToSqr(target) < 2.6 && mob.level() instanceof ServerLevel level) {
			landedHit = true;
			if (mob.doHurtTarget(level, target)) {
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 50, 1), mob);
			}
		}
	}
}
