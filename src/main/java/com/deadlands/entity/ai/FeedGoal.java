package com.deadlands.entity.ai;

import com.deadlands.entity.InfectedEntity;
import com.deadlands.entity.InfectedKind;
import com.deadlands.horror.HorrorDirector;
import com.deadlands.registry.DLSounds;
import java.util.EnumSet;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;

/**
 * Phase 4 (the "Feeding" jumpscare): an idle infected with no target now and then settles over a kill:
 * it stands still, head bowed, and feeds (wet bites, a little blood). A survivor who comes within
 * eight blocks interrupts it: its head snaps round to them before the body turns, it screeches, and
 * every infected within thirty blocks comes for them. Walkers, runners, military and charred feed;
 * the stalker, brute, screamer, crawler and bloater have their own ways.
 */
public class FeedGoal extends Goal {
	private static final int CHANCE = 6000; // about once every five minutes of idling per infected
	private static final int MAX_TICKS = 2400;
	private static final double NOTICE = 8.0;
	private static final double WAKE = 30.0;
	private final InfectedEntity infected;
	private int ticks;
	private int nextBite;

	public FeedGoal(InfectedEntity infected) {
		this.infected = infected;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
	}

	private boolean feeder() {
		InfectedKind kind = infected.kind();
		return kind == InfectedKind.WALKER || kind == InfectedKind.RUNNER || kind == InfectedKind.MILITARY || kind == InfectedKind.CHARRED;
	}

	@Override
	public boolean canUse() {
		return feeder() && infected.getTarget() == null && infected.investigatePos() == null && infected.onGround()
			&& infected.getRandom().nextInt(CHANCE) == 0;
	}

	@Override
	public boolean canContinueToUse() {
		return infected.getTarget() == null && ticks < MAX_TICKS;
	}

	@Override
	public void start() {
		ticks = 0;
		nextBite = 10;
		infected.getNavigation().stop();
		infected.setFlag(InfectedEntity.FLAG_FEEDING, true);
	}

	@Override
	public void stop() {
		infected.setFlag(InfectedEntity.FLAG_FEEDING, false);
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		ticks++;
		infected.getNavigation().stop();
		infected.setXRot(55.0F); // head bowed over the kill
		if (!(infected.level() instanceof ServerLevel level)) {
			return;
		}
		if (--nextBite <= 0) {
			nextBite = 30 + infected.getRandom().nextInt(40);
			infected.playSound(DLSounds.INFECTED_FEEDING.value(), 0.8F, 0.9F + infected.getRandom().nextFloat() * 0.2F);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()),
				infected.getX(), infected.getY() + 0.2, infected.getZ(), 4, 0.25, 0.05, 0.25, 0.02);
		}
		ServerPlayer near = (ServerPlayer) level.getNearestPlayer(infected, NOTICE);
		if (near != null && !near.isSpectator() && !near.isCreative()) {
			interrupted(level, near);
		}
	}

	private void interrupted(ServerLevel level, ServerPlayer player) {
		// The head snaps round to the survivor before the body follows.
		double dx = player.getX() - infected.getX();
		double dz = player.getZ() - infected.getZ();
		float yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
		infected.setYHeadRot(yaw);
		infected.setXRot(0.0F);
		level.playSound(null, infected.getX(), infected.getEyeY(), infected.getZ(), DLSounds.INFECTED_FEEDING_SCREECH.value(), SoundSource.HOSTILE, 1.6F, 1.0F);
		HorrorDirector.fire(player, "deadlands:feeding_turn", 12000L);
		infected.setFlag(InfectedEntity.FLAG_FEEDING, false);
		for (InfectedEntity other : level.getEntitiesOfClass(InfectedEntity.class, infected.getBoundingBox().inflate(WAKE, 8.0, WAKE),
			e -> e != infected && e.isAlive() && e.getTarget() == null)) {
			other.acceptAlert(player);
		}
		infected.setTarget(player);
	}
}
