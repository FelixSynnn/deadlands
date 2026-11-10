package com.deadlands.entity.ai;

import com.deadlands.config.ConfigManager;
import com.deadlands.entity.BruteEntity;
import com.deadlands.entity.InfectedEntity;
import com.deadlands.registry.DLSounds;
import com.deadlands.registry.DLTags;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;

/**
 * The Brute raises both fists and smashes the ground: everything around it (except other
 * infected) is hurt and thrown back, and barricades right in front of it break. Telegraphed by
 * a long wind-up, with a cooldown of five seconds.
 */
public class SlamGoal extends Goal {
	private static final int SLAM_TICKS = 26;
	private static final int IMPACT_AT = 13;
	private static final double RADIUS = 3.5;
	private static final int COOLDOWN = 100;

	private final BruteEntity mob;
	private int ticks;
	private long readyAt;

	public SlamGoal(BruteEntity mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
	}

	@Override
	public boolean canUse() {
		LivingEntity target = mob.getTarget();
		return target != null && target.isAlive() && mob.onGround() && mob.level().getGameTime() >= readyAt
			&& mob.distanceToSqr(target) < 3.2 * 3.2;
	}

	@Override
	public void start() {
		ticks = 0;
		mob.getNavigation().stop();
		mob.level().broadcastEntityEvent(mob, InfectedEntity.EVENT_SLAM);
	}

	@Override
	public boolean canContinueToUse() {
		return ticks < SLAM_TICKS && mob.isAlive();
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
		if (target != null && ticks < IMPACT_AT) {
			mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
		}
		if (ticks == IMPACT_AT && mob.level() instanceof ServerLevel level) {
			impact(level);
		}
	}

	private void impact(ServerLevel level) {
		Vec3 center = mob.position();
		float damage = (float) mob.getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.2F;
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(RADIUS, 1.5, RADIUS),
			e -> e != mob && e.isAlive() && !(e instanceof InfectedEntity))) {
			double distance = victim.distanceTo(mob);
			if (distance > RADIUS + 0.5) {
				continue;
			}
			float scale = (float) Math.max(0.35, 1.0 - distance / (RADIUS + 1.0));
			if (victim.hurtServer(level, mob.damageSources().mobAttack(mob), damage * scale)) {
				Vec3 push = victim.position().subtract(center).multiply(1.0, 0.0, 1.0).normalize().scale(1.1 * scale);
				victim.push(push.x, 0.45 * scale, push.z);
				victim.hurtMarked = true;
			}
		}
		BlockPos ground = mob.blockPosition().below();
		BlockState groundState = level.getBlockState(ground);
		if (!groundState.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, groundState), center.x, center.y + 0.1, center.z, 40, 1.4, 0.1, 1.4, 0.15);
		}
		level.playSound(null, mob.blockPosition(), DLSounds.INFECTED_BRUTE_SLAM.value(), mob.getSoundSource(), 1.6F, 0.9F + mob.getRandom().nextFloat() * 0.15F);
		if (ConfigManager.get().zombies.breakBarricades && level.getGameRules().get(GameRules.MOB_GRIEFING)) {
			Vec3 forward = Vec3.directionFromRotation(0.0F, mob.getYRot());
			for (int dy = 0; dy <= 1; dy++) {
				BlockPos front = BlockPos.containing(center.x + forward.x * 1.4, center.y + dy + 0.2, center.z + forward.z * 1.4);
				BlockState state = level.getBlockState(front);
				if ((state.is(DLTags.INFECTED_BREAKABLE) || state.is(DLTags.BRUTE_BREAKABLE)) && state.getDestroySpeed(level, front) >= 0.0F) {
					level.destroyBlock(front, true, mob);
				}
			}
		}
	}
}
