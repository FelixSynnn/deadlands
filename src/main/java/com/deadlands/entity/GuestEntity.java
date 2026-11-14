package com.deadlands.entity;

import com.deadlands.Deadlands;
import com.deadlands.config.ConfigManager;
import com.deadlands.config.DeadlandsConfig;
import com.deadlands.registry.DLSounds;
import com.deadlands.registry.DLTags;
import com.deadlands.survival.SurvivalAttachments;
import com.deadlands.survival.SurvivalRules;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Guest (Phase 5): the thing that came out of Kessler's incinerator. It does not stalk: it spots a
 * survivor and comes straight for them, fast, and it does not retreat.
 *
 * <p>Its six abilities:
 * <ol>
 *   <li><b>The Knock</b>: a survivor sheltering indoors where it cannot get to them hears three hard knocks
 *       on the nearest door or wall first.</li>
 *   <li><b>Let Me In</b>: then it tears its way in, a 3x3 hole through doors, glass, wood and the like every
 *       second and a half (stone stops it; {@code guest.breaksBlocks} and the mobGriefing rule decide).</li>
 *   <li><b>Grab</b>: a one-second wind-up (a warning sound, arms spread), a lunge, and if it connects it lifts
 *       the survivor and slams them down ({@code guest.grabDamage}). A miss crashes it to a stagger.</li>
 *   <li><b>Swipe</b>: its claws, quick ({@code guest.damage}, 3.5: six hits kill an unarmoured survivor on
 *       Normal), with knockback.</li>
 *   <li><b>Fever</b>: it burns. Within six blocks thirst drains fast and you slow. Water is its weakness:
 *       rain, deep water or a splashed water bottle douses it, and it steams and slows.</li>
 *   <li><b>Feeding</b>: it eats the infected that die near it, healing and growing (up to a fifth bigger) as
 *       the night goes on.</li>
 * </ol>
 * It leaves at dawn (unseen, in smoke) and never despawns otherwise.
 */
public class GuestEntity extends Monster {
	public enum Mode {
		ROAM, HUNT, KNOCK, ENTER, FEED, STALK // append only
	}

	public enum Action {
		NONE, ROAR, SWIPE_WINDUP, SWIPE, GRAB_WINDUP, GRAB_LUNGE, GRAB_HOLD, SLAM, STUN, KNOCK, TEAR, FEED // append only
	}

	private static final EntityDataAccessor<Integer> DATA_MODE = SynchedEntityData.defineId(GuestEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_ACTION = SynchedEntityData.defineId(GuestEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_PREY = SynchedEntityData.defineId(GuestEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_CHARGING = SynchedEntityData.defineId(GuestEntity.class, EntityDataSerializers.BOOLEAN);
	private static final Identifier CHARGE = Deadlands.id("guest_charge");
	private static final Identifier DOUSED = Deadlands.id("guest_doused");
	private static final float MAX_GROWTH = 1.2F;

	private Mode mode = Mode.ROAM;
	private @Nullable UUID prey;
	private int modeTicks;
	private int unseenTicks;
	private @Nullable Vec3 lastKnown;
	private int actionLeft;
	private int swipeCooldown;
	private int grabCooldown = 60;
	private int windup;
	private int holdTicks;
	private int chargeLeft;
	private int chargeCooldown = 80;
	private int stunned;
	private int blockedTicks;
	private @Nullable BlockPos knockAt;
	private int knocks;
	private int tearCooldown;
	private int tearFailures;
	private boolean knockedThisApproach;
	private @Nullable Vec3 feedAt;
	private int doused;
	private int dayTicks;
	private long voiceUntil;
	private int stepTicks;
	// Stalking (it follows from cover first, then comes).
	private int stalkTicks;
	private int stalkPlan = 2400;
	private @Nullable Vec3 stalkSpot;
	private int replanIn;
	private int watchedTicks;
	private boolean slipping;
	private int creepIn = 400;
	private int stillTicks;
	private @Nullable Vec3 preyLastPos;
	/** Never stuck for long in a hunt: it comes back near them, out of sight. */
	private final com.deadlands.entity.ai.Stalking.Unstuck unstuck = new com.deadlands.entity.ai.Stalking.Unstuck();
	private int catchUpCooldown = 200;
	/** Client: when the current action began, and the eased run blend. */
	private int actionStartTick;
	private float run;
	private float runO;

	public GuestEntity(EntityType<? extends GuestEntity> type, Level level) {
		super(type, level);
		this.xpReward = 80;
		if (getNavigation() instanceof GroundPathNavigation ground) {
			ground.setCanOpenDoors(false);
			ground.getNodeEvaluator().setCanPassDoors(true);
			ground.setCanFloat(true);
		}
		// Swims: floats at the surface and paths through water rather than around it or sinking.
		setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.WATER, 2.0F);
		applyConfig();
		if (!level.isClientSide()) {
			setPersistenceRequired();
		}
	}

	public static AttributeSupplier.Builder createAttributes() {
		DeadlandsConfig.Guest config = ConfigManager.get().guest;
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, config.health)
			.add(Attributes.ATTACK_DAMAGE, config.damage)
			.add(Attributes.MOVEMENT_SPEED, config.speed)
			.add(Attributes.FOLLOW_RANGE, config.followRange)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
			.add(Attributes.ARMOR, config.armor)
			.add(Attributes.STEP_HEIGHT, 1.5);
	}

	private void applyConfig() {
		DeadlandsConfig.Guest config = ConfigManager.get().guest;
		getAttribute(Attributes.MAX_HEALTH).setBaseValue(config.health);
		getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(config.damage);
		getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(config.speed);
		getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(config.followRange);
		getAttribute(Attributes.ARMOR).setBaseValue(config.armor);
		setHealth(getMaxHealth());
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_MODE, 0);
		builder.define(DATA_ACTION, 0);
		builder.define(DATA_PREY, -1);
		builder.define(DATA_CHARGING, false);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);
		if (DATA_ACTION.equals(key)) {
			actionStartTick = tickCount;
		}
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
	}

	// ======================================================================================== the client's view

	public Mode mode() {
		return Mode.values()[Mth.clamp(entityData.get(DATA_MODE), 0, Mode.values().length - 1)];
	}

	public Action action() {
		return Action.values()[Mth.clamp(entityData.get(DATA_ACTION), 0, Action.values().length - 1)];
	}

	public float actionAge(float partialTick) {
		return tickCount - actionStartTick + partialTick;
	}

	/** The survivor it is hunting (the chase music's layer 4), or -1. */
	public int huntingId() {
		return entityData.get(DATA_PREY);
	}

	public boolean charging() {
		return entityData.get(DATA_CHARGING);
	}

	public float run(float partialTick) {
		return Mth.lerp(partialTick, runO, run);
	}

	private void act(Action action, int ticks) {
		entityData.set(DATA_ACTION, action.ordinal());
		actionLeft = ticks;
	}

	/** Set it on a survivor (the spawner does this as it arrives): it screams, and comes. */
	public void hunt(ServerLevel level, Player player, boolean arrival) {
		prey = player.getUUID();
		lastKnown = player.position();
		setMode(Mode.HUNT);
		act(Action.ROAR, 40);
		voice(level, arrival ? DLSounds.GUEST_ARRIVE.value() : DLSounds.GUEST_ROAR.value(), 2.5F, 1.0F, 60);
		if (distanceTo(player) < 24.0) {
			// Its scream roots them to the spot for a second.
			player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 25, 3, true, false));
		}
	}

	/**
	 * Start stalking a survivor (the spawner does this as it arrives, far off): it follows from cover for one
	 * and a half to three minutes, closing in, freezing when looked at and slipping out of sight, before it
	 * comes (sooner if they are hurt, alone in the dark close by, or walk right up to it).
	 */
	public void stalk(ServerLevel level, Player player) {
		prey = player.getUUID();
		lastKnown = player.position();
		setMode(Mode.STALK);
		stalkTicks = 0;
		stalkPlan = 1800 + random.nextInt(1800);
		stalkSpot = null;
		voice(level, DLSounds.GUEST_SENSE.value(), 1.2F, 0.85F, 40);
	}

	private void stalkTick(ServerLevel level, Player target) {
		double distance = distanceTo(target);
		if (distance > 128.0) {
			setMode(Mode.ROAM);
			return;
		}
		stalkTicks++;
		lastKnown = target.position();
		boolean watched = com.deadlands.entity.ai.Stalking.watching(target, this);
		Vec3 now = target.position();
		stillTicks = preyLastPos != null && now.distanceToSqr(preyLastPos) < 0.02 ? stillTicks + 1 : 0;
		preyLastPos = now;
		float progress = Math.min(1.0F, stalkTicks / (float) stalkPlan);
		// When it comes.
		boolean hurtBy = getLastHurtByMob() == target && tickCount - getLastHurtByMobTimestamp() < 40;
		boolean opening = stalkTicks > stalkPlan / 3 && (target.getHealth() < target.getMaxHealth() * 0.5F
			|| !watched && distance < 14.0 && com.deadlands.entity.ai.Stealth.light(target) <= 7);
		if (hurtBy || distance < 6.0 || opening || stalkTicks >= stalkPlan && distance < 40.0) {
			hunt(level, target, true);
			return;
		}
		getLookControl().setLookAt(target, 20.0F, 20.0F);
		// Caught looking: it freezes, low to the ground, growls, then melts away out of sight.
		if (watched && !slipping) {
			if (++watchedTicks == 1) {
				voice(level, DLSounds.GUEST_AMBIENT.value(), 0.9F, 0.7F, 60);
			}
			if (watchedTicks < 35) {
				getNavigation().stop();
				return;
			}
			slipping = true;
			stalkSpot = com.deadlands.entity.ai.Stalking.hiddenSpot(this, level, target, 22.0, 34.0, true, 3.3);
			replanIn = 60;
		}
		if (!watched) {
			watchedTicks = 0;
		}
		// A sniff or a heavy step just behind them, out of view.
		if (!watched && distance < 30.0 && --creepIn <= 0) {
			creepIn = 600 + random.nextInt(900);
			com.deadlands.entity.ai.Stalking.soundBehind(level, target, random.nextBoolean() ? DLSounds.GUEST_SENSE.value() : DLSounds.GUEST_STEP.value(),
				0.5F, 0.85F, 5.0 + random.nextDouble() * 4.0);
		}
		// It closes in as the stalk goes on: 20-32 blocks at first, 10-18 near the end.
		double near = 20.0 - 10.0 * progress;
		double far = 32.0 - 14.0 * progress;
		if (--replanIn <= 0 || stalkSpot == null) {
			replanIn = 40 + random.nextInt(20);
			slipping = false;
			Vec3 spot = com.deadlands.entity.ai.Stalking.hiddenSpot(this, level, target, near, far, false, 3.3);
			if (spot != null) {
				stalkSpot = spot;
			}
		}
		if (stalkSpot == null) {
			return;
		}
		// When they stand still, so does it (if they can't see it).
		if (stillTicks > 15 && !slipping && distance < far + 6.0 && !com.deadlands.entity.ai.Stalking.visibleFrom(target, position(), 3.0)) {
			getNavigation().stop();
			return;
		}
		if (position().distanceToSqr(stalkSpot) < 3.0) {
			getNavigation().stop();
			return;
		}
		double pace = slipping ? 1.15 : distance > 36.0 ? 1.0 : 0.6;
		if (getNavigation().isDone() || (tickCount + getId()) % 10 == 0) {
			getNavigation().moveTo(stalkSpot.x, stalkSpot.y, stalkSpot.z, pace);
		}
	}

	// ======================================================================================== the mind

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		com.deadlands.entity.ai.Stalking.swim(this);
		catchUpCooldown = Math.max(0, catchUpCooldown - 1);
		modeTicks++;
		swipeCooldown = Math.max(0, swipeCooldown - 1);
		grabCooldown = Math.max(0, grabCooldown - 1);
		chargeCooldown = Math.max(0, chargeCooldown - 1);
		tearCooldown = Math.max(0, tearCooldown - 1);
		if (actionLeft > 0 && --actionLeft == 0) {
			entityData.set(DATA_ACTION, Action.NONE.ordinal());
		}
		if (morning(level)) {
			return;
		}
		tickCharge();
		tickDoused(level);
		fever(level);
		if (stunned > 0) {
			stunned--;
			getNavigation().stop();
			return;
		}
		Player target = preyPlayer(level);
		if (target == null) {
			holdTicks = 0; // the survivor is gone (dead, left): nothing half-done carries over
			windup = 0;
		}
		if (holdTicks > 0 && target != null) {
			tickHold(level, target);
			return;
		}
		if (windup != 0 && target != null) {
			tickWindup(level, target);
			return;
		}
		if (mode != Mode.ROAM && mode != Mode.FEED && target == null) {
			setMode(Mode.ROAM);
		}
		switch (mode) {
			case ROAM -> roam(level);
			case HUNT -> hunt(level, target);
			case KNOCK -> knock(level, target);
			case ENTER -> enter(level, target);
			case FEED -> feed(level);
			case STALK -> stalkTick(level, target);
		}
		entityData.set(DATA_PREY, target != null && mode != Mode.ROAM && mode != Mode.FEED && mode != Mode.STALK ? target.getId() : -1);
	}

	private void setMode(Mode next) {
		if (mode == next) {
			return;
		}
		mode = next;
		modeTicks = 0;
		unseenTicks = 0;
		blockedTicks = 0;
		entityData.set(DATA_MODE, next.ordinal());
		if (next == Mode.ROAM) {
			prey = null;
			knockedThisApproach = false;
		}
		getNavigation().stop();
	}

	private @Nullable Player preyPlayer(ServerLevel level) {
		if (prey == null) {
			return null;
		}
		Player player = level.getPlayerByUUID(prey);
		return player != null && huntable(player) && player.level() == level() ? player : null;
	}

	private static boolean huntable(Player player) {
		return player.isAlive() && !player.isSpectator() && !player.isCreative();
	}

	private void roam(ServerLevel level) {
		if (feedAt != null) {
			setMode(Mode.FEED);
			return;
		}
		if ((tickCount + getId()) % 10 != 0) {
			return;
		}
		Player best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Player player : level.players()) {
			if (!huntable(player)) {
				continue;
			}
			double distance = distanceTo(player);
			double sight = com.deadlands.entity.ai.Stealth.rangeFactor(player); // darkness and crouching hide them (Stealth)
			if (distance < bestDistance && (distance < 24.0 * sight || distance < getAttributeValue(Attributes.FOLLOW_RANGE) * sight && hasLineOfSight(player))) {
				best = player;
				bestDistance = distance;
			}
		}
		if (best != null) {
			if (bestDistance < 8.0) {
				level.playSound(null, getX(), getY(), getZ(), DLSounds.GUEST_SENSE.value(), SoundSource.HOSTILE, 2.0F, 1.0F);
				hunt(level, best, false);
			} else {
				stalk(level, best);
			}
		} else if (getNavigation().isDone() && random.nextInt(60) == 0) {
			Vec3 wander = position().add(random.nextGaussian() * 12.0, 0.0, random.nextGaussian() * 12.0);
			getNavigation().moveTo(wander.x, wander.y, wander.z, 0.8);
		}
	}

	/** Straight at them: charge in bursts, swipe, grab. It knocks and tears its way in if they are shut inside. */
	private void hunt(ServerLevel level, Player target) {
		double distance = distanceTo(target);
		if (distance > 128.0) {
			setMode(Mode.ROAM);
			return;
		}
		boolean seen = hasLineOfSight(target);
		if (seen) {
			lastKnown = target.position();
			unseenTicks = 0;
			knockedThisApproach = false;
		} else if (++unseenTicks > 900 || com.deadlands.entity.ai.Stealth.inDarkness(target)
			&& unseenTicks > com.deadlands.entity.ai.Stealth.loseTicks() + com.deadlands.entity.ai.Stealth.searchTicks()) {
			// Forty-five seconds without a trace, or lost in the dark and not found where they were last seen.
			setMode(Mode.ROAM);
			return;
		}
		getLookControl().setLookAt(target, 40.0F, 40.0F);
		if (grabCooldown == 0 && seen && distance < 4.2 && distance > 1.2) {
			windup = 20;
			act(Action.GRAB_WINDUP, 20);
			level.playSound(null, getX(), getY(), getZ(), DLSounds.GUEST_GRAB_WARN.value(), SoundSource.HOSTILE, 2.0F, 1.0F);
			level.playSound(null, getX(), getY(), getZ(), DLSounds.GUEST_GRAB_PREPARE.value(), SoundSource.HOSTILE, 1.5F, 1.0F);
			getNavigation().stop();
			return;
		}
		if (swipeCooldown == 0 && seen && distance < 3.0) {
			windup = -6; // negative: a swipe's wind-up
			act(Action.SWIPE_WINDUP, 6);
			level.playSound(null, getX(), getY(), getZ(), DLSounds.GUEST_SWING.value(), SoundSource.HOSTILE, 1.4F, 0.9F + random.nextFloat() * 0.2F);
			getNavigation().stop();
			return;
		}
		if (chargeCooldown == 0 && seen && distance > 7.0 && distance < 40.0) {
			chargeLeft = 60;
			chargeCooldown = 140;
		}
		// Shut in: it cannot reach them. Knock, then come in.
		Path path = getNavigation().getPath();
		boolean reachable = path == null || path.canReach() || distance < 3.0;
		blockedTicks = reachable && !horizontalCollision ? 0 : blockedTicks + 1;
		if (blockedTicks > 30 && distance < 20.0) {
			BlockPos wall = wallTowards(level, target);
			if (wall != null) {
				knockAt = wall;
				knocks = 0;
				setMode(knockedThisApproach ? Mode.ENTER : Mode.KNOCK);
				knockedThisApproach = true;
				return;
			}
		}
		if (mode == Mode.HUNT && (unstuck.tick(this, target, 80) || distance > 40.0 && seen && catchUpCooldown == 0)) {
			catchUpCooldown = 300;
			if (com.deadlands.entity.ai.Stalking.teleportNear(this, level, target, 8.0, 14.0, 3.3)) {
				voice(level, DLSounds.GUEST_SENSE.value(), 1.5F, 0.8F, 40);
				return;
			}
		}
		if ((tickCount + getId()) % 4 == 0 || getNavigation().isDone()) {
			Vec3 aim = seen ? target.position().add(target.getDeltaMovement().multiply(6.0, 0.0, 6.0)) : lastKnown;
			if (aim != null) {
				getNavigation().moveTo(aim.x, aim.y, aim.z, 1.0);
			}
		}
	}

	private void tickCharge() {
		boolean charging = chargeLeft > 0 && mode == Mode.HUNT;
		if (charging) {
			chargeLeft--;
			double extra = ConfigManager.get().guest.chargeSpeed - ConfigManager.get().guest.speed;
			getAttribute(Attributes.MOVEMENT_SPEED).addOrReplacePermanentModifier(new AttributeModifier(CHARGE, extra, AttributeModifier.Operation.ADD_VALUE));
		} else {
			getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(CHARGE);
		}
		if (charging != entityData.get(DATA_CHARGING)) {
			entityData.set(DATA_CHARGING, charging);
		}
	}

	// ---------------------------------------------------------------------------------------- swipe and grab

	private void tickWindup(ServerLevel level, Player target) {
		getNavigation().stop();
		getLookControl().setLookAt(target, 60.0F, 60.0F);
		if (windup < 0) {
			if (++windup == 0) {
				act(Action.SWIPE, 8);
				swing(net.minecraft.world.InteractionHand.MAIN_HAND);
				if (distanceTo(target) < 3.4) {
					strike(level, target, (float) getAttributeValue(Attributes.ATTACK_DAMAGE), 1.1);
				}
				swipeCooldown = 30;
			}
			return;
		}
		if (--windup == 0) {
			// The lunge.
			act(Action.GRAB_LUNGE, 8);
			Vec3 to = target.position().subtract(position()).multiply(1.0, 0.0, 1.0).normalize();
			setDeltaMovement(to.x * 0.9, 0.2, to.z * 0.9);
			hurtMarked = true;
			level.playSound(null, getX(), getY(), getZ(), DLSounds.GUEST_GRAB_LUNGE.value(), SoundSource.HOSTILE, 1.8F, 1.0F);
			grabCooldown = 160;
			if (distanceTo(target) < 4.6) {
				holdTicks = 22;
				act(Action.GRAB_HOLD, 22);
			} else {
				stunned = 30;
				act(Action.STUN, 30);
				level.playSound(null, getX(), getY(), getZ(), DLSounds.GUEST_GRAB_MISS.value(), SoundSource.HOSTILE, 1.8F, 1.0F);
			}
		}
	}

	/** Holding them up, then the slam. */
	private void tickHold(ServerLevel level, Player target) {
		getNavigation().stop();
		Vec3 front = position().add(getLookAngle().multiply(1.0, 0.0, 1.0).normalize().scale(1.7 * getScale())).add(0.0, 1.6 * getScale(), 0.0);
		target.teleportTo(front.x, front.y, front.z);
		target.setDeltaMovement(Vec3.ZERO);
		target.hurtMarked = true;
		if (--holdTicks == 6) {
			act(Action.SLAM, 12);
		}
		if (holdTicks == 0) {
			level.playSound(null, getX(), getY(), getZ(), DLSounds.GUEST_SLAM.value(), SoundSource.HOSTILE, 2.2F, 1.0F);
			level.playSound(null, getX(), getY(), getZ(), DLSounds.GUEST_GRAB_HIT.value(), SoundSource.HOSTILE, 2.0F, 1.0F);
			Vec3 ground = position().add(getLookAngle().multiply(1.0, 0.0, 1.0).normalize().scale(1.5));
			target.teleportTo(ground.x, getY(), ground.z);
			strike(level, target, (float) ConfigManager.get().guest.grabDamage, 0.6);
			level.sendParticles(ParticleTypes.EXPLOSION, ground.x, getY() + 0.2, ground.z, 1, 0, 0, 0, 0);
		}
	}

	private void strike(ServerLevel level, Player target, float damage, double knock) {
		if (target.hurtServer(level, damageSources().mobAttack(this), damage)) {
			Vec3 push = target.position().subtract(position()).multiply(1.0, 0.0, 1.0).normalize().scale(knock);
			target.push(push.x, 0.25, push.z);
			target.hurtMarked = true;
			if (!target.isAlive()) {
				voice(level, DLSounds.GUEST_KILL.value(), 2.5F, 1.0F, 80);
				setMode(Mode.ROAM);
			}
		}
	}

	// ---------------------------------------------------------------------------------------- the knock, and coming in

	/** The first block between it and the survivor (a door, a wall), within reach. */
	private @Nullable BlockPos wallTowards(ServerLevel level, Player target) {
		for (double height : new double[] {1.0, 0.2, 2.0}) {
			Vec3 from = position().add(0.0, height, 0.0);
			Vec3 to = target.position().add(0.0, Math.min(height, 1.6), 0.0);
			BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
			if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().distToCenterSqr(position()) < 5.0 * 5.0) {
				return hit.getBlockPos();
			}
		}
		return null;
	}

	private void knock(ServerLevel level, @Nullable Player target) {
		getNavigation().stop();
		if (knockAt != null) {
			getLookControl().setLookAt(Vec3.atCenterOf(knockAt));
		}
		if (modeTicks % 18 == 1 && knocks < 3) {
			knocks++;
			act(Action.KNOCK, 14);
			BlockPos at = knockAt != null ? knockAt : blockPosition();
			level.playSound(null, at, DLSounds.GUEST_KNOCK.value(), SoundSource.HOSTILE, 2.5F, 0.8F + random.nextFloat() * 0.1F);
		}
		if (modeTicks > 60) {
			setMode(Mode.ENTER);
		}
	}

	private void enter(ServerLevel level, @Nullable Player target) {
		if (target == null) {
			setMode(Mode.ROAM);
			return;
		}
		getNavigation().stop();
		getLookControl().setLookAt(target, 30.0F, 30.0F);
		Path path = getNavigation().createPath(target, 0);
		if (path != null && path.canReach() || distanceTo(target) < 3.0) {
			setMode(Mode.HUNT); // in
			return;
		}
		if (tearCooldown > 0) {
			return;
		}
		tearCooldown = 30;
		BlockPos centre = wallTowards(level, target);
		if (centre == null || !tear(level, centre)) {
			if (++tearFailures > 6) {
				tearFailures = 0;
				setMode(Mode.HUNT); // stone: it cannot get in here; it circles, waiting
				unseenTicks = 600;
			}
			return;
		}
		tearFailures = 0;
	}

	/** Tear a 3x3 hole around {@code centre} (facing it) through what it can break. True if anything gave way. */
	private boolean tear(ServerLevel level, BlockPos centre) {
		if (!ConfigManager.get().guest.breaksBlocks || !level.getGameRules().get(GameRules.MOB_GRIEFING)) {
			return false;
		}
		act(Action.TEAR, 20);
		boolean any = false;
		Vec3 facing = Vec3.atCenterOf(centre).subtract(position()).multiply(1.0, 0.0, 1.0).normalize();
		boolean alongX = Math.abs(facing.z) > Math.abs(facing.x);
		for (int a = -1; a <= 1; a++) {
			for (int dy = -1; dy <= 1; dy++) {
				BlockPos pos = alongX ? centre.offset(a, dy, 0) : centre.offset(0, dy, a);
				BlockState state = level.getBlockState(pos);
				if (!state.isAir() && tearable(level, pos, state)) {
					level.destroyBlock(pos, true, this);
					any = true;
				}
			}
		}
		if (any) {
			level.playSound(null, centre, DLSounds.GUEST_TEAR.value(), SoundSource.HOSTILE, 2.0F, 0.75F);
		}
		return any;
	}

	private static boolean tearable(ServerLevel level, BlockPos pos, BlockState state) {
		float hardness = state.getDestroySpeed(level, pos);
		if (hardness < 0.0F) {
			return false; // bedrock and the like
		}
		return state.is(DLTags.INFECTED_BREAKABLE) || state.is(DLTags.BRUTE_BREAKABLE) || state.is(BlockTags.DOORS) || state.is(BlockTags.TRAPDOORS)
			|| state.is(BlockTags.PLANKS) || state.is(BlockTags.LOGS) || state.is(BlockTags.WOOL) || state.is(BlockTags.FENCES)
			|| state.is(BlockTags.FENCE_GATES) || state.is(BlockTags.LEAVES) || state.is(BlockTags.IMPERMEABLE) || hardness <= 0.8F;
	}

	// ---------------------------------------------------------------------------------------- feeding

	/** An infected died near it: it may go and feed. */
	public void bodyNear(Vec3 at) {
		if (feedAt == null && (mode == Mode.ROAM || mode == Mode.HUNT && lastKnown != null && lastKnown.distanceTo(position()) > 24.0)) {
			feedAt = at;
		}
	}

	private void feed(ServerLevel level) {
		if (feedAt == null) {
			setMode(Mode.ROAM);
			return;
		}
		if (position().distanceToSqr(feedAt) > 2.5 * 2.5) {
			if (getNavigation().isDone() || modeTicks % 20 == 0) {
				getNavigation().moveTo(feedAt.x, feedAt.y, feedAt.z, 1.0);
			}
			if (modeTicks > 400) {
				feedAt = null; // could not get there
			}
			return;
		}
		getNavigation().stop();
		if (action() != Action.FEED) {
			act(Action.FEED, 60);
		}
		if (modeTicks % 15 == 0) {
			level.playSound(null, getX(), getY(), getZ(), DLSounds.GUEST_FEED.value(), SoundSource.HOSTILE, 1.4F, 0.9F + random.nextFloat() * 0.2F);
		}
		if (actionLeft == 1) {
			heal(40.0F);
			double scale = Math.min(MAX_GROWTH, getAttribute(Attributes.SCALE).getBaseValue() + 0.05);
			getAttribute(Attributes.SCALE).setBaseValue(scale);
			level.playSound(null, getX(), getY(), getZ(), DLSounds.GUEST_HEAL.value(), SoundSource.HOSTILE, 1.4F, 1.0F);
			feedAt = null;
			setMode(prey != null ? Mode.HUNT : Mode.ROAM);
		}
	}

	// ---------------------------------------------------------------------------------------- fever and water

	/** Within six blocks (more as it grows): thirst drains and you slow. */
	private void fever(ServerLevel level) {
		if (tickCount % 20 != 0 || doused > 0) {
			return;
		}
		double reach = 6.0 * getScale();
		boolean thirst = SurvivalRules.thirst();
		for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(reach), GuestEntity::huntable)) {
			if (player.distanceTo(this) > reach) {
				continue;
			}
			if (thirst) {
				SurvivalAttachments.get(player).addThirst(-2.5F);
			}
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 0, true, false), this);
		}
		if (tickCount % 120 == 0) {
			level.playSound(null, getX(), getY(), getZ(), DLSounds.GUEST_FEVER.value(), SoundSource.HOSTILE, 0.7F, 1.0F);
		}
	}

	@Override
	public boolean isSensitiveToWater() {
		return true; // rain and water douse it (handled in hurtServer: no damage, it steams and slows)
	}

	private void douse(ServerLevel level) {
		if (doused <= 0) {
			level.playSound(null, getX(), getY(), getZ(), DLSounds.DISASTER_CAUSTIC_BURN.value(), SoundSource.HOSTILE, 1.5F, 0.7F);
		}
		doused = 100;
		getAttribute(Attributes.MOVEMENT_SPEED).addOrReplacePermanentModifier(
			new AttributeModifier(DOUSED, -0.35, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
	}

	private void tickDoused(ServerLevel level) {
		if (doused > 0) {
			doused--;
			if (tickCount % 3 == 0) {
				level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 1.5 * getScale(), getZ(), 2, 0.4, 0.8, 0.4, 0.02);
			}
			if (doused == 0) {
				getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(DOUSED);
			}
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (source.is(DamageTypes.DROWN) || source.getDirectEntity() instanceof AbstractThrownPotion && amount <= 1.0F) {
			douse(level);
			return false;
		}
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && source.getEntity() instanceof Player player && huntable(player) && (mode == Mode.ROAM || mode == Mode.FEED || mode == Mode.STALK || prey == null)) {
			hunt(level, player, false);
		}
		return hurt;
	}

	// ---------------------------------------------------------------------------------------- dawn

	/** It leaves at dawn: once it is light and no one is looking, in smoke (after a minute regardless). */
	private boolean morning(ServerLevel level) {
		long time = Math.floorMod(level.getDayTime(), 24000L);
		boolean day = time < 12500L && ConfigManager.get().guest.nightOnly;
		dayTicks = day ? dayTicks + 1 : 0;
		if (!day || holdTicks > 0) {
			return false;
		}
		boolean seen = false;
		for (Player player : level.players()) {
			if (huntable(player) && player.distanceToSqr(this) < 48.0 * 48.0 && player.hasLineOfSight(this)) {
				Vec3 to = getEyePosition().subtract(player.getEyePosition()).normalize();
				if (player.getViewVector(1.0F).dot(to) > 0.75) {
					seen = true;
					break;
				}
			}
		}
		if (seen && dayTicks < 1200) {
			return false;
		}
		level.playSound(null, getX(), getY(), getZ(), DLSounds.GUEST_LEAVE.value(), SoundSource.HOSTILE, 2.0F, 1.0F);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.6, getZ(), 40, 0.6, 1.2, 0.6, 0.02);
		level.sendParticles(ParticleTypes.FLAME, getX(), getY() + 1.2, getZ(), 30, 0.6, 1.0, 0.6, 0.02);
		discard();
		return true;
	}

	// ======================================================================================== sound

	private void voice(ServerLevel level, SoundEvent sound, float volume, float pitch, int ticks) {
		long now = level.getGameTime();
		if (now < voiceUntil) {
			return;
		}
		voiceUntil = now + ticks;
		level.playSound(null, getX(), getY(), getZ(), sound, SoundSource.HOSTILE, volume, pitch);
	}

	@Override
	public void playAmbientSound() {
		if (level() instanceof ServerLevel level) {
			voice(level, DLSounds.GUEST_AMBIENT.value(), 1.2F, 1.0F, 80);
		}
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return DLSounds.GUEST_AMBIENT.value();
	}

	@Override
	public int getAmbientSoundInterval() {
		return 300;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return DLSounds.GUEST_HURT.value();
	}

	@Override
	protected SoundEvent getDeathSound() {
		return DLSounds.GUEST_DEATH.value();
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		// Paced in tick().
	}

	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide()) {
			climbing = horizontalCollision && mode != Mode.ROAM && mode != Mode.FEED; // up walls, fences, cliffs: whatever is between it and them
		}
		if (level().isClientSide()) {
			float moving = (float) Math.min(1.0, getDeltaMovement().horizontalDistance() / 0.25);
			runO = run;
			run = Mth.approach(run, charging() || mode() == Mode.HUNT && moving > 0.6F ? moving : 0.0F, 0.08F);
			return;
		}
		double speed = getDeltaMovement().horizontalDistance();
		if (!onGround() || speed < 0.03) {
			stepTicks = 0;
			return;
		}
		int every = speed > 0.22 ? 6 : speed > 0.12 ? 9 : 13;
		if (++stepTicks % every == 0) {
			level().playSound(null, getX(), getY(), getZ(), DLSounds.GUEST_STEP.value(), SoundSource.HOSTILE, 1.1F, 0.9F + random.nextFloat() * 0.15F);
		}
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public void checkDespawn() {
		// Only the morning takes it.
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	static List<GuestEntity> near(ServerLevel level, Vec3 at, double radius) {
		return level.getEntitiesOfClass(GuestEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(radius), GuestEntity::isAlive);
	}

	// ---------------------------------------------------------------------------------------- climbing

	/** Climbing like a spider when it presses into a wall while after someone (set on the server each tick). */
	private boolean climbing;

	@Override
	protected net.minecraft.world.entity.ai.navigation.PathNavigation createNavigation(net.minecraft.world.level.Level level) {
		return new net.minecraft.world.entity.ai.navigation.WallClimberNavigation(this, level); // paths go up walls too
	}

	@Override
	public boolean onClimbable() {
		return climbing || super.onClimbable();
	}

	/** Falls don't hurt it: a long drop off a roof won't stop it. */
	@Override
	public boolean isInvulnerableTo(ServerLevel level, net.minecraft.world.damagesource.DamageSource source) {
		return source.is(net.minecraft.tags.DamageTypeTags.IS_FALL) || super.isInvulnerableTo(level, source);
	}

}
