package com.deadlands.entity.ai;

import com.deadlands.entity.InfectedEntity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;

/**
 * Spot survivors by sight. The range changes with the time of day and the weather
 * ({@link InfectedEntity#detectionRange}); Minecraft's targeting rules already shorten it for
 * crouching or invisible survivors and skip creative and spectator players.
 */
public class InfectedTargetGoal extends NearestAttackableTargetGoal<Player> {
	public InfectedTargetGoal(InfectedEntity mob) {
		// Darkness and crouching shorten how far each survivor is seen (Stealth).
		super(mob, Player.class, 10, true, false, (target, level) -> mob.distanceTo(target) <= mob.detectionRange() * Stealth.rangeFactor(target));
	}

	/** Also called from the super constructor, so it must only use {@code mob}. */
	@Override
	protected double getFollowDistance() {
		return this.mob instanceof InfectedEntity infected ? infected.detectionRange() : super.getFollowDistance();
	}
}
