package com.deadlands.entity;

import com.deadlands.Deadlands;
import com.deadlands.config.ConfigManager;
import com.deadlands.config.DeadlandsConfig;
import com.deadlands.item.FlashlightItem;
import com.deadlands.registry.DLSounds;
import com.deadlands.registry.DLWeapons;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Aberrant (internal id {@code asesino}): DEADLANDS' slasher. One weapon, one move: a heavy slash.
 * Everything else is how he hunts.
 *
 * <p><b>His mind</b> is a state machine that decides for itself:
 * <ul>
 *   <li><b>Roam</b>: wanders until he picks a prey, the most isolated, then the most hurt, survivor he
 *       notices (sees, hears, or senses within 32 blocks at night). He leaves groups of three or more.</li>
 *   <li><b>Stalk</b> (about {@code asesino.stalkMinutes}, 10 by default): he follows from 16 to 28 blocks,
 *       choosing spots the prey cannot see, behind them, and moving on as they move. Caught watching, he
 *       freezes and stares, then slips away. Every minute or two he lets himself be seen: steps out at a
 *       distance, stares, and is gone. He keeps track of them by sound and scent even out of sight.</li>
 *   <li><b>Deciding to attack</b>: when the stalk has run its course and the prey is alone, he commits. He
 *       strikes sooner if they give him a chance: hurt, alone in the dark, looking the other way, busy
 *       standing still, or walking right up to him. He never commits on a group, or by day unless they are
 *       in the dark.</li>
 *   <li><b>Chase</b>: relentless, faster the longer it lasts, through the dark (darkness does not hide
 *       you once he is on you). He leads you, opens doors, and slashes with a half-second wind-up. Only
 *       breaking his line of sight for eight seconds loses him.</li>
 *   <li><b>Search</b>: thirty seconds checking where he lost you and the hiding places around it, rooms
 *       first, listening: running or walking uncrouched within 16 blocks gives you away; within 20 blocks
 *       in view (4 through anything) he finds you.</li>
 *   <li><b>Lurk</b>: if the search finds nothing he hides nearby, silent and still, no music: crouched out
 *       of sight near where you vanished, for one to two minutes. Come out close and unaware and he
 *       ambushes; spot him first and he slips back into stalking.</li>
 * </ul>
 * He also thinks about himself: badly hurt, he breaks off and lurks somewhere hidden to recover; facing
 * a group mid-chase, he backs off; with the prey out of reach (up a pillar, across water), he waits
 * nearby instead of pacing beneath them. He leaves with the morning (unseen, in smoke) and never despawns otherwise.
 *
 * <p>Animations are synced as an {@link Action}; his voice never talks over itself.
 */
public class AsesinoEntity extends Monster {
	public enum Mode {
		ROAM, STALK, CHASE, SEARCH, LURK // append only
	}

	/** What his body is doing, for the animations (append only; the old ability poses stay so indices hold). */
	public enum Action {
		NONE, WINDUP, SWING, CROUCH, LEAP, STUN, SHRIEK, BREACH, FRENZY, SCENT, STARE, WINDUP2, SWING2
	}

	private static final EntityDataAccessor<Integer> DATA_PREY = SynchedEntityData.defineId(AsesinoEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_MODE = SynchedEntityData.defineId(AsesinoEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_ACTION = SynchedEntityData.defineId(AsesinoEntity.class, EntityDataSerializers.INT);
	private static final Identifier CHASE_SPEED = Deadlands.id("asesino_chase_speed");

	private static final int MELEE_WINDUP = 10;
	private static final int SWING_TICKS = 7;
	private static final int LOSE_TICKS = 160;
	private static final int SEARCH_TICKS = 600;
	private static final int RAMP_TICKS = 600;
	private static final int CHASE_CUE_GAP = 600;

	private Mode mode = Mode.ROAM;
	private @Nullable UUID prey;
	private int modeTicks;
	private int unseenTicks;
	private @Nullable Vec3 lastKnown;
	// Stalking.
	private int stalkTicks;
	private int stalkPlan = 12000;
	private int lookedAwayTicks;
	private int watchedTicks;
	private int stillTicks;
	private @Nullable Vec3 preyLastPos;
	private double lastDistance;
	private int peekCooldown = 1200;
	private int peekLeft;
	private @Nullable Vec3 peekSpot;
	private @Nullable Vec3 stalkSpot;
	private int replanIn;
	private boolean slipping;
	// Chase.
	private int unreachableTicks;
	private boolean recovering;
	// Search and lurk.
	private final ArrayList<Vec3> searchSpots = new ArrayList<>();
	private int searchLeft;
	private int lookingAround;
	private @Nullable Vec3 lurkSpot;
	private int lurkLeft;
	// Melee.
	private int meleeCooldown;
	private int meleeWindup;
	private boolean backhand;
	/** The lunge: crouch (wind-up), then a low dash at the survivor. */
	private int lungeWindup;
	private int lungeTicks;
	private int lungeCooldown = 100;
	private boolean lungeHit;
	/** Never stuck for long in a chase: he comes back near them, out of sight (Stalking.Unstuck). */
	private final com.deadlands.entity.ai.Stalking.Unstuck unstuck = new com.deadlands.entity.ai.Stalking.Unstuck();
	private int catchUpCooldown = 200;
	/** Ticks until the next sound behind the survivor he is stalking (a step, a breath). */
	private int creepIn = 600;
	private int actionLeft;
	// Sound.
	private long voiceUntil;
	private long chaseCueAt = -CHASE_CUE_GAP;
	private long hurtSoundAt;
	private int stepTicks;
	private int dayTicks;
	/** Client: the tick the current action started, and the eased stance blends (with last tick's). */
	private int actionStartTick;
	private float hunch;
	private float hunchO;
	private float sprint;
	private float sprintO;

	public AsesinoEntity(EntityType<? extends AsesinoEntity> type, Level level) {
		super(type, level);
		this.xpReward = 60;
		if (getNavigation() instanceof GroundPathNavigation ground) {
			ground.setCanOpenDoors(true); // he opens doors like anyone: no smashing (the slash is his only move)
			ground.setCanFloat(true);
		}
		// Swims: floats at the surface and paths through water rather than around it or sinking.
		setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.WATER, 2.0F);
		applyConfig();
		if (!level.isClientSide()) {
			setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(DLWeapons.MACHETE));
			setDropChance(EquipmentSlot.MAINHAND, 0.0F);
			setPersistenceRequired(); // he never despawns
		}
	}

	public static AttributeSupplier.Builder createAttributes() {
		DeadlandsConfig.Asesino config = ConfigManager.get().asesino;
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, config.health)
			.add(Attributes.ATTACK_DAMAGE, config.damage)
			.add(Attributes.MOVEMENT_SPEED, config.stalkSpeed)
			.add(Attributes.FOLLOW_RANGE, config.followRange)
			.add(Attributes.KNOCKBACK_RESISTANCE, config.knockbackResistance)
			.add(Attributes.ARMOR, config.armor)
			.add(Attributes.STEP_HEIGHT, 1.0);
	}

	private void applyConfig() {
		DeadlandsConfig.Asesino config = ConfigManager.get().asesino;
		getAttribute(Attributes.MAX_HEALTH).setBaseValue(config.health);
		getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(config.damage);
		getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(config.stalkSpeed);
		getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(config.followRange);
		getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(config.knockbackResistance);
		getAttribute(Attributes.ARMOR).setBaseValue(config.armor);
		setHealth(getMaxHealth());
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_PREY, -1);
		builder.define(DATA_MODE, 0);
		builder.define(DATA_ACTION, 0);
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
		goalSelector.addGoal(1, new OpenDoorGoal(this, true));
		goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.85) {
			@Override
			public boolean canUse() {
				return mode == Mode.ROAM && super.canUse();
			}

			@Override
			public boolean canContinueToUse() {
				return mode == Mode.ROAM && super.canContinueToUse();
			}
		});
	}

	// ======================================================================================== what the client reads

	/** The survivor he is chasing (the chase music's layer 4), or -1. */
	public int chasingId() {
		return mode() == Mode.CHASE ? entityData.get(DATA_PREY) : -1;
	}

	/** The survivor he is stalking or searching for (layer 1 plays for them), or -1. Lurking is silent: -1. */
	public int stalkingId() {
		return mode() == Mode.STALK || mode() == Mode.SEARCH ? entityData.get(DATA_PREY) : -1;
	}

	public Mode mode() {
		return Mode.values()[Mth.clamp(entityData.get(DATA_MODE), 0, Mode.values().length - 1)];
	}

	public Action action() {
		return Action.values()[Mth.clamp(entityData.get(DATA_ACTION), 0, Action.values().length - 1)];
	}

	public float actionAge(float partialTick) {
		return tickCount - actionStartTick + partialTick;
	}

	public float hunch(float partialTick) {
		return Mth.lerp(partialTick, hunchO, hunch);
	}

	public float sprint(float partialTick) {
		return Mth.lerp(partialTick, sprintO, sprint);
	}

	private void act(Action action, int ticks) {
		entityData.set(DATA_ACTION, action.ordinal());
		actionLeft = ticks;
	}

	// ======================================================================================== the mind

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		com.deadlands.entity.ai.Stalking.swim(this);
		modeTicks++;
		meleeCooldown = Math.max(0, meleeCooldown - 1);
		lungeCooldown = Math.max(0, lungeCooldown - 1);
		catchUpCooldown = Math.max(0, catchUpCooldown - 1);
		peekCooldown = Math.max(0, peekCooldown - 1);
		if (actionLeft > 0 && --actionLeft == 0) {
			entityData.set(DATA_ACTION, Action.NONE.ordinal());
		}
		if (morning(level)) {
			return;
		}
		Player target = preyPlayer(level);
		if (mode != Mode.ROAM && target == null) {
			setMode(Mode.ROAM);
		}
		switch (mode) {
			case ROAM -> roam(level);
			case STALK -> stalk(level, target);
			case CHASE -> chase(level, target);
			case SEARCH -> search(level, target);
			case LURK -> lurk(level, target);
		}
		if (target != null && mode != Mode.ROAM) {
			entityData.set(DATA_PREY, target.getId());
		}
	}

	/**
	 * Morning: he does not stay for the day. Once it is light he breaks off (a chase gets thirty more
	 * seconds) and, the moment nobody is looking at him, he is gone in a breath of smoke; after a minute
	 * he goes even if someone is. Returns true once he has left.
	 */
	private boolean morning(ServerLevel level) {
		long time = Math.floorMod(level.getDayTime(), 24000L);
		boolean day = time >= 0L && time < 12500L && ConfigManager.get().asesino.nightOnly;
		dayTicks = day ? dayTicks + 1 : 0;
		if (!day || mode == Mode.CHASE && dayTicks < 600) {
			return false;
		}
		boolean seen = false;
		for (Player player : level.players()) {
			if (huntable(player) && player.distanceToSqr(this) < 48.0 * 48.0 && player.hasLineOfSight(this) && watching(player)) {
				seen = true;
				break;
			}
		}
		if (seen && dayTicks < 1200) {
			if (mode != Mode.ROAM && mode != Mode.LURK) {
				setMode(Mode.ROAM);
			}
			return false;
		}
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.2, getZ(), 30, 0.4, 0.9, 0.4, 0.02);
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE, getX(), getY() + 1.0, getZ(), 40, 0.5, 1.0, 0.5, 0.01);
		discard();
		return true;
	}

	private void setMode(Mode next) {
		if (mode == next) {
			return;
		}
		mode = next;
		modeTicks = 0;
		unseenTicks = 0;
		watchedTicks = 0;
		lookedAwayTicks = 0;
		unreachableTicks = 0;
		stalkSpot = null;
		peekSpot = null;
		peekLeft = 0;
		slipping = false;
		searchSpots.clear();
		lookingAround = 0;
		meleeWindup = 0;
		entityData.set(DATA_MODE, next.ordinal());
		if (next != Mode.CHASE) {
			getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(CHASE_SPEED);
			setTarget(null);
		}
		if (next == Mode.ROAM) {
			prey = null;
			lastKnown = null;
			stalkTicks = 0;
			recovering = false;
			entityData.set(DATA_PREY, -1);
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

	private boolean night() {
		return !level().isBrightOutside();
	}

	// ---------------------------------------------------------------------------------------- perception

	/** Whether a survivor is lit: a flashlight shining in either hand, light where they stand, or daylight. */
	public static boolean lit(Player player) {
		if (FlashlightItem.shining(player.getMainHandItem()) || FlashlightItem.shining(player.getOffhandItem())) {
			return true;
		}
		Level level = player.level();
		BlockPos eyes = BlockPos.containing(player.getEyePosition());
		return level.getBrightness(LightLayer.BLOCK, eyes) >= 9 || level.isBrightOutside() && level.getBrightness(LightLayer.SKY, eyes) >= 10;
	}

	/** He sees them: in his line of sight and near enough (beyond 20 blocks only if lit). In a chase any line of sight counts. */
	private boolean sees(Player player) {
		double distance = distanceTo(player);
		// Darkness and crouching shorten how far he sees (Stealth): a careful survivor can slip away in the dark.
		if (distance > getAttributeValue(Attributes.FOLLOW_RANGE) * com.deadlands.entity.ai.Stealth.rangeFactor(player) || !hasLineOfSight(player)) {
			return distance <= 3.0; // right on top of them, through anything
		}
		return mode == Mode.CHASE || distance <= 20.0 || lit(player);
	}

	/** He hears them: running, or walking uncrouched, within 16 blocks; anything within 6. */
	private boolean hears(Player player) {
		double distance = distanceTo(player);
		boolean moving = player.getDeltaMovement().horizontalDistanceSqr() > 0.004;
		return distance <= 6.0 || distance <= 16.0 && (player.isSprinting() || moving && !player.isShiftKeyDown());
	}

	/** The survivor is looking roughly at him and can see him. */
	private boolean watching(Player player) {
		Vec3 to = getEyePosition().subtract(player.getEyePosition()).normalize();
		return player.getViewVector(1.0F).dot(to) > 0.75 && player.hasLineOfSight(this);
	}

	/** Other survivors within 10 blocks of this one. */
	private int company(Player player, List<? extends Player> players) {
		int count = 0;
		for (Player other : players) {
			if (other != player && huntable(other) && other.distanceToSqr(player) < 100.0) {
				count++;
			}
		}
		return count;
	}

	/** Whether the survivor could see a person standing at {@code feet}. */
	private static boolean visibleFrom(Player player, Vec3 feet) {
		Vec3 head = feet.add(0.0, 1.8, 0.0);
		return player.level().clip(new ClipContext(player.getEyePosition(), head, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player))
			.getType() == HitResult.Type.MISS;
	}

	// ---------------------------------------------------------------------------------------- roam

	private void roam(ServerLevel level) {
		if ((tickCount + getId()) % 20 != 0) {
			return;
		}
		Player best = null;
		double bestScore = Double.MAX_VALUE;
		List<? extends Player> players = level.players();
		for (Player player : players) {
			if (!huntable(player)) {
				continue;
			}
			double distance = distanceTo(player);
			boolean noticed = sees(player) || hears(player) || night() && distance < 32.0 * com.deadlands.entity.ai.Stealth.rangeFactor(player);
			int company = company(player, players);
			if (!noticed || company >= 2) {
				continue;
			}
			double score = company * 40.0 + player.getHealth() * 2.0 + distance * 0.5;
			if (score < bestScore) {
				bestScore = score;
				best = player;
			}
		}
		if (best != null) {
			prey = best.getUUID();
			lastKnown = best.position();
			lastDistance = distanceTo(best);
			double minutes = ConfigManager.get().asesino.stalkMinutes;
			stalkPlan = (int) (minutes * 1200.0 * (0.8 + random.nextDouble() * 0.4));
			stalkTicks = 0;
			peekCooldown = 900 + random.nextInt(900);
			setMode(Mode.STALK);
		}
	}

	// ---------------------------------------------------------------------------------------- stalk

	private void stalk(ServerLevel level, Player target) {
		double distance = distanceTo(target);
		if (distance > 160.0) {
			setMode(Mode.ROAM);
			return;
		}
		stalkTicks++;
		lastKnown = target.position(); // he keeps track of them by sound and scent
		boolean watched = watching(target);
		lookedAwayTicks = watched ? 0 : lookedAwayTicks + 1;
		Vec3 now = target.position();
		stillTicks = preyLastPos != null && now.distanceToSqr(preyLastPos) < 0.02 ? stillTicks + 1 : 0;
		preyLastPos = now;
		boolean approaching = watched && distance < lastDistance - 0.04 && distance < 14.0;
		lastDistance = distance;
		boolean alone = company(target, level.players()) == 0;

		if (recovering && getHealth() >= getMaxHealth() * 0.7F) {
			recovering = false;
		}
		if (!recovering && decideToAttack(target, distance, alone, approaching)) {
			startChase(level, target);
			return;
		}
		getLookControl().setLookAt(target, 20.0F, 20.0F);

		// Peeking: now and then he lets himself be seen, stares, and goes.
		if (peekSpot != null || peekCooldown == 0 && !watched && distance < 40.0 && night()) {
			if (peek(level, target, watched)) {
				return;
			}
		}
		if (watched && distance < 40.0 && !slipping) {
			// Caught: freeze and stare, then slip away out of sight.
			if (++watchedTicks < 50) {
				getNavigation().stop();
				if (action() == Action.NONE) {
					act(Action.STARE, 50);
				}
				return;
			}
			slipping = true;
			stalkSpot = hiddenSpot(level, target, 18.0, 30.0, true);
			replanIn = 60;
		}
		if (!watched) {
			watchedTicks = 0;
		}
		// A step or a breath just behind them, out of view, now and then.
		if (!watched && distance < 28.0 && --creepIn <= 0) {
			creepIn = 700 + random.nextInt(1100);
			com.deadlands.entity.ai.Stalking.soundBehind(level, target,
				random.nextBoolean() ? DLSounds.ASESINO_STEP.value() : DLSounds.ASESINO_AMBIENT.value(), 0.45F, 0.9F, 5.0 + random.nextDouble() * 4.0);
		}
		if (approaching && !slipping) {
			slipping = true; // they are coming: not yet, so he melts away
			stalkSpot = hiddenSpot(level, target, 20.0, 30.0, true);
			replanIn = 60;
		}
		// He closes in as the stalk goes on: 22-34 blocks at first, 10-18 near the end.
		float progress = Math.min(1.0F, stalkTicks / (float) stalkPlan);
		double near = 22.0 - 12.0 * progress;
		double far = 34.0 - 16.0 * progress;
		if (--replanIn <= 0 || stalkSpot == null) {
			replanIn = 40 + random.nextInt(20);
			slipping = false;
			Vec3 spot = hiddenSpot(level, target, near, far, false);
			if (spot != null) {
				stalkSpot = spot;
			}
		}
		if (stalkSpot == null) {
			return;
		}
		// When they stand still, so does he (if they can't see him): moving only when they move, like a hunter.
		if (stillTicks > 15 && !slipping && distance < far + 6.0 && !visibleFrom(target, position())) {
			getNavigation().stop();
			return;
		}
		if (position().distanceToSqr(stalkSpot) < 2.0) {
			getNavigation().stop();
			return;
		}
		double pace = slipping ? 1.15 : distance > 34.0 ? 1.1 : 0.75;
		if (getNavigation().isDone() || (tickCount + getId()) % 10 == 0) {
			getNavigation().moveTo(stalkSpot.x, stalkSpot.y, stalkSpot.z, pace);
		}
	}

	/**
	 * When to attack. Never on a group; by day only if they are in the dark. Otherwise when the stalk has
	 * run its course, or sooner on an opening: hurt, alone in the dark, looking away, standing still, or
	 * walking right up to him. The better the opening, the sooner (but at least a third of the stalk).
	 */
	private boolean decideToAttack(Player target, double distance, boolean alone, boolean approaching) {
		if (getLastHurtByMob() == target && tickCount - getLastHurtByMobTimestamp() < 40) {
			return true;
		}
		if (!alone || !night() && lit(target)) {
			return false;
		}
		if (distance < 5.0 && !watching(target)) {
			return true; // they walked right into him
		}
		if (stalkTicks >= stalkPlan) {
			return distance < 30.0;
		}
		if (stalkTicks < stalkPlan / 3 || (tickCount + getId()) % 20 != 0) {
			return false;
		}
		float opening = 0.0F;
		float health = target.getHealth() / target.getMaxHealth();
		opening += health < 0.25F ? 2.0F : health < 0.5F ? 1.0F : 0.0F;
		opening += lit(target) ? 0.0F : 1.0F;
		opening += lookedAwayTicks > 200 ? 1.0F : 0.0F;
		opening += stillTicks > 200 ? 1.0F : 0.0F;
		opening += approaching ? 1.0F : 0.0F;
		opening += distance < 14.0 ? 0.5F : 0.0F;
		float needed = 4.0F - 2.0F * (stalkTicks / (float) stalkPlan); // about 3.3 a third of the way in, 2 near the end
		return opening >= needed;
	}

	/** Stepping into view at a distance, staring, and going. Returns true while it is in charge. */
	private boolean peek(ServerLevel level, Player target, boolean watched) {
		if (peekSpot == null) {
			peekSpot = visibleSpot(level, target);
			peekLeft = 200;
			if (peekSpot == null) {
				peekCooldown = 300;
				return false;
			}
		}
		if (watched) {
			watchedTicks++;
		}
		if (--peekLeft <= 0 || watchedTicks > 40) {
			peekSpot = null;
			peekCooldown = 1200 + random.nextInt(1200); // one to two minutes until the next time
			watchedTicks = 0;
			slipping = true;
			stalkSpot = hiddenSpot(level, target, 18.0, 30.0, true);
			replanIn = 60;
			return false;
		}
		if (position().distanceToSqr(peekSpot) > 2.0) {
			if (getNavigation().isDone() || (tickCount + getId()) % 10 == 0) {
				getNavigation().moveTo(peekSpot.x, peekSpot.y, peekSpot.z, 0.8);
			}
		} else {
			getNavigation().stop();
			getLookControl().setLookAt(target, 30.0F, 30.0F);
			if (action() == Action.NONE) {
				act(Action.STARE, 60);
			}
		}
		return true;
	}

	/** A spot {@code near}-{@code far} blocks from the prey that they cannot see, behind them and dark if it can be. */
	private @Nullable Vec3 hiddenSpot(ServerLevel level, Player target, double near, double far, boolean awayFromMe) {
		Vec3 gaze = target.getViewVector(1.0F).multiply(1.0, 0.0, 1.0);
		gaze = gaze.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : gaze.normalize();
		Vec3 best = null;
		double bestScore = Double.MAX_VALUE;
		for (int attempt = 0; attempt < 14; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double r = near + random.nextDouble() * (far - near);
			BlockPos floor = standable(level, BlockPos.containing(target.getX() + Math.cos(angle) * r, target.getY(), target.getZ() + Math.sin(angle) * r));
			if (floor == null) {
				continue;
			}
			Vec3 at = Vec3.atBottomCenterOf(floor);
			Vec3 dir = at.subtract(target.position()).multiply(1.0, 0.0, 1.0).normalize();
			double score = position().distanceTo(at) * (awayFromMe ? -0.2 : 0.4);
			score += visibleFrom(target, at) ? 60.0 : 0.0;
			score += dir.dot(gaze) * 12.0; // behind them is better
			score += level.getMaxLocalRawBrightness(floor) * 0.6; // darker is better
			if (score < bestScore) {
				bestScore = score;
				best = at;
			}
		}
		return best;
	}

	/** A spot in front of the prey, 18-26 blocks off, in their view. */
	private @Nullable Vec3 visibleSpot(ServerLevel level, Player target) {
		Vec3 gaze = target.getViewVector(1.0F).multiply(1.0, 0.0, 1.0);
		gaze = gaze.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : gaze.normalize();
		for (int attempt = 0; attempt < 12; attempt++) {
			Vec3 dir = gaze.yRot((float) ((random.nextDouble() - 0.5) * 1.2));
			double r = 18.0 + random.nextDouble() * 8.0;
			BlockPos floor = standable(level, BlockPos.containing(target.position().add(dir.scale(r))));
			if (floor != null && visibleFrom(target, Vec3.atBottomCenterOf(floor))) {
				return Vec3.atBottomCenterOf(floor);
			}
		}
		return null;
	}

	// ---------------------------------------------------------------------------------------- chase

	private void startChase(ServerLevel level, Player target) {
		if (mode != Mode.CHASE && distanceTo(target) < 24.0) {
			// The moment he comes for them, the dark closes in for a few seconds.
			target.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DARKNESS, 80, 0, true, false));
		}
		setMode(Mode.CHASE);
		setTarget(target);
		lastKnown = target.position();
		long now = level.getGameTime();
		if (now - chaseCueAt > CHASE_CUE_GAP) {
			chaseCueAt = now;
			voice(level, DLSounds.ASESINO_CHASE_START.value(), 1.4F, 1.0F, 60);
		}
	}

	private void chase(ServerLevel level, Player target) {
		double distance = distanceTo(target);
		boolean seen = sees(target);
		if (seen) {
			lastKnown = target.position();
			unseenTicks = 0;
		} else if (++unseenTicks > (com.deadlands.entity.ai.Stealth.inDarkness(target) ? com.deadlands.entity.ai.Stealth.loseTicks() : LOSE_TICKS)) {
			setMode(Mode.SEARCH);
			return;
		}
		// Thinking about himself.
		if (getHealth() < getMaxHealth() * 0.3F && !recovering) {
			recovering = true; // badly hurt: break off and recover somewhere hidden
			startLurk(level, target, 28.0, 44.0, 900 + random.nextInt(600));
			return;
		}
		if (company(target, level.players()) >= 2 && getHealth() < getMaxHealth() * 0.6F) {
			setMode(Mode.STALK); // a group fighting back: wait for a better moment
			stalkTicks = Math.max(stalkTicks, stalkPlan / 2);
			return;
		}
		double ramp = Math.min(1.0, modeTicks / (double) RAMP_TICKS);
		DeadlandsConfig.Asesino config = ConfigManager.get().asesino;
		getAttribute(Attributes.MOVEMENT_SPEED).addOrReplacePermanentModifier(
			new AttributeModifier(CHASE_SPEED, (config.chaseSpeedMax - config.stalkSpeed) * ramp
				+ (distance > 10.0 ? Math.min(0.14, (distance - 10.0) * 0.01) : 0.0), // left behind: he closes the gap, always
				AttributeModifier.Operation.ADD_VALUE));
		if (seen) {
			getLookControl().setLookAt(target, 30.0F, 30.0F);
		}
		if (melee(level, target, distance)) {
			return;
		}
		// Stuck (no way to them, or getting nowhere), or left far behind: he comes back near them, out of sight.
		if (unstuck.tick(this, target, 60) || distance > 40.0 && catchUpCooldown == 0) {
			catchUpCooldown = 300;
			if (com.deadlands.entity.ai.Stalking.teleportNear(this, level, target, 7.0, 13.0, 2.0)) {
				unreachableTicks = 0;
				return;
			}
		}
		// Truly out of reach (up a pillar) and nowhere to appear: wait nearby rather than pace beneath them.
		Path path = getNavigation().getPath();
		boolean reachable = path == null || path.canReach() || distance < 3.0;
		unreachableTicks = reachable ? 0 : unreachableTicks + 1;
		if (unreachableTicks > 240) {
			startLurk(level, target, 8.0, 16.0, 600 + random.nextInt(600));
			return;
		}
		if ((tickCount + getId()) % 5 == 0 || getNavigation().isDone()) {
			Vec3 aim = seen ? target.position() : lastKnown;
			if (aim != null) {
				if (seen && distance > 6.0) {
					aim = aim.add(target.getDeltaMovement().multiply(10.0, 0.0, 10.0)); // where they will be
				}
				getNavigation().moveTo(aim.x, aim.y, aim.z, 1.0);
			}
		}
	}

	/** The slash: a half-second wind-up (the raised arm and the swing sound are the warning), alternating overhead and backhand. */
	private boolean melee(ServerLevel level, Player target, double distance) {
		if (lunge(level, target, distance)) {
			return true;
		}
		if (meleeWindup > 0) {
			getNavigation().stop();
			getLookControl().setLookAt(target, 40.0F, 40.0F);
			if (--meleeWindup == 0) {
				act(backhand ? Action.SWING2 : Action.SWING, SWING_TICKS);
				swing(InteractionHand.MAIN_HAND);
				if (distanceTo(target) < 3.4 && hasLineOfSight(target)) {
					strike(level, target, (float) getAttributeValue(Attributes.ATTACK_DAMAGE));
				}
				backhand = !backhand;
				meleeCooldown = 14 + random.nextInt(8);
			}
			return true;
		}
		if (meleeCooldown == 0 && distance < 2.6) {
			meleeWindup = MELEE_WINDUP;
			act(backhand ? Action.WINDUP2 : Action.WINDUP, MELEE_WINDUP);
			level.playSound(null, getX(), getY(), getZ(), DLSounds.ASESINO_SWING.value(), SoundSource.HOSTILE, 1.0F, 0.95F + random.nextFloat() * 0.1F);
			getNavigation().stop();
			return true;
		}
		return false;
	}

	/**
	 * The lunge: from 4.5 to 9 blocks, in sight, he drops into a crouch with a growl (0.6 s, the warning),
	 * then dashes low and fast at them with the axe out, hitting if he reaches them. A sidestep makes him miss.
	 */
	private boolean lunge(ServerLevel level, Player target, double distance) {
		if (lungeTicks > 0) {
			lungeTicks--;
			if (!lungeHit && distanceTo(target) < 2.0) {
				lungeHit = true;
				level.playSound(null, getX(), getY(), getZ(), DLSounds.ASESINO_POUNCE_HIT.value(), SoundSource.HOSTILE, 1.3F, 1.0F);
				strike(level, target, (float) (getAttributeValue(Attributes.ATTACK_DAMAGE) * ConfigManager.get().asesino.lungeDamageFactor));
			}
			if (lungeTicks == 0 && !lungeHit) {
				level.playSound(null, getX(), getY(), getZ(), DLSounds.ASESINO_POUNCE_MISS.value(), SoundSource.HOSTILE, 1.2F, 1.0F);
				meleeCooldown = Math.max(meleeCooldown, 16); // a moment to recover from the miss
			}
			return true;
		}
		if (lungeWindup > 0) {
			getNavigation().stop();
			getLookControl().setLookAt(target, 40.0F, 40.0F);
			if (--lungeWindup == 0) {
				act(Action.LEAP, 16);
				Vec3 dir = target.position().subtract(position()).multiply(1.0, 0.0, 1.0).normalize();
				setDeltaMovement(dir.x * 1.2, 0.28, dir.z * 1.2);
				hurtMarked = true;
				lungeTicks = 12;
				lungeHit = false;
				lungeCooldown = ConfigManager.get().asesino.lungeCooldownSeconds * 20;
				level.playSound(null, getX(), getY(), getZ(), DLSounds.ASESINO_POUNCE.value(), SoundSource.HOSTILE, 1.4F, 1.0F);
			}
			return true;
		}
		if (lungeCooldown == 0 && meleeWindup == 0 && onGround() && distance > 4.5 && distance < 9.0 && hasLineOfSight(target)
			&& random.nextInt(8) == 0) {
			lungeWindup = 12;
			act(Action.CROUCH, 12);
			level.playSound(null, getX(), getY(), getZ(), DLSounds.ASESINO_FRENZY.value(), SoundSource.HOSTILE, 0.9F, 0.8F);
			getNavigation().stop();
			return true;
		}
		return false;
	}

	private void strike(ServerLevel level, Player target, float damage) {
		if (target.hurtServer(level, damageSources().mobAttack(this), damage)) {
			Vec3 push = target.position().subtract(position()).multiply(1.0, 0.0, 1.0).normalize().scale(0.4);
			target.push(push.x, 0.15, push.z);
			target.hurtMarked = true;
			level.playSound(null, getX(), getY(), getZ(), DLSounds.ASESINO_HIT.value(), SoundSource.HOSTILE, 1.1F, 0.9F + random.nextFloat() * 0.2F);
			if (!target.isAlive()) {
				voice(level, DLSounds.ASESINO_EXECUTION.value(), 1.5F, 1.0F, 60);
				setMode(Mode.ROAM);
			}
		}
	}

	// ---------------------------------------------------------------------------------------- search

	private void search(ServerLevel level, Player target) {
		if (sees(target)) {
			startChase(level, target);
			return;
		}
		if (hears(target)) {
			lastKnown = target.position();
			searchSpots.clear();
			searchSpots.add(target.position()); // straight there
			searchLeft = Math.max(searchLeft, 200);
		}
		if (modeTicks == 1) {
			searchLeft = SEARCH_TICKS;
			planSearch(level);
		}
		if (--searchLeft <= 0) {
			startLurk(level, target, 8.0, 18.0, 1200 + random.nextInt(1200)); // nothing: hide nearby and wait
			return;
		}
		if (lookingAround > 0) {
			lookingAround--;
			getNavigation().stop();
			return;
		}
		if (searchSpots.isEmpty()) {
			planSearch(level);
			if (searchSpots.isEmpty()) {
				return;
			}
		}
		Vec3 spot = searchSpots.getFirst();
		if (position().distanceToSqr(spot) < 2.5 * 2.5) {
			searchSpots.removeFirst();
			lookingAround = 25 + random.nextInt(20); // stop, look, listen
			if (action() == Action.NONE) {
				act(Action.SCENT, 30);
			}
			return;
		}
		if (getNavigation().isDone() || (tickCount + getId()) % 20 == 0) {
			if (!getNavigation().moveTo(spot.x, spot.y, spot.z, 0.95)) {
				searchSpots.removeFirst(); // cannot get there
			}
		}
	}

	/** Where to look: where he lost them, then up to eight hiding places around it, indoors first. */
	private void planSearch(ServerLevel level) {
		searchSpots.clear();
		if (lastKnown == null) {
			return;
		}
		searchSpots.add(lastKnown);
		BlockPos centre = BlockPos.containing(lastKnown);
		List<Vec3> inside = new ArrayList<>();
		List<Vec3> outside = new ArrayList<>();
		for (int attempt = 0; attempt < 48 && inside.size() + outside.size() < 16; attempt++) {
			BlockPos floor = standable(level, centre.offset(random.nextInt(25) - 12, random.nextInt(7) - 3, random.nextInt(25) - 12));
			if (floor != null) {
				(level.canSeeSky(floor) ? outside : inside).add(Vec3.atBottomCenterOf(floor));
			}
		}
		Vec3 from = lastKnown;
		inside.sort(Comparator.comparingDouble(v -> v.distanceToSqr(from)));
		outside.sort(Comparator.comparingDouble(v -> v.distanceToSqr(from)));
		inside.stream().limit(6).forEach(searchSpots::add);
		outside.stream().limit(2).forEach(searchSpots::add);
	}

	/** A spot near {@code probe} with solid ground and two blocks of room (searching up and down a little). */
	private static @Nullable BlockPos standable(ServerLevel level, BlockPos probe) {
		if (!level.isLoaded(probe)) {
			return null;
		}
		for (int dy = 0; dy <= 4; dy++) {
			for (int sign : new int[] {1, -1}) {
				BlockPos at = probe.offset(0, dy * sign, 0);
				if (level.getBlockState(at.below()).isSolid() && level.getBlockState(at).getCollisionShape(level, at).isEmpty()
					&& level.getBlockState(at.above()).getCollisionShape(level, at.above()).isEmpty()
					&& level.getFluidState(at).isEmpty()) {
					return at;
				}
			}
		}
		return null;
	}

	// ---------------------------------------------------------------------------------------- lurk

	/** Hide near the prey, out of their sight, silent (no music) and still, for {@code ticks}. */
	private void startLurk(ServerLevel level, Player target, double near, double far, int ticks) {
		setMode(Mode.LURK);
		lurkLeft = ticks;
		lurkSpot = hiddenSpot(level, target, near, far, false);
	}

	private void lurk(ServerLevel level, Player target) {
		double distance = distanceTo(target);
		if (recovering && tickCount % 40 == 0) {
			heal(1.0F);
		}
		if (lurkSpot != null && position().distanceToSqr(lurkSpot) > 2.0) {
			if (getNavigation().isDone() || (tickCount + getId()) % 20 == 0) {
				getNavigation().moveTo(lurkSpot.x, lurkSpot.y, lurkSpot.z, recovering ? 1.15 : 0.9);
			}
		} else {
			getNavigation().stop();
		}
		boolean seesThem = hasLineOfSight(target) && distance < 24.0;
		boolean alone = company(target, level.players()) == 0;
		if (seesThem && !recovering && alone) {
			if (distance < 10.0 && !watching(target)) {
				startChase(level, target); // they came out: ambush
				return;
			}
			if (watching(target)) {
				lastDistance = distance;
				setMode(Mode.STALK); // spotted: slip back into the shadows
				stalkTicks = Math.max(stalkTicks, stalkPlan - 1200);
				return;
			}
		}
		if (recovering && getHealth() >= getMaxHealth() * 0.7F) {
			recovering = false;
			setMode(Mode.STALK);
			return;
		}
		if (--lurkLeft <= 0 || distance > 96.0) {
			setMode(Mode.STALK); // back to following them, close to deciding
			stalkTicks = Math.max(stalkTicks, stalkPlan - 1800);
		}
	}

	// ======================================================================================== sounds: one voice at a time

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
		if (level() instanceof ServerLevel level && mode == Mode.ROAM) {
			voice(level, DLSounds.ASESINO_AMBIENT.value(), 0.8F, 1.0F, 60); // silent while hunting
		}
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return DLSounds.ASESINO_AMBIENT.value();
	}

	@Override
	public int getAmbientSoundInterval() {
		return 500;
	}

	@Override
	protected void playHurtSound(DamageSource source) {
		if (level() instanceof ServerLevel level && level.getGameTime() - hurtSoundAt > 12) {
			hurtSoundAt = level.getGameTime();
			level.playSound(null, getX(), getY(), getZ(), DLSounds.ASESINO_HURT.value(), SoundSource.HOSTILE, 1.0F, 0.95F + random.nextFloat() * 0.1F);
		}
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return DLSounds.ASESINO_HURT.value();
	}

	@Override
	protected SoundEvent getDeathSound() {
		return DLSounds.ASESINO_DEATH.value();
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		// His own footsteps, paced by how fast he moves (tick); silent while lurking or creeping.
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && source.getEntity() instanceof Player player && huntable(player) && !recovering && mode != Mode.CHASE) {
			prey = player.getUUID();
			startChase(level, player);
		}
		return hurt;
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			Mode now = mode();
			float hunchGoal = switch (now) {
				case ROAM -> 0.05F;
				case STALK -> 0.65F;
				case CHASE -> 0.45F;
				case SEARCH -> 0.25F;
				case LURK -> 1.0F;
			};
			float moving = (float) Math.min(1.0, getDeltaMovement().horizontalDistance() / 0.22);
			hunchO = hunch;
			sprintO = sprint;
			hunch = Mth.approach(hunch, hunchGoal, 0.06F);
			sprint = Mth.approach(sprint, now == Mode.CHASE ? moving : 0.0F, 0.08F);
			return;
		}
		if (!onGround() || mode == Mode.LURK) {
			return;
		}
		double speed = getDeltaMovement().horizontalDistance();
		if (speed < 0.03 || mode == Mode.STALK && speed < 0.12) {
			stepTicks = 0;
			return; // creeping is silent
		}
		int every = speed > 0.2 ? 7 : speed > 0.12 ? 10 : 14;
		if (++stepTicks % every == 0) {
			level().playSound(null, getX(), getY(), getZ(), DLSounds.ASESINO_STEP.value(), SoundSource.HOSTILE,
				mode == Mode.CHASE ? 0.8F : 0.35F, 0.95F + random.nextFloat() * 0.1F);
		}
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public void checkDespawn() {
		// Never.
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	public boolean canPickUpLoot() {
		return false;
	}
}
