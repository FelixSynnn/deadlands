package com.deadlands.entity;

import com.deadlands.config.ConfigManager;
import com.deadlands.config.DeadlandsConfig;
import com.deadlands.entity.ai.Stalking;
import com.deadlands.entity.ai.Stealth;
import com.deadlands.registry.DLSounds;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * D.O.T. (Deceased On Transfer), Patient-4 of Kessler's LANTERN trial. He walks; he doesn't hunt. Most nights he
 * pays survivors no attention at all. Give him a reason and he comes, and nothing stops him.
 *
 * <p><b>Provoked</b> by: hitting him; standing in his way (within about 2 blocks in front of him); looking straight at
 * him for {@code dot.stareSeconds}; or being one of the names in {@code dot.alwaysProvokes} (on sight; he also keeps
 * after them twice as long). Provoked, he follows and strikes (10 on Normal), but gives up after
 * {@code dot.giveUpSeconds} if the survivor stays away and out of his sight (darkness and crouching hide them, as for
 * every killer). Then he walks on as if nothing happened.
 *
 * <p><b>Abilities</b> while provoked:
 * <ul>
 *   <li><b>Static Step</b> (12 s): out of their sight, he glitches 6 to 8 blocks closer, leaving an afterimage.</li>
 *   <li><b>Restore Point</b> (60 s): after losing 40% of his health within 5 s, he snaps back to where he stood 3 s
 *       earlier, at the health he had then.</li>
 *   <li><b>Error Grasp</b> (18 s): a one-second wind-up, then a grab within 3 blocks: rooted 1.5 s, and a full hit.</li>
 *   <li><b>Corruption Field</b> (25 s): for 6 s, within 6 blocks: flashlights stutter, sickness, his music skips.</li>
 * </ul>
 *
 * <p><b>Unstoppable</b>: no knockback, no slowing or other harmful effects, no stuns, freezing, fire or fall damage;
 * walks through doors, fences, panes and leaves (breaking them); won't be put in a boat or a minecart; swims hard; and
 * if he is stuck he is somewhere else, near them, out of sight. Either invulnerable ({@code dot.invulnerable}) or, by
 * default, very high health with heavy damage resistance. Running and hiding is how you live.
 *
 * <p>He walks at night only, and leaves at dawn.
 */
public class DotEntity extends Monster {
	public enum Mode {
		WALK, STAND, PROVOKED // append only
	}

	public enum Action {
		NONE, SWING, GRASP_WINDUP, GRASP, STATIC_STEP, RESTORE, FIELD, TWITCH, LOOM, SWING2, THRUST, SCREAM, YELL, POUNCE_CROUCH, POUNCE // append only
	}

	private static final EntityDataAccessor<Integer> DATA_MODE = SynchedEntityData.defineId(DotEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_ACTION = SynchedEntityData.defineId(DotEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_PREY = SynchedEntityData.defineId(DotEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_CLIMBING = SynchedEntityData.defineId(DotEntity.class, EntityDataSerializers.BOOLEAN);

	private Mode mode = Mode.WALK;
	private @Nullable UUID prey;
	private int lostTicks;
	private int actionLeft;
	private int swingCooldown;
	/** Which cut comes next: 0 the overhead slash, 1 the backhand, 2 the thrust. */
	private int combo;
	/** The scream when he spots someone (he stops for it), the yells while he runs, the pounce. */
	private int screamLeft;
	private int yellIn = 160;
	private int pounceCooldown = 100;
	private int pounceWindup;
	private int pounceTicks;
	private boolean pounceHit;
	private int windup;
	private int staticCooldown = 120;
	private int restoreCooldown;
	private int graspCooldown = 120;
	private int graspWindup;
	private int graspHold;
	private int fieldCooldown = 200;
	private int fieldLeft;
	private int stepTicks;
	private long voiceUntil;
	private int dayTicks;
	private int twitchIn = 200;
	private final Map<UUID, Integer> stares = new HashMap<>();
	/** Where he was and how hurt, every 5 ticks for the last 3 seconds (Restore Point). */
	private final Deque<Snapshot> history = new ArrayDeque<>();
	/** Damage taken in the last 5 seconds: (game time, amount). */
	private final Deque<float[]> recentDamage = new ArrayDeque<>();
	private final Stalking.Unstuck unstuck = new Stalking.Unstuck();
	/** Client: when the current action began. */
	private int actionStartTick;
	/** Jumpscares: whether the prey was looking at him last tick (Wrong Frame), ticks until he blinks away after
	 * Behind the Light, and ticks left as a Road Ghost (then he is gone). */
	private boolean preyWasWatching = true;
	private int blinkAwayIn;
	private int ghostTicks;
	/** Client: eased blends (previous and current tick) between walking and provoked, and onto a wall. */
	private float aggro;
	private float aggroO;
	private float climb;
	private float climbO;

	public float aggro(float partialTick) {
		return Mth.lerp(partialTick, aggroO, aggro);
	}

	public float climb(float partialTick) {
		return Mth.lerp(partialTick, climbO, climb);
	}

	/** Client: how far through a stride he is (0 right foot down, 0.5 left), shared by the prowl and the sprint. */
	private float walkPhase;
	private float walkPhaseO;

	public float walkPhase(float partialTick) {
		return Mth.lerp(partialTick, walkPhaseO, walkPhase);
	}

	private record Snapshot(Vec3 pos, float health) {
	}

	public DotEntity(EntityType<? extends DotEntity> type, Level level) {
		super(type, level);
		this.xpReward = 120;
		if (getNavigation() instanceof GroundPathNavigation ground) {
			ground.setCanOpenDoors(true);
			ground.getNodeEvaluator().setCanPassDoors(true);
			ground.setCanFloat(true);
		}
		// Through water, fences and leaves rather than around them.
		setPathfindingMalus(PathType.WATER, 0.0F);
		setPathfindingMalus(PathType.LEAVES, 0.0F);
		setPathfindingMalus(PathType.FENCE, 0.0F);
		setPathfindingMalus(PathType.DOOR_WOOD_CLOSED, 0.0F);
		applyConfig();
		if (!level.isClientSide()) {
			setPersistenceRequired();
		}
	}

	public static AttributeSupplier.Builder createAttributes() {
		DeadlandsConfig.Dot config = ConfigManager.get().dot;
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, config.health)
			.add(Attributes.ATTACK_DAMAGE, config.damage)
			.add(Attributes.MOVEMENT_SPEED, config.speed)
			.add(Attributes.FOLLOW_RANGE, 48.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
			.add(Attributes.ARMOR, 10.0)
			.add(Attributes.STEP_HEIGHT, 1.25);
	}

	private void applyConfig() {
		DeadlandsConfig.Dot config = ConfigManager.get().dot;
		getAttribute(Attributes.MAX_HEALTH).setBaseValue(config.health);
		getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(config.damage);
		getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(config.speed);
		setHealth(getMaxHealth());
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_MODE, 0);
		builder.define(DATA_ACTION, 0);
		builder.define(DATA_PREY, -1);
		builder.define(DATA_CLIMBING, false);
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

	@Override
	protected PathNavigation createNavigation(Level level) {
		return new WallClimberNavigation(this, level); // paths go up walls too
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

	/** The id of the survivor he is after, or -1. */
	public int preyId() {
		return entityData.get(DATA_PREY);
	}

	public boolean climbing() {
		return entityData.get(DATA_CLIMBING);
	}

	private void act(Action action, int ticks) {
		entityData.set(DATA_ACTION, action.ordinal());
		actionLeft = ticks;
	}

	private void setMode(Mode next) {
		if (mode != next) {
			mode = next;
			entityData.set(DATA_MODE, next.ordinal());
			getNavigation().stop();
		}
	}

	/** A Road Ghost: standing in the road for a moment, then gone (DotSpawner). */
	public void ghost(Player facing, int ticks) {
		standInTheWay(facing);
		ghostTicks = ticks;
	}

	/** Put him standing in the road (the spawner), facing the survivor he is in the way of. */
	public void standInTheWay(Player facing) {
		setMode(Mode.STAND);
		lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, facing.getEyePosition());
		setYBodyRot(getYRot());
		setYHeadRot(getYRot());
	}

	// ======================================================================================== the mind

	private static boolean huntable(Player player) {
		return player.isAlive() && !player.isSpectator() && !player.isCreative();
	}

	/** Whether this survivor's name is one that always provokes him. */
	public static boolean alwaysProvokes(Player player) {
		String names = ConfigManager.get().dot.alwaysProvokes;
		String name = player.getGameProfile().name().toLowerCase(Locale.ROOT);
		return Arrays.stream(names.split(",")).map(s -> s.trim().toLowerCase(Locale.ROOT)).anyMatch(name::equals);
	}

	private @Nullable Player preyPlayer(ServerLevel level) {
		Player player = prey == null ? null : level.getPlayerByUUID(prey);
		return player != null && huntable(player) && player.level() == level ? player : null;
	}

	/** He sees them: in his line of sight and within his sight range, shortened by darkness and crouching. */
	private boolean sees(Player player) {
		double range = 40.0 * Stealth.rangeFactor(player);
		return distanceTo(player) <= range && hasLineOfSight(player);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		Stalking.swim(this);
		if (isInWater() && mode == Mode.PROVOKED) {
			// Water won't slow him: he wades on as if it weren't there.
			Vec3 v = getDeltaMovement();
			Vec3 look = Vec3.directionFromRotation(0.0F, getYRot());
			setDeltaMovement(v.x + look.x * 0.04, v.y, v.z + look.z * 0.04);
		}
		tickTimers();
		if (ghostTicks > 0) {
			Player nearest = level.getNearestPlayer(this, 80.0);
			if (--ghostTicks == 0 || nearest != null && nearest.distanceTo(this) < 12.0) {
				afterimage(level, position());
				discard(); // the Road Ghost: there, and then not
			}
			return;
		}
		if (morning(level)) {
			return;
		}
		recordHistory(level);
		Player target = preyPlayer(level);
		if (mode == Mode.PROVOKED && target == null) {
			calm();
		}
		if (mode != Mode.PROVOKED) {
			Player provoker = provoker(level);
			if (provoker != null) {
				provoke(level, provoker);
				target = provoker;
			}
		}
		pushThrough(level);
		field(level);
		if (graspHold > 0 && target != null) {
			holdGrasp(level, target);
			return;
		}
		switch (mode) {
			case WALK -> walk(level);
			case STAND -> stand(level);
			case PROVOKED -> {
				if (target != null) {
					pursue(level, target);
				}
			}
		}
		entityData.set(DATA_PREY, mode == Mode.PROVOKED && target != null ? target.getId() : -1);
	}

	private void tickTimers() {
		swingCooldown = Math.max(0, swingCooldown - 1);
		staticCooldown = Math.max(0, staticCooldown - 1);
		pounceCooldown = Math.max(0, pounceCooldown - 1);
		restoreCooldown = Math.max(0, restoreCooldown - 1);
		graspCooldown = Math.max(0, graspCooldown - 1);
		fieldCooldown = Math.max(0, fieldCooldown - 1);
		if (actionLeft > 0 && --actionLeft == 0) {
			entityData.set(DATA_ACTION, Action.NONE.ordinal());
		}
	}

	/** Who provokes him now: an always-provoking name in sight, someone in his way, someone staring at him. */
	private @Nullable Player provoker(ServerLevel level) {
		Vec3 forward = Vec3.directionFromRotation(0.0F, yBodyRot);
		int stareTicks = (int) Math.round(ConfigManager.get().dot.stareSeconds * 20.0);
		for (Player player : level.players()) {
			if (!huntable(player) || player.distanceToSqr(this) > 64.0 * 64.0) {
				stares.remove(player.getUUID());
				continue;
			}
			if (alwaysProvokes(player) && sees(player)) {
				return player;
			}
			if (!ConfigManager.get().dot.passive && sees(player)) {
				return player; // a predator: seen is hunted
			}
			Vec3 to = player.position().subtract(position()).multiply(1.0, 0.0, 1.0);
			if (to.length() < 2.2 && to.normalize().dot(forward) > 0.55) {
				return player; // in his way
			}
			if (Stalking.watching(player, this) && player.distanceTo(this) < 48.0) {
				int ticks = stares.merge(player.getUUID(), 1, Integer::sum);
				if (ticks >= stareTicks) {
					return player; // looked at him too long
				}
			} else {
				stares.remove(player.getUUID());
			}
		}
		return null;
	}

	private void provoke(ServerLevel level, Player player) {
		prey = player.getUUID();
		lostTicks = 0;
		unstuck.reset();
		setMode(Mode.PROVOKED);
		stares.clear();
		getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(ConfigManager.get().dot.aggroSpeed);
		voice(level, DLSounds.DOT_PROVOKED.value(), 2.0F, 1.0F, 100);
		// He screams, like the infected do but worse: rearing up, beak to the sky, and they freeze for a moment.
		screamLeft = 34;
		act(Action.SCREAM, 34);
		level.playSound(null, getX(), getY(), getZ(), DLSounds.INFECTED_SCREAMER_SCREAM.value(), SoundSource.HOSTILE, 2.6F, 0.62F);
		level.playSound(null, getX(), getY(), getZ(), DLSounds.INFECTED_BRUTE_ROAR.value(), SoundSource.HOSTILE, 2.0F, 0.7F);
		if (distanceTo(player) < 24.0) {
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 25, 3, true, false));
		}
	}

	private void calm() {
		prey = null;
		setMode(Mode.WALK);
		getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(ConfigManager.get().dot.speed);
	}

	private void walk(ServerLevel level) {
		// He walks, slowly, somewhere; now and then he stops, and something in him twitches.
		if (--twitchIn <= 0) {
			twitchIn = 120 + random.nextInt(400);
			act(Action.TWITCH, 12);
			getNavigation().stop();
			return;
		}
		if (getNavigation().isDone() && random.nextInt(80) == 0) {
			Vec3 to = position().add(random.nextGaussian() * 14.0, 0.0, random.nextGaussian() * 14.0);
			getNavigation().moveTo(to.x, to.y, to.z, 0.8);
		}
	}

	private void stand(ServerLevel level) {
		// In the road, in the way, waiting. He walks on when nobody is near.
		getNavigation().stop();
		Player nearest = level.getNearestPlayer(this, 40.0);
		if (nearest != null) {
			getLookControl().setLookAt(nearest, 10.0F, 10.0F);
		} else if (random.nextInt(200) == 0) {
			setMode(Mode.WALK);
		}
		if (--twitchIn <= 0) {
			twitchIn = 80 + random.nextInt(240);
			act(Action.TWITCH, 12);
		}
	}

	private void pursue(ServerLevel level, Player target) {
		double distance = distanceTo(target);
		boolean seen = sees(target);
		// Giving up: away and out of his sight for long enough (twice as long for the names that provoke him).
		int patience = ConfigManager.get().dot.giveUpSeconds * 20 * (alwaysProvokes(target) ? 2 : 1);
		lostTicks = seen && distance < 32.0 ? 0 : lostTicks + 1;
		if (lostTicks > patience) {
			calm();
			return;
		}
		getLookControl().setLookAt(target, 30.0F, 30.0F);
		if (screamLeft > 0) {
			screamLeft--;
			getNavigation().stop();
			return; // the scream holds him a moment
		}
		if (scares(level, target, distance)) {
			return;
		}
		if (pounce(level, target, distance, seen)) {
			return;
		}
		// Yells as he runs: short, ragged, like the infected calling to each other.
		if (--yellIn <= 0) {
			yellIn = 120 + random.nextInt(140);
			act(Action.YELL, 14);
			SoundEvent yell = switch (random.nextInt(3)) {
				case 0 -> DLSounds.INFECTED_RUNNER_ALERT.value();
				case 1 -> DLSounds.INFECTED_BRUTE_ROAR.value();
				default -> DLSounds.INFECTED_SCREAMER_SCREAM.value();
			};
			level.playSound(null, getX(), getY(), getZ(), yell, SoundSource.HOSTILE, 1.8F, 0.65F + random.nextFloat() * 0.15F);
		}
		if (graspWindup > 0) {
			getNavigation().stop();
			if (--graspWindup == 0) {
				if (distance < 3.5 && hasLineOfSight(target)) {
					act(Action.GRASP, 30);
					graspHold = 30;
					level.playSound(null, getX(), getY(), getZ(), DLSounds.DOT_GRASP.value(), SoundSource.HOSTILE, 1.6F, 1.0F);
					strike(level, target);
				}
				graspCooldown = 360;
			}
			return;
		}
		if (windup > 0) {
			getNavigation().stop();
			if (--windup == 0) {
				swing(net.minecraft.world.InteractionHand.MAIN_HAND);
				if (distanceTo(target) < 3.4 && hasLineOfSight(target)) {
					strike(level, target);
				}
				swingCooldown = 24;
			}
			return;
		}
		if (graspCooldown == 0 && distance < 3.0 && seen) {
			graspWindup = 20;
			act(Action.GRASP_WINDUP, 20);
			level.playSound(null, getX(), getY(), getZ(), DLSounds.DOT_GRASP_WINDUP.value(), SoundSource.HOSTILE, 1.4F, 1.0F);
			return;
		}
		if (swingCooldown == 0 && distance < 3.2) {
			windup = 8;
			// He uses the blade three ways, in turn: an overhead slash, a backhand cut, a thrust.
			act(combo == 0 ? Action.SWING : combo == 1 ? Action.SWING2 : Action.THRUST, 18);
			combo = (combo + 1) % 3;
			level.playSound(null, getX(), getY(), getZ(), DLSounds.DOT_SWING.value(), SoundSource.HOSTILE, 1.2F, 1.0F);
			return;
		}
		if (fieldCooldown == 0 && distance < 10.0) {
			fieldLeft = 120;
			fieldCooldown = 500;
			act(Action.FIELD, 24);
			level.playSound(null, getX(), getY(), getZ(), DLSounds.DOT_FIELD_START.value(), SoundSource.HOSTILE, 1.8F, 1.0F);
		}
		// Static Step: out of their sight, a glitch closer.
		if (staticCooldown == 0 && distance > 8.0 && distance < 32.0 && !target.hasLineOfSight(this)) {
			if (staticStep(level, target)) {
				return;
			}
		}
		// Stuck (no way to them, or getting nowhere) or far behind: he is somewhere else, near them, out of sight.
		if (unstuck.tick(this, target, 70) || distance > 48.0 && seen) {
			if (Stalking.teleportNear(this, level, target, 8.0, 14.0, 2.7)) {
				level.playSound(null, getX(), getY(), getZ(), DLSounds.DOT_STATIC_STEP.value(), SoundSource.HOSTILE, 1.4F, 0.8F);
				return;
			}
		}
		if ((tickCount + getId()) % 5 == 0 || getNavigation().isDone()) {
			getNavigation().moveTo(target, distance > 6.0 ? 1.2 : 1.0); // a run when he's far, closing in when near
		}
	}

	/**
	 * The pounce: from 4 to 8 blocks, in sight, he drops low (half a second), then springs at them, claws first. A
	 * sidestep makes him miss and stumble. 8 seconds between pounces.
	 */
	private boolean pounce(ServerLevel level, Player target, double distance, boolean seen) {
		if (pounceTicks > 0) {
			pounceTicks--;
			if (!pounceHit && distanceTo(target) < 2.2) {
				pounceHit = true;
				strike(level, target);
			}
			if (pounceTicks == 0 && !pounceHit) {
				swingCooldown = Math.max(swingCooldown, 20); // a stumble after a miss
			}
			return true;
		}
		if (pounceWindup > 0) {
			getNavigation().stop();
			getLookControl().setLookAt(target, 40.0F, 40.0F);
			if (--pounceWindup == 0) {
				act(Action.POUNCE, 14);
				Vec3 dir = target.position().subtract(position()).multiply(1.0, 0.0, 1.0).normalize();
				setDeltaMovement(dir.x * 1.25, 0.42, dir.z * 1.25);
				hurtMarked = true;
				pounceTicks = 12;
				pounceHit = false;
				pounceCooldown = 160;
				level.playSound(null, getX(), getY(), getZ(), DLSounds.INFECTED_RUNNER_ALERT.value(), SoundSource.HOSTILE, 1.8F, 0.6F);
			}
			return true;
		}
		if (pounceCooldown == 0 && seen && onGround() && distance > 4.0 && distance < 8.0 && random.nextInt(6) == 0) {
			pounceWindup = 10;
			act(Action.POUNCE_CROUCH, 10);
			getNavigation().stop();
			return true;
		}
		return false;
	}

	/**
	 * His jumpscares while he pursues ({@code dot.jumpscares}; each has its own cooldown per survivor):
	 * Wrong Frame when they turn to face him within 20 blocks (10 min); Behind the Light, in the dark with their
	 * flashlight on and him out of their sight, their light dies and he is right in front of them when it comes
	 * back, then gone (8 min). Returns true while one holds him.
	 */
	private boolean scares(ServerLevel level, Player target, double distance) {
		if (blinkAwayIn > 0) {
			getNavigation().stop();
			if (--blinkAwayIn == 0) {
				afterimage(level, position());
				Stalking.teleportNear(this, level, target, 12.0, 18.0, 2.7);
			}
			return true;
		}
		if (!ConfigManager.get().dot.jumpscares || !(target instanceof ServerPlayer player)) {
			return false;
		}
		boolean watching = Stalking.watching(target, this);
		if (watching && !preyWasWatching && distance < 20.0) {
			com.deadlands.horror.HorrorDirector.fire(player, "deadlands:dot_wrong_frame", 12000L);
		}
		preyWasWatching = watching;
		boolean flashlight = com.deadlands.item.FlashlightItem.shining(target.getMainHandItem()) || com.deadlands.item.FlashlightItem.shining(target.getOffhandItem());
		if (flashlight && Stealth.light(target) <= 7 && distance > 8.0 && distance < 24.0 && !target.hasLineOfSight(this) && random.nextInt(200) == 0
			&& com.deadlands.horror.HorrorDirector.fire(player, "deadlands:dot_behind_light", 9600L)) {
			Vec3 front = target.position().add(target.getViewVector(1.0F).multiply(1.0, 0.0, 1.0).normalize().scale(3.0));
			BlockPos floor = Stalking.standable(level, BlockPos.containing(front), 3);
			if (floor != null) {
				afterimage(level, position());
				teleportTo(floor.getX() + 0.5, floor.getY(), floor.getZ() + 0.5);
				lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
				blinkAwayIn = 40; // there when the light comes back (after a second), then gone
				act(Action.LOOM, 40);
				return true;
			}
		}
		return false;
	}

	private void strike(ServerLevel level, Player target) {
		if (target.hurtServer(level, damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE))) {
			if (!target.isAlive()) {
				level.playSound(null, getX(), getY(), getZ(), DLSounds.DOT_KILL.value(), SoundSource.HOSTILE, 1.8F, 1.0F);
				if (target instanceof ServerPlayer player && ConfigManager.get().dot.jumpscares) {
					com.deadlands.horror.HorrorDirector.fire(player, "deadlands:dot_404", 0L); // 404 Death
				}
				calm();
			}
		}
	}

	/** Error Grasp's hold: they can't move. */
	private void holdGrasp(ServerLevel level, Player target) {
		graspHold--;
		target.setDeltaMovement(0.0, Math.min(0.0, target.getDeltaMovement().y), 0.0);
		target.hurtMarked = true;
		target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 4, 9, true, false));
		getNavigation().stop();
		getLookControl().setLookAt(target, 40.0F, 40.0F);
	}

	private boolean staticStep(ServerLevel level, Player target) {
		Vec3 from = position();
		Vec3 dir = target.position().subtract(from).multiply(1.0, 0.0, 1.0).normalize();
		double jump = 6.0 + random.nextDouble() * 2.0;
		for (double back = 0.0; back < 3.0; back += 1.0) {
			Vec3 at = from.add(dir.scale(jump - back));
			BlockPos floor = Stalking.standable(level, BlockPos.containing(at), 3);
			if (floor == null || Stalking.visibleFrom(target, Vec3.atBottomCenterOf(floor), 2.6)) {
				continue;
			}
			afterimage(level, from);
			teleportTo(floor.getX() + 0.5, floor.getY(), floor.getZ() + 0.5);
			getNavigation().stop();
			act(Action.STATIC_STEP, 10);
			staticCooldown = 240;
			level.playSound(null, getX(), getY(), getZ(), DLSounds.DOT_STATIC_STEP.value(), SoundSource.HOSTILE, 1.6F, 1.0F);
			return true;
		}
		staticCooldown = 40;
		return false;
	}

	private void afterimage(ServerLevel level, Vec3 at) {
		DustParticleOptions green = new DustParticleOptions(0x3DFF6E, 1.6F);
		for (int i = 0; i < 40; i++) {
			level.sendParticles(green, at.x + (random.nextDouble() - 0.5) * 0.9, at.y + random.nextDouble() * 2.7,
				at.z + (random.nextDouble() - 0.5) * 0.9, 1, 0.0, 0.0, 0.0, 0.0);
		}
		level.sendParticles(ParticleTypes.SMOKE, at.x, at.y + 1.3, at.z, 12, 0.3, 0.8, 0.3, 0.01);
	}

	/** Corruption Field: for its time, everyone within 6 blocks sickens and their flashlight stutters. */
	private void field(ServerLevel level) {
		if (fieldLeft <= 0) {
			return;
		}
		fieldLeft--;
		if (fieldLeft % 10 == 0) {
			DustParticleOptions green = new DustParticleOptions(0x3DFF6E, 1.2F);
			for (int i = 0; i < 24; i++) {
				double a = random.nextDouble() * Math.PI * 2.0;
				level.sendParticles(green, getX() + Math.cos(a) * 6.0, getY() + 0.2 + random.nextDouble() * 0.6, getZ() + Math.sin(a) * 6.0, 1, 0.0, 0.0, 0.0, 0.0);
			}
			for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(6.0), DotEntity::huntable)) {
				player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 80, 0, true, false));
				com.deadlands.horror.HorrorDirector.fire(player, "deadlands:dot_corruption", 100L);
			}
		}
		if (fieldLeft % 40 == 20) {
			level.playSound(null, getX(), getY(), getZ(), DLSounds.DOT_FIELD_LOOP.value(), SoundSource.HOSTILE, 1.2F, 1.0F);
		}
		if (fieldLeft == 0) {
			level.playSound(null, getX(), getY(), getZ(), DLSounds.DOT_FIELD_END.value(), SoundSource.HOSTILE, 1.4F, 1.0F);
		}
	}

	/** He walks through doors, fences, panes and leaves, breaking them. */
	private void pushThrough(ServerLevel level) {
		if (!ConfigManager.get().dot.pushesThrough || !level.getGameRules().get(GameRules.MOB_GRIEFING) || getDeltaMovement().horizontalDistanceSqr() < 1.0E-4
			&& !horizontalCollision) {
			return;
		}
		Vec3 ahead = position().add(Vec3.directionFromRotation(0.0F, yBodyRot).scale(0.9));
		for (int dy = 0; dy < 3; dy++) {
			BlockPos pos = BlockPos.containing(ahead.x, getY() + dy, ahead.z);
			BlockState state = level.getBlockState(pos);
			if (state.is(BlockTags.DOORS) || state.is(BlockTags.FENCES) || state.is(BlockTags.FENCE_GATES) || state.is(BlockTags.LEAVES)
				|| state.is(BlockTags.TRAPDOORS) || state.is(net.minecraft.world.level.block.Blocks.GLASS_PANE)
				|| state.getBlock() instanceof net.minecraft.world.level.block.IronBarsBlock) {
				level.destroyBlock(pos, true, this);
			}
		}
	}

	private void recordHistory(ServerLevel level) {
		long now = level.getGameTime();
		if (now % 5 == 0) {
			history.addLast(new Snapshot(position(), getHealth()));
			while (history.size() > 13) {
				history.removeFirst();
			}
		}
		while (!recentDamage.isEmpty() && now - (long) recentDamage.peekFirst()[0] > 100) {
			recentDamage.removeFirst();
		}
	}

	// ======================================================================================== unstoppable

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		DeadlandsConfig.Dot config = ConfigManager.get().dot;
		if (config.invulnerable && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			if (source.getEntity() instanceof Player player && huntable(player) && mode != Mode.PROVOKED) {
				provoke(level, player);
			}
			return false;
		}
		float taken = source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) ? amount : amount * (float) (1.0 - config.damageResistance);
		boolean hurt = super.hurtServer(level, source, taken);
		if (hurt) {
			recentDamage.addLast(new float[] {level.getGameTime(), taken});
			if (source.getEntity() instanceof Player player && huntable(player) && mode != Mode.PROVOKED) {
				provoke(level, player);
			}
			restorePoint(level);
		}
		return hurt;
	}

	/** Restore Point: badly hurt in a moment, he is back where he was, as he was. */
	private void restorePoint(ServerLevel level) {
		float lost = 0.0F;
		for (float[] hit : recentDamage) {
			lost += hit[1];
		}
		if (restoreCooldown > 0 || lost < getMaxHealth() * 0.4F || history.isEmpty() || !isAlive()) {
			return;
		}
		Snapshot back = history.peekFirst();
		afterimage(level, position());
		teleportTo(back.pos().x, back.pos().y, back.pos().z);
		setHealth(Math.max(getHealth(), back.health()));
		recentDamage.clear();
		restoreCooldown = 1200;
		act(Action.RESTORE, 16);
		level.playSound(null, getX(), getY(), getZ(), DLSounds.DOT_RESTORE.value(), SoundSource.HOSTILE, 1.8F, 1.0F);
	}

	@Override
	public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
		return source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_FREEZING)
			|| source.is(DamageTypeTags.IS_DROWNING) || source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL)
			|| super.isInvulnerableTo(level, source);
	}

	/** Nothing harmful takes: no slowness, weakness, poison, nothing that would hold him. */
	@Override
	public boolean canBeAffected(MobEffectInstance effect) {
		return effect.getEffect().value().isBeneficial() && super.canBeAffected(effect);
	}

	@Override
	public boolean canFreeze() {
		return false;
	}

	@Override
	public boolean startRiding(Entity vehicle, boolean force, boolean sendEvent) {
		return false; // no boat or minecart holds him
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean isPushedByFluid() {
		return false;
	}

	@Override
	public void knockback(double strength, double x, double z) {
		// He doesn't move for anything.
	}

	// ======================================================================================== climbing

	@Override
	public boolean onClimbable() {
		return entityData.get(DATA_CLIMBING) || super.onClimbable();
	}

	// ======================================================================================== dawn

	private boolean morning(ServerLevel level) {
		long time = Math.floorMod(level.getDayTime(), 24000L);
		boolean day = time < 12500L;
		dayTicks = day ? dayTicks + 1 : 0;
		if (!day) {
			return false;
		}
		boolean watched = false;
		for (Player player : level.players()) {
			if (huntable(player) && Stalking.watching(player, this) && player.distanceTo(this) < 48.0) {
				watched = true;
			}
		}
		if (watched && dayTicks < 1200) {
			return false;
		}
		afterimage(level, position());
		level.playSound(null, getX(), getY(), getZ(), DLSounds.DOT_FIELD_END.value(), SoundSource.HOSTILE, 1.4F, 0.8F);
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
			voice(level, DLSounds.DOT_IDLE.value(), 1.0F, 1.0F, 200);
		}
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return DLSounds.DOT_IDLE.value();
	}

	@Override
	public int getAmbientSoundInterval() {
		return 400;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return DLSounds.DOT_HURT.value();
	}

	@Override
	protected SoundEvent getDeathSound() {
		return DLSounds.DOT_DEATH.value();
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		// Paced in tick().
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			aggroO = aggro;
			climbO = climb;
			aggro = Mth.approach(aggro, mode() == Mode.PROVOKED ? 1.0F : 0.0F, 0.04F);
			climb = Mth.approach(climb, climbing() ? 1.0F : 0.0F, 0.12F);
			// A stride takes 16.5 units of walk animation prowling, 10.1 sprinting (longer, faster strides).
			walkPhaseO = walkPhase;
			walkPhase += walkAnimation.speed() / Mth.lerp(aggro, 16.5F, 10.1F);
			if (walkPhase > 64.0F) {
				walkPhase -= 64.0F;
				walkPhaseO -= 64.0F;
			}
			return;
		}
		boolean climb = horizontalCollision && mode == Mode.PROVOKED;
		if (climb != entityData.get(DATA_CLIMBING)) {
			entityData.set(DATA_CLIMBING, climb);
		}
		if (climb) {
			Stalking.climbSlowly(this, ConfigManager.get().dot.climbFactor);
		}
		double speed = getDeltaMovement().horizontalDistance();
		if (!onGround() || speed < 0.03) {
			stepTicks = 0;
			return;
		}
		if (++stepTicks % (speed > 0.18 ? 9 : 13) == 0) {
			level().playSound(null, getX(), getY(), getZ(), DLSounds.DOT_STEP.value(), SoundSource.HOSTILE, 1.0F, 0.9F + random.nextFloat() * 0.15F);
		}
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public void checkDespawn() {
		// Only the morning takes him.
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}
}
