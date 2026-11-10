package com.deadlands.nest;

import com.deadlands.config.ConfigManager;
import com.deadlands.entity.InfectedEntity;
import com.deadlands.entity.InfectedKind;
import com.deadlands.entity.ai.InfectedNav;
import com.deadlands.noise.Loudness;
import com.deadlands.registry.DLNests;
import com.deadlands.registry.DLSounds;
import com.deadlands.spawning.InfectedManager;
import com.deadlands.spawning.SpawnFinder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The persistent state of one nest: how many infected shelter inside, how alert it is, when it
 * may release again and when it last grew.
 *
 * <ul>
 *   <li>Survivor noise nearby raises its alertness (louder and closer counts more); past the
 *       threshold, and off cooldown, it releases a few of its infected, who go to the noise.</li>
 *   <li>It refills slowly (twice as fast at night), up to its kind's population.</li>
 *   <li>Released infected that find nothing come back and crawl in again
 *       ({@link #absorb}).</li>
 *   <li>Destroying it lets everything still inside out at once.</li>
 * </ul>
 * Everything is saved with the chunk, so a nest that was emptied stays empty after a restart.
 */
public class NestBlockEntity extends BlockEntity {
	private static final float ALERT_THRESHOLD = 6.0F;
	private static final float ALERT_DECAY_PER_SECOND = 0.5F;
	private static final int RELEASE_SEARCH_RADIUS = 3;

	private int population = -1;
	private float alert;
	private long cooldownUntil;
	private long lastGrowth;
	private int released;

	public NestBlockEntity(BlockPos pos, BlockState state) {
		super(DLNests.NEST, pos, state);
	}

	public NestKind kind() {
		return getBlockState().getBlock() instanceof NestBlock nest ? nest.kind() : NestKind.RESIDENTIAL;
	}

	public int population() {
		return Math.max(0, population);
	}

	// ------------------------------------------------------------------------------------ ticking

	/** Server tick (every tick; does its work once a second). */
	public static void serverTick(Level level, BlockPos pos, BlockState state, NestBlockEntity nest) {
		if (!(level instanceof ServerLevel server) || (level.getGameTime() + pos.asLong()) % 20 != 0) {
			return;
		}
		long now = level.getGameTime();
		if (nest.population < 0) {
			// First tick after world generation: start between half and fully grown.
			int max = nest.kind().maxPopulation();
			nest.population = max / 2 + server.getRandom().nextInt(max - max / 2 + 1);
			nest.lastGrowth = now;
			nest.setChanged();
		}
		if (nest.alert > 0.0F) {
			nest.alert = Math.max(0.0F, nest.alert - ALERT_DECAY_PER_SECOND);
		}
		int regen = level.isDarkOutside() ? nest.kind().regenTicks() / 2 : nest.kind().regenTicks();
		if (nest.population < nest.kind().maxPopulation() && now - nest.lastGrowth >= regen) {
			nest.population++;
			nest.lastGrowth = now;
			nest.setChanged();
		} else if (nest.population >= nest.kind().maxPopulation()) {
			nest.lastGrowth = now;
		}
	}

	// ------------------------------------------------------------------------------------ noise

	/** A survivor-made noise was heard {@code distance} blocks away (see {@link NestRegistry}). */
	public void hear(ServerLevel level, Vec3 source, Loudness loudness, double distance, double heardRadius) {
		if (!ConfigManager.get().zombies.nestReleases || level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL
			|| !level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.SPAWN_MONSTERS)) {
			return;
		}
		float closeness = (float) Math.max(0.3, 1.0 - distance / Math.max(1.0, heardRadius) * 0.6);
		alert += points(loudness) * closeness;
		setChanged();
		if (alert >= ALERT_THRESHOLD && level.getGameTime() >= cooldownUntil && population() > 0) {
			release(level, source);
		}
	}

	private static float points(Loudness loudness) {
		return switch (loudness) {
			case QUIET -> 1.0F;
			case LOW -> 3.0F;
			case MODERATE -> 6.0F;
			case LOUD -> 12.0F;
			case GUNSHOT -> 20.0F;
			case EXPLOSION -> 40.0F;
		};
	}

	/** Let some infected out toward {@code toward} (or everything left, when {@code all}). */
	public int release(ServerLevel level, @Nullable Vec3 toward) {
		return release(level, toward, false);
	}

	private int release(ServerLevel level, @Nullable Vec3 toward, boolean all) {
		RandomSource random = level.getRandom();
		int wanted = all ? population() : Math.min(population(), kind().releaseCount(random));
		if (!all && !InfectedManager.canAddNear(level, worldPosition, wanted)) {
			wanted = Math.min(wanted, InfectedManager.roomNear(level, worldPosition));
		}
		int out = 0;
		InfectedEntity.GroupData pack = new InfectedEntity.GroupData(InfectedEntity.newPackId(random));
		for (int i = 0; i < wanted; i++) {
			BlockPos spot = exitSpot(level, random);
			if (spot == null) {
				break;
			}
			InfectedKind kind = kind().pickResident(random);
			InfectedEntity infected = SpawnFinder.spawn(level, kind, spot, EntitySpawnReason.MOB_SUMMONED, pack);
			if (infected == null) {
				continue;
			}
			out++;
			infected.setHomeNest(worldPosition);
			level.broadcastEntityEvent(infected, InfectedEntity.EVENT_EMERGE);
			if (toward != null) {
				infected.investigate(toward, Loudness.MODERATE.priority(), 600, 1.1);
			}
		}
		if (out > 0) {
			population = Math.max(0, population() - out);
			released += out;
			cooldownUntil = level.getGameTime() + kind().cooldownTicks();
			alert = 0.0F;
			level.playSound(null, worldPosition, DLSounds.NEST_RELEASE.value(), SoundSource.HOSTILE, 1.6F, 0.9F + random.nextFloat() * 0.2F);
			level.sendParticles(new DustParticleOptions(0x4A2E28, 1.6F), worldPosition.getX() + 0.5, worldPosition.getY() + 0.8,
				worldPosition.getZ() + 0.5, 30, 0.8, 0.5, 0.8, 0.02);
			setChanged();
		}
		return out;
	}

	private @Nullable BlockPos exitSpot(ServerLevel level, RandomSource random) {
		for (int attempt = 0; attempt < 12; attempt++) {
			BlockPos candidate = worldPosition.offset(random.nextInt(RELEASE_SEARCH_RADIUS * 2 + 1) - RELEASE_SEARCH_RADIUS,
				random.nextInt(3) - 1, random.nextInt(RELEASE_SEARCH_RADIUS * 2 + 1) - RELEASE_SEARCH_RADIUS);
			if (!candidate.equals(worldPosition) && InfectedNav.isStandable(level, candidate)) {
				return candidate;
			}
		}
		return null;
	}

	/** Take a returning infected back in. False (and nothing happens) when the nest is full. */
	public boolean absorb(InfectedEntity infected) {
		if (population() >= kind().maxPopulation() || !(level instanceof ServerLevel server)) {
			return false;
		}
		population = population() + 1;
		server.playSound(null, worldPosition, DLSounds.NEST_ABSORB.value(), SoundSource.HOSTILE, 0.9F, 0.9F + server.getRandom().nextFloat() * 0.2F);
		infected.discard();
		setChanged();
		return true;
	}

	/** The nest block is being broken or replaced (not unloaded): everything still inside bursts out. */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		super.preRemoveSideEffects(pos, state);
		if (level instanceof ServerLevel server && population() > 0) {
			burst(server);
		}
	}

	public void burst(ServerLevel level) {
		level.playSound(null, worldPosition, DLSounds.NEST_DESTROYED.value(), SoundSource.HOSTILE, 2.0F, 0.9F);
		release(level, null, true);
		population = 0;
	}

	// ------------------------------------------------------------------------------------ persistence

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		population = input.getIntOr("Population", -1);
		alert = input.getFloatOr("Alert", 0.0F);
		cooldownUntil = input.getLongOr("CooldownUntil", 0L);
		lastGrowth = input.getLongOr("LastGrowth", 0L);
		released = input.getIntOr("Released", 0);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("Population", population);
		output.putFloat("Alert", alert);
		output.putLong("CooldownUntil", cooldownUntil);
		output.putLong("LastGrowth", lastGrowth);
		output.putInt("Released", released);
	}
}
