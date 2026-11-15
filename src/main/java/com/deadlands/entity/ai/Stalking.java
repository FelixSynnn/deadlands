package com.deadlands.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What the killers (the Aberrant, the Guest) share when they stalk and chase.
 *
 * <ul>
 *   <li><b>Cover:</b> {@link #hiddenSpot} picks a place near the prey that they can't see from where they
 *       stand, preferring behind them and in the dark; {@link #visibleSpot} one they can, for a deliberate peek.</li>
 *   <li><b>Being seen:</b> {@link #watching}: the prey is looking right at it, with nothing in between.</li>
 *   <li><b>Never stuck for long:</b> {@link Unstuck} notices when a chaser has stopped getting anywhere (no
 *       path, or no progress) and {@link #teleportNear} brings it back near the prey, out of their sight and
 *       behind them, in a breath of smoke at both ends. A chaser left far behind is brought closer the same way.</li>
 *   <li><b>Swimming:</b> {@link #swim} keeps a big body at the surface and moving toward its path in water.</li>
 *   <li><b>Sounds behind you:</b> {@link #soundBehind} plays a sound a few blocks behind the prey, out of view.</li>
 * </ul>
 */
public final class Stalking {
	private Stalking() {
	}

	/** Whether the prey can see a body of {@code height} standing at {@code feet} from where they are. */
	public static boolean visibleFrom(Player player, Vec3 feet, double height) {
		Vec3 head = feet.add(0.0, height, 0.0);
		return player.level().clip(new ClipContext(player.getEyePosition(), head, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player))
			.getType() == HitResult.Type.MISS;
	}

	/** The prey is looking right at it, with nothing in between. */
	public static boolean watching(Player player, Mob mob) {
		Vec3 to = mob.getEyePosition().subtract(player.getEyePosition()).normalize();
		return player.getViewVector(1.0F).dot(to) > 0.75 && player.hasLineOfSight(mob);
	}

	/** A dry floor near {@code probe} with {@code room} blocks of headroom, or null. */
	public static @Nullable BlockPos standable(ServerLevel level, BlockPos probe, int room) {
		if (!level.isLoaded(probe)) {
			return null;
		}
		for (int dy = 0; dy <= 4; dy++) {
			for (int sign : new int[] {1, -1}) {
				BlockPos at = probe.offset(0, dy * sign, 0);
				if (!level.getBlockState(at.below()).isSolid() || !level.getFluidState(at).isEmpty()) {
					continue;
				}
				boolean clear = true;
				for (int y = 0; y < room && clear; y++) {
					clear = level.getBlockState(at.above(y)).getCollisionShape(level, at.above(y)).isEmpty();
				}
				if (clear) {
					return at;
				}
			}
		}
		return null;
	}

	/**
	 * A place {@code near} to {@code far} blocks from the prey that they can't see, behind them and dark if
	 * possible, and close to the stalker (or, with {@code awayFromMe}, a little farther: slipping away).
	 */
	public static @Nullable Vec3 hiddenSpot(Mob mob, ServerLevel level, Player target, double near, double far, boolean awayFromMe, double height) {
		Vec3 gaze = target.getViewVector(1.0F).multiply(1.0, 0.0, 1.0);
		gaze = gaze.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : gaze.normalize();
		RandomSource random = mob.getRandom();
		int room = (int) Math.ceil(height);
		Vec3 best = null;
		double bestScore = Double.MAX_VALUE;
		for (int attempt = 0; attempt < 16; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double r = near + random.nextDouble() * (far - near);
			BlockPos floor = standable(level, BlockPos.containing(target.getX() + Math.cos(angle) * r, target.getY(), target.getZ() + Math.sin(angle) * r), room);
			if (floor == null) {
				continue;
			}
			Vec3 at = Vec3.atBottomCenterOf(floor);
			Vec3 dir = at.subtract(target.position()).multiply(1.0, 0.0, 1.0).normalize();
			double score = mob.position().distanceTo(at) * (awayFromMe ? -0.2 : 0.4);
			score += visibleFrom(target, at, height) ? 60.0 : 0.0;
			score += dir.dot(gaze) * 12.0; // behind them is better
			score += level.getMaxLocalRawBrightness(floor) * 0.6; // darker is better
			if (score < bestScore) {
				bestScore = score;
				best = at;
			}
		}
		return best;
	}

	/** A spot in front of the prey, {@code near} to {@code far} blocks off, in their view. */
	public static @Nullable Vec3 visibleSpot(Mob mob, ServerLevel level, Player target, double near, double far, double height) {
		Vec3 gaze = target.getViewVector(1.0F).multiply(1.0, 0.0, 1.0);
		gaze = gaze.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : gaze.normalize();
		RandomSource random = mob.getRandom();
		for (int attempt = 0; attempt < 12; attempt++) {
			Vec3 dir = gaze.yRot((float) ((random.nextDouble() - 0.5) * 1.2));
			double r = near + random.nextDouble() * (far - near);
			BlockPos floor = standable(level, BlockPos.containing(target.position().add(dir.scale(r))), (int) Math.ceil(height));
			if (floor != null && visibleFrom(target, Vec3.atBottomCenterOf(floor), height)) {
				return Vec3.atBottomCenterOf(floor);
			}
		}
		return null;
	}

	/**
	 * Bring the mob back near the prey, {@code near} to {@code far} blocks off, somewhere they can't see:
	 * smoke where it was and where it lands. Returns false if no such place was found.
	 */
	public static boolean teleportNear(Mob mob, ServerLevel level, Player target, double near, double far, double height) {
		for (int tries = 0; tries < 3; tries++) {
			Vec3 spot = hiddenSpot(mob, level, target, near, far, false, height);
			if (spot == null || visibleFrom(target, spot, height)) {
				continue;
			}
			puff(level, mob.position(), mob.getBbHeight());
			mob.teleportTo(spot.x, spot.y, spot.z);
			mob.getNavigation().stop();
			mob.setDeltaMovement(Vec3.ZERO);
			puff(level, spot, mob.getBbHeight());
			return true;
		}
		return false;
	}

	/**
	 * Right by the prey: a floor {@code near} to {@code far} blocks from them with nothing solid in between (so it lands on
	 * their side of any wall), out of their sight if it can be, in their sight if not. False if there is no room at all.
	 */
	public static boolean teleportBeside(Mob mob, ServerLevel level, Player target, double near, double far, double height) {
		RandomSource random = mob.getRandom();
		int room = (int) Math.ceil(height);
		Vec3 eye = target.getEyePosition();
		Vec3 fallback = null;
		for (int attempt = 0; attempt < 24; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double r = near + random.nextDouble() * (far - near);
			BlockPos floor = standable(level, BlockPos.containing(target.getX() + Math.cos(angle) * r, target.getY(), target.getZ() + Math.sin(angle) * r), room);
			if (floor == null || Math.abs(floor.getY() - target.getY()) > 2.0) {
				continue;
			}
			Vec3 at = Vec3.atBottomCenterOf(floor);
			if (level.clip(new ClipContext(at.add(0.0, 1.0, 0.0), eye, ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, mob)).getType() != HitResult.Type.MISS) {
				continue; // a wall between: it would still be shut out
			}
			if (!visibleFrom(target, at, height)) {
				fallback = at;
				break;
			}
			if (fallback == null) {
				fallback = at;
			}
		}
		if (fallback == null) {
			return false;
		}
		puff(level, mob.position(), mob.getBbHeight());
		mob.teleportTo(fallback.x, fallback.y, fallback.z);
		mob.getNavigation().stop();
		mob.setDeltaMovement(Vec3.ZERO);
		puff(level, fallback, mob.getBbHeight());
		return true;
	}

	private static void puff(ServerLevel level, Vec3 at, float height) {
		level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + height * 0.5, at.z, 18, 0.4, height * 0.35, 0.4, 0.01);
	}

	/** In water: stay at the surface and keep moving along the path (a big body would otherwise wallow). */
	public static void swim(Mob mob) {
		if (!mob.isInWater()) {
			return;
		}
		Vec3 v = mob.getDeltaMovement();
		double up = mob.getFluidHeight(net.minecraft.tags.FluidTags.WATER) > mob.getBbHeight() * 0.45 ? 0.06 : 0.0;
		Path path = mob.getNavigation().getPath();
		Vec3 push = Vec3.ZERO;
		if (path != null && !path.isDone()) {
			Vec3 next = Vec3.atBottomCenterOf(path.getNextNodePos());
			Vec3 to = next.subtract(mob.position()).multiply(1.0, 0.0, 1.0);
			if (to.lengthSqr() > 0.01) {
				push = to.normalize().scale(0.035);
			}
		}
		mob.setDeltaMovement(v.x + push.x, Math.min(0.25, v.y + up), v.z + push.z);
	}

	/**
	 * Climbing slowly: while on a wall, upward speed is held to {@code factor} of the mob's walking speed (per tick,
	 * taken as its movement speed), instead of a spider's quick scuttle.
	 */
	public static void climbSlowly(Mob mob, double factor) {
		Vec3 v = mob.getDeltaMovement();
		double up = Math.max(0.02, factor * mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED));
		if (v.y > up) {
			mob.setDeltaMovement(v.x * 0.5, up, v.z * 0.5);
		}
	}

	/** A sound a few blocks behind the prey, out of their view: heard only by them, as if right there. */
	public static void soundBehind(ServerLevel level, Player target, SoundEvent sound, float volume, float pitch, double distance) {
		Vec3 back = target.getViewVector(1.0F).multiply(-1.0, 0.0, -1.0);
		back = back.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : back.normalize();
		Vec3 at = target.position().add(back.yRot((float) ((target.getRandom().nextDouble() - 0.5) * 1.2)).scale(distance));
		if (target instanceof net.minecraft.server.level.ServerPlayer player) {
			player.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(
				net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), SoundSource.HOSTILE,
				at.x, at.y + 1.0, at.z, volume, pitch, target.getRandom().nextLong()));
		}
	}

	/**
	 * Notices a chaser that has stopped getting anywhere: no path to the prey, or no real progress toward
	 * them for {@code patience} ticks while more than a few blocks away.
	 */
	/**
	 * Every killer's last resort against getting stuck (the {@code killers} config section), ticked once a server tick:
	 *
	 * <ul>
	 *   <li><b>Rooted to the spot:</b> trying to walk somewhere (its path not finished) but no more than a block from where
	 *       it stood for 10 to 15 seconds (a fresh random limit each time), it vanishes in smoke and reappears 3 to 6 blocks
	 *       away, toward its prey if it has one. Standing still on purpose (lurking, staring, feeding, knocking) has no
	 *       path, so it doesn't count.</li>
	 *   <li><b>No way to them:</b> after a minute with no route to the survivor it is after (shut in, up a tower, across
	 *       water), it appears right beside them, on their side of any wall.</li>
	 * </ul>
	 */
	public static final class Unstick {
		private Vec3 anchor = Vec3.ZERO;
		private int still;
		private int limit = -1;
		private int unreachable;

		/** One tick; {@code target} is the survivor it is after (null when none). True if it teleported. */
		public boolean tick(Mob mob, ServerLevel level, @Nullable Player target, double height) {
			com.deadlands.config.DeadlandsConfig.Killers config = com.deadlands.config.ConfigManager.get().killers;
			RandomSource random = mob.getRandom();
			if (limit < 0) {
				limit = Math.round(config.stuckSecondsMin * 20.0F + random.nextFloat() * Math.max(0.0F, config.stuckSecondsMax - config.stuckSecondsMin) * 20.0F);
			}
			if (mob.position().distanceToSqr(anchor) > 1.0) {
				anchor = mob.position();
				still = 0;
			} else if (!mob.getNavigation().isDone() && !mob.isPassenger()) {
				still++;
			}
			if (config.hopWhenStuck && still > limit) {
				still = 0;
				limit = -1;
				if (hop(mob, level, target, 3.0, 6.0, height)) {
					anchor = mob.position();
					return true;
				}
			}
			if (target == null || !config.teleportWhenUnreachable) {
				unreachable = 0;
				return false;
			}
			Path path = mob.getNavigation().getPath();
			boolean noWay = mob.distanceTo(target) > 3.0 && (path == null ? mob.getNavigation().isDone() : !path.canReach());
			unreachable = noWay ? unreachable + 1 : 0;
			if (unreachable > config.unreachableSeconds * 20) {
				unreachable = 0;
				if (teleportBeside(mob, level, target, 2.5, 6.0, height) || teleportNear(mob, level, target, 6.0, 12.0, height)) {
					anchor = mob.position();
					still = 0;
					return true;
				}
			}
			return false;
		}
	}

	/** A short hop out of a stuck spot: a floor {@code near} to {@code far} blocks away, toward {@code toward} if given. */
	public static boolean hop(Mob mob, ServerLevel level, @Nullable Player toward, double near, double far, double height) {
		RandomSource random = mob.getRandom();
		int room = (int) Math.ceil(height);
		double bias = toward == null ? random.nextDouble() * Math.PI * 2.0
			: Math.atan2(toward.getZ() - mob.getZ(), toward.getX() - mob.getX());
		for (int attempt = 0; attempt < 16; attempt++) {
			double spread = toward == null ? Math.PI * 2.0 : Math.PI * (0.5 + attempt / 16.0); // widen if the way ahead is blocked
			double angle = bias + (random.nextDouble() - 0.5) * spread;
			double r = near + random.nextDouble() * (far - near);
			BlockPos floor = standable(level, BlockPos.containing(mob.getX() + Math.cos(angle) * r, mob.getY(), mob.getZ() + Math.sin(angle) * r), room);
			if (floor == null || floor.distToCenterSqr(mob.position()) < near * near * 0.5) {
				continue;
			}
			Vec3 at = Vec3.atBottomCenterOf(floor);
			puff(level, mob.position(), mob.getBbHeight());
			mob.teleportTo(at.x, at.y, at.z);
			mob.getNavigation().stop();
			mob.setDeltaMovement(Vec3.ZERO);
			puff(level, at, mob.getBbHeight());
			return true;
		}
		return false;
	}

	public static final class Unstuck {
		private Vec3 checkpoint = Vec3.ZERO;
		private double bestDistance = Double.MAX_VALUE;
		private int stuck;
		private int cooldown;

		public void reset() {
			stuck = 0;
			bestDistance = Double.MAX_VALUE;
		}

		/** One tick of the chase; true when it is time to teleport. */
		public boolean tick(Mob mob, Player target, int patience) {
			if (cooldown > 0) {
				cooldown--;
			}
			double distance = mob.distanceTo(target);
			Path path = mob.getNavigation().getPath();
			boolean noPath = path != null && !path.canReach() && path.isDone() || mob.getNavigation().isDone() && distance > 6.0;
			if (distance < bestDistance - 1.0 || distance < 5.0) {
				bestDistance = distance;
				stuck = 0;
			} else {
				stuck++;
			}
			if (mob.position().distanceToSqr(checkpoint) > 4.0) {
				checkpoint = mob.position();
			} else if (noPath) {
				stuck += 2;
			}
			if (cooldown == 0 && distance > 6.0 && stuck > patience) {
				cooldown = 200;
				reset();
				return true;
			}
			return false;
		}
	}
}
