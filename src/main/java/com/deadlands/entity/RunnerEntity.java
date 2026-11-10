package com.deadlands.entity;

import com.deadlands.Deadlands;
import com.deadlands.entity.ai.InfectedAttackGoal;
import com.deadlands.registry.DLSounds;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * Runners chase in bursts: a few seconds of all-out sprint, a moment winded, then again. In the
 * open (under the sky) the bursts last longer, so they are most dangerous on open ground. They
 * shriek when they spot someone.
 */
public class RunnerEntity extends InfectedEntity {
	private static final Identifier BURST_ID = Deadlands.id("runner_burst");
	// Ground speed goes with the square of the attribute: +17% is 6.0 blocks a second (a sprinting
	// survivor does 5.6), -30% is 2.2.
	private static final AttributeModifier BURST = new AttributeModifier(BURST_ID, 0.17, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
	private static final AttributeModifier WINDED = new AttributeModifier(BURST_ID, -0.30, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);

	private int phaseTicks;
	private boolean bursting;

	public RunnerEntity(EntityType<? extends RunnerEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected void addAttackGoal() {
		this.goalSelector.addGoal(3, new InfectedAttackGoal(this, chaseSpeed(), 14, 4));
	}

	@Override
	protected void onTargetSpotted(LivingEntity target) {
		playSound(DLSounds.INFECTED_RUNNER_ALERT.value(), 1.4F, 0.9F + random.nextFloat() * 0.2F);
		bursting = false;
		phaseTicks = 0;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}
		LivingEntity target = getTarget();
		if (target == null || !target.isAlive()) {
			speed.removeModifier(BURST_ID);
			setFlag(FLAG_SPRINTING, false);
			bursting = false;
			phaseTicks = 0;
			return;
		}
		// customServerAiStep runs once per AI update; count the ticks that passed since the last one.
		phaseTicks -= aiInterval();
		if (phaseTicks > 0) {
			return;
		}
		bursting = !bursting;
		speed.removeModifier(BURST_ID);
		if (bursting) {
			boolean open = level.canSeeSky(blockPosition());
			phaseTicks = 60 + random.nextInt(40) + (open ? 40 : 0);
			speed.addTransientModifier(BURST);
		} else {
			phaseTicks = 30 + random.nextInt(20);
			speed.addTransientModifier(WINDED);
		}
		setFlag(FLAG_SPRINTING, bursting);
	}
}
