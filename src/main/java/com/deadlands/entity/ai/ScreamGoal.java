package com.deadlands.entity.ai;

import com.deadlands.entity.InfectedEntity;
import com.deadlands.entity.InfectedKind;
import com.deadlands.entity.ScreamerEntity;
import com.deadlands.noise.Loudness;
import com.deadlands.noise.NoiseSystem;
import com.deadlands.registry.DLSounds;
import com.deadlands.spawning.InfectedManager;
import com.deadlands.spawning.SpawnFinder;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * The Screamer stops, throws its head back and screams when it sees a survivor: the scream is a
 * loud noise (every infected within ~65 blocks comes, and nearby nests wake), and the first few
 * screams also call up a couple of infected out of sight. Long cooldown, limited calls per
 * Screamer, and the usual population caps, so screams can never snowball.
 */
public class ScreamGoal extends Goal {
	private static final int SCREAM_TICKS = 50;
	private static final int NOISE_AT = 10;
	private static final int SUMMON_AT = 22;

	private final ScreamerEntity mob;
	private int ticks;

	public ScreamGoal(ScreamerEntity mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
	}

	@Override
	public boolean canUse() {
		LivingEntity target = mob.getTarget();
		return target != null && target.isAlive() && mob.canScream() && mob.distanceToSqr(target) < 28.0 * 28.0
			&& mob.getSensing().hasLineOfSight(target);
	}

	@Override
	public void start() {
		ticks = 0;
		mob.getNavigation().stop();
		mob.setFlag(InfectedEntity.FLAG_SCREAMING, true);
		mob.level().broadcastEntityEvent(mob, InfectedEntity.EVENT_SCREAM);
		mob.playSound(DLSounds.INFECTED_SCREAMER_SCREAM.value(), 4.0F, 0.9F + mob.getRandom().nextFloat() * 0.2F);
	}

	@Override
	public boolean canContinueToUse() {
		return ticks < SCREAM_TICKS && mob.isAlive();
	}

	@Override
	public void stop() {
		mob.setFlag(InfectedEntity.FLAG_SCREAMING, false);
		mob.startScreamCooldown();
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
			mob.getLookControl().setLookAt(target, 10.0F, 10.0F);
		}
		if (!(mob.level() instanceof ServerLevel level)) {
			return;
		}
		if (ticks == NOISE_AT) {
			NoiseSystem.emit(level, mob.position(), Loudness.LOUD, mob, true);
		}
		if (ticks == SUMMON_AT && mob.useSummon()) {
			summon(level, target);
		}
	}

	private void summon(ServerLevel level, LivingEntity target) {
		int count = 2 + mob.getRandom().nextInt(2);
		InfectedEntity.GroupData pack = new InfectedEntity.GroupData(InfectedEntity.newPackId(mob.getRandom()));
		for (int i = 0; i < count && InfectedManager.canAddNear(level, mob.blockPosition(), 0); i++) {
			BlockPos pos = SpawnFinder.around(level, mob.blockPosition(), 10.0, 18.0, mob.getRandom(), false);
			if (pos == null) {
				continue;
			}
			InfectedKind kind = mob.getRandom().nextFloat() < 0.35F ? InfectedKind.RUNNER : InfectedKind.WALKER;
			InfectedEntity called = SpawnFinder.spawn(level, kind, pos, EntitySpawnReason.REINFORCEMENT, pack);
			if (called != null && target != null) {
				called.acceptAlert(target);
			}
		}
	}
}
