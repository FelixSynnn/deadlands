package com.deadlands.entity;

import com.deadlands.Deadlands;
import com.deadlands.config.ConfigManager;
import com.deadlands.config.DeadlandsConfig;
import com.deadlands.registry.DLSounds;
import com.deadlands.registry.DLTags;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Asesino (Phase 5): DEADLANDS' slasher. Before the outbreak he was the county's unsolved killer,
 * "the Night Caller"; caught at last, he was moved to Kessler for an NR-7 trial on violent inmates.
 * NR-7 kept the hunter and erased the rest.
 *
 * <p>His mind is a state machine, not vanilla targeting:
 * <ul>
 *   <li><b>Roam</b>: wanders at night, picking a prey: the most isolated, then the most hurt, survivor
 *       he can see or hear (he leaves groups of three or more alone until someone strays).</li>
 *   <li><b>Stalk</b>: keeps 20 to 30 blocks off, behind the prey and out of their sight, until they are
 *       alone and either looking away for a while, closer than 12 blocks, or he has stalked long enough
 *       (or they hurt him).</li>
 *   <li><b>Chase</b>: runs for where the prey is going (their movement led a half second ahead), breaks
 *       doors and barricades in his way, and gets faster the longer it lasts.</li>
 *   <li><b>Search</b>: twelve seconds without seeing the prey, he hunts around where he last saw them;
 *       ten seconds more and he gives up and roams again, leaving that survivor alone for a minute.</li>
 * </ul>
 * Melee has a half-second wind-up. Abilities: Pounce Cut, Breach, Scent of Fear, Frenzy, Dread Shriek
 * (see each method). Stats come from the {@code asesino} config section. By day he slips away.
 */
public class AsesinoEntity extends Monster {
	public enum Mode {
		ROAM, STALK, CHASE, SEARCH
	}

	private static final EntityDataAccessor<Integer> DATA_PREY = SynchedEntityData.defineId(AsesinoEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_MODE = SynchedEntityData.defineId(AsesinoEntity.class, EntityDataSerializers.INT);
	private static final Identifier FRENZY = Deadlands.id("asesino_frenzy");
	private static final Identifier CHASE_SPEED = Deadlands.id("asesino_chase_speed");

	private static final int MELEE_WINDUP = 10;
	private static final int POUNCE_COOLDOWN = 240;
	private static final int POUNCE_CROUCH = 12;
	private static final int POUNCE_FLIGHT = 16;
	private static final int STUN_TICKS = 30;
	private static final int BREACH_COOLDOWN = 120;
	private static final int BREACH_WINDUP = 24;
	private static final int SCENT_COOLDOWN = 900;
	private static final int SCENT_TICKS = 120;
	private static final int FRENZY_COOLDOWN = 500;
	private static final int FRENZY_TICKS = 100;
	private static final int SHRIEK_COOLDOWN = 600;
	private static final int SHRIEK_WINDUP = 20;
	private static final int LOSE_TICKS = 240;
	private static final int SEARCH_TICKS = 200;
	private static final int RAMP_TICKS = 600;

	private Mode mode = Mode.ROAM;
	private @Nullable UUID prey;
	private int modeTicks;
	private int unseenTicks;
	private int lookedAwayTicks;
	private @Nullable Vec3 lastKnown;
	private final Map<UUID, Long> leftAlone = new HashMap<>();
	private final Map<UUID, Integer> bleeding = new HashMap<>();
	private int meleeCooldown;
	private int meleeWindup;
	private int pounceCooldown = 80;
	private int pounceCrouch;
	private int pounceFlight;
	private boolean pounceHit;
	private int stunned;
	private int breachCooldown;
	private int breachWindup;
	private @Nullable BlockPos breaching;
	private final Map<BlockPos, Integer> breachHits = new HashMap<>();
	private int scentCooldown = 200;
	private int scenting;
	private int frenzyCooldown;
	private int frenzy;
	private int shriekCooldown = 200;
	private int shriekWindup;
	private int stepTicks;

	public AsesinoEntity(EntityType<? extends AsesinoEntity> type, Level level) {
		super(type, level);
		this.xpReward = 60;
		if (getNavigation() instanceof GroundPathNavigation ground) {
			ground.setCanOpenDoors(false);
			ground.getNodeEvaluator().setCanPassDoors(true); // paths go through doors; Breach takes care of them
			ground.setCanFloat(true);
		}
		applyConfig();
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

	/** The config's stats (it can change between worlds). */
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
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(6, new RoamGoal(this));
		goalSelector.addGoal(7, new RandomLookAroundGoal(this) {
			@Override
			public boolean canUse() {
				return mode == Mode.ROAM && super.canUse();
			}
		});
	}

	/** Wandering, only while roaming. */
	private static final class RoamGoal extends WaterAvoidingRandomStrollGoal {
		private final AsesinoEntity asesino;

		RoamGoal(AsesinoEntity asesino) {
			super(asesino, 0.9);
			this.asesino = asesino;
		}

		@Override
		public boolean canUse() {
			return asesino.mode == Mode.ROAM && asesino.stunned == 0 && super.canUse();
		}

		@Override
		public boolean canContinueToUse() {
			return asesino.mode == Mode.ROAM && super.canContinueToUse();
		}
	}

	// ======================================================================================== what the client reads

	/** The entity id of the survivor he is chasing (the chase music's layer 4), or -1. */
	public int chasingId() {
		return entityData.get(DATA_PREY);
	}

	public Mode mode() {
		return Mode.values()[Mth.clamp(entityData.get(DATA_MODE), 0, Mode.values().length - 1)];
	}

	// ======================================================================================== the mind

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		modeTicks++;
		tickTimers();
		tickBleeding(level);
		if (stunned > 0) {
			getNavigation().stop();
			return;
		}
		if (pounceCrouch > 0 || pounceFlight > 0) {
			tickPounce(level);
			return;
		}
		if (breachWindup > 0) {
			tickBreach(level);
			return;
		}
		if (shriekWindup > 0) {
			tickShriek(level);
			return;
		}
		Player target = preyPlayer(level);
		switch (mode) {
			case ROAM -> roam(level);
			case STALK -> stalk(level, target);
			case CHASE -> chase(level, target);
			case SEARCH -> search(level, target);
		}
		if (level.isBrightOutside() && mode == Mode.ROAM && level.getNearestPlayer(this, 32.0) == null && modeTicks > 100) {
			discard(); // by day he slips away
		}
	}

	private void tickTimers() {
		meleeCooldown = Math.max(0, meleeCooldown - 1);
		pounceCooldown = Math.max(0, pounceCooldown - 1);
		breachCooldown = Math.max(0, breachCooldown - 1);
		scentCooldown = Math.max(0, scentCooldown - 1);
		frenzyCooldown = Math.max(0, frenzyCooldown - 1);
		shriekCooldown = Math.max(0, shriekCooldown - 1);
		if (stunned > 0 && --stunned == 0) {
			setPose(Pose.STANDING);
		}
		if (scenting > 0) {
			scenting--;
		}
		if (frenzy > 0 && --frenzy == 0) {
			getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(FRENZY);
		}
	}

	private void setMode(Mode next) {
		if (mode == next) {
			return;
		}
		mode = next;
		modeTicks = 0;
		unseenTicks = 0;
		lookedAwayTicks = 0;
		entityData.set(DATA_MODE, next.ordinal());
		if (next != Mode.CHASE) {
			getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(CHASE_SPEED);
			entityData.set(DATA_PREY, -1);
		}
		if (next == Mode.ROAM) {
			prey = null;
			lastKnown = null;
			setTarget(null);
		}
	}

	private @Nullable Player preyPlayer(ServerLevel level) {
		if (prey == null) {
			return null;
		}
		Player player = level.getPlayerByUUID(prey);
		if (player == null || !player.isAlive() || player.isSpectator() || player.isCreative() || player.level() != level()) {
			return null;
		}
		return player;
	}

	/** Roam: look for prey (seen within the follow range, or heard close by), or sniff it out with Scent of Fear. */
	private void roam(ServerLevel level) {
		if ((tickCount + getId()) % 20 != 0) {
			return;
		}
		double range = getAttributeValue(Attributes.FOLLOW_RANGE);
		Player best = null;
		double bestScore = Double.MAX_VALUE;
		long now = level.getGameTime();
		leftAlone.values().removeIf(until -> until < now);
		List<? extends Player> players = level.players();
		for (Player player : players) {
			if (!huntable(player) || leftAlone.containsKey(player.getUUID())) {
				continue;
			}
			double distance = distanceTo(player);
			boolean noticed = distance < 16.0 || distance < range && hasLineOfSight(player) || scenting > 0 && distance < 40.0;
			if (!noticed) {
				continue;
			}
			int company = company(player, players);
			if (company >= 2) {
				continue; // a group of three: wait for someone to stray
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
			setMode(Mode.STALK);
			return;
		}
		if (scentCooldown == 0) {
			scentOfFear(level);
		}
	}

	private boolean huntable(Player player) {
		return player.isAlive() && !player.isSpectator() && !player.isCreative();
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

	/** Stalk: keep 20 to 30 blocks off, behind the prey and out of their sight, then commit. */
	private void stalk(ServerLevel level, @Nullable Player target) {
		if (target == null || distanceTo(target) > getAttributeValue(Attributes.FOLLOW_RANGE) + 16.0) {
			setMode(Mode.ROAM);
			return;
		}
		boolean seen = hasLineOfSight(target);
		if (seen) {
			lastKnown = target.position();
		}
		getLookControl().setLookAt(target, 20.0F, 20.0F);
		double distance = distanceTo(target);
		boolean watched = watching(target);
		lookedAwayTicks = watched ? 0 : lookedAwayTicks + 1;
		boolean alone = company(target, level.players()) == 0;
		boolean commit = alone && (lookedAwayTicks > 60 && distance < 30.0 || distance < 12.0 || modeTicks > 800)
			|| getLastHurtByMob() == target && tickCount - getLastHurtByMobTimestamp() < 40;
		if (commit) {
			startChase(level, target);
			return;
		}
		if ((tickCount + getId()) % 10 != 0) {
			return;
		}
		Vec3 spot;
		if (watched && distance < 34.0) {
			// Seen: step out of their sight, sideways from their gaze.
			Vec3 gaze = target.getViewVector(1.0F);
			Vec3 side = new Vec3(-gaze.z, 0.0, gaze.x).normalize().scale((getId() & 1) == 0 ? 8.0 : -8.0);
			spot = position().add(side);
		} else {
			// Hang back behind them, at about 24 blocks.
			Vec3 behind = target.getViewVector(1.0F).multiply(1.0, 0.0, 1.0).normalize().scale(-24.0);
			spot = target.position().add(behind);
			if (distance > 20.0 && distance < 30.0 && position().distanceTo(spot) < 10.0) {
				getNavigation().stop();
				return;
			}
		}
		getNavigation().moveTo(spot.x, spot.y, spot.z, 0.8);
	}

	/** Whether the prey is looking roughly at him. */
	private boolean watching(Player target) {
		Vec3 to = position().add(0.0, getEyeHeight(), 0.0).subtract(target.getEyePosition()).normalize();
		return target.getViewVector(1.0F).dot(to) > 0.75 && target.hasLineOfSight(this);
	}

	private void startChase(ServerLevel level, Player target) {
		setMode(Mode.CHASE);
		setTarget(target);
		entityData.set(DATA_PREY, target.getId());
		playSound(DLSounds.ASESINO_CHASE_START.value(), 1.4F, 1.0F);
	}

	/** Chase: lead the prey's movement, break through what is in the way, speed up; lose them after twelve unseen seconds. */
	private void chase(ServerLevel level, @Nullable Player target) {
		if (target == null) {
			setMode(Mode.ROAM);
			return;
		}
		boolean seen = hasLineOfSight(target);
		if (seen) {
			lastKnown = target.position();
			unseenTicks = 0;
		} else if (++unseenTicks > LOSE_TICKS) {
			setMode(Mode.SEARCH);
			return;
		}
		double ramp = Math.min(1.0, modeTicks / (double) RAMP_TICKS);
		DeadlandsConfig.Asesino config = ConfigManager.get().asesino;
		double extra = (config.chaseSpeedMax - config.stalkSpeed) * ramp;
		getAttribute(Attributes.MOVEMENT_SPEED).addOrReplacePermanentModifier(new AttributeModifier(CHASE_SPEED, extra, AttributeModifier.Operation.ADD_VALUE));
		getLookControl().setLookAt(target, 30.0F, 30.0F);
		double distance = distanceTo(target);

		if (frenzyCooldown == 0 && (modeTicks > 200 && seen || getHealth() < getMaxHealth() * 0.5F)) {
			frenzy();
		}
		if (shriekCooldown == 0 && seen && distance < 12.0 && distance > 3.5 && fleeing(target)) {
			shriekWindup = SHRIEK_WINDUP;
			getNavigation().stop();
			return;
		}
		if (pounceCooldown == 0 && seen && distance > 5.0 && distance < 9.0 && Math.abs(target.getY() - getY()) < 2.0) {
			pounceCrouch = POUNCE_CROUCH;
			setPose(Pose.CROUCHING);
			getNavigation().stop();
			return;
		}
		if (melee(level, target, distance)) {
			return;
		}
		if (breachCooldown == 0 && blocked() && griefing(level)) {
			BlockPos block = findBarricade(target.position());
			if (block != null) {
				breaching = block;
				breachWindup = BREACH_WINDUP;
				getNavigation().stop();
				return;
			}
		}
		if ((tickCount + getId()) % 6 == 0 || getNavigation().isDone()) {
			Vec3 aim = seen ? target.position() : lastKnown;
			if (seen && distance > 6.0) {
				aim = aim.add(target.getDeltaMovement().multiply(10.0, 0.0, 10.0)); // where they will be
			}
			getNavigation().moveTo(aim.x, aim.y, aim.z, 1.0);
		}
	}

	private boolean fleeing(Player target) {
		Vec3 away = target.position().subtract(position()).multiply(1.0, 0.0, 1.0).normalize();
		Vec3 moving = target.getDeltaMovement().multiply(1.0, 0.0, 1.0);
		return moving.lengthSqr() > 0.004 && moving.normalize().dot(away) > 0.5;
	}

	/** Search: hunt around where he last saw the prey; give up after ten seconds. */
	private void search(ServerLevel level, @Nullable Player target) {
		if (target != null && hasLineOfSight(target) && distanceTo(target) < 40.0) {
			startChase(level, target);
			return;
		}
		if (scentCooldown == 0) {
			scentOfFear(level);
		}
		if (scenting > 0 && target != null && distanceTo(target) < 40.0) {
			lastKnown = target.position(); // the scent gives them away
		}
		if (modeTicks > SEARCH_TICKS) {
			if (prey != null) {
				leftAlone.put(prey, level.getGameTime() + 1200L);
			}
			setMode(Mode.ROAM);
			return;
		}
		if (lastKnown != null && (getNavigation().isDone() || modeTicks % 40 == 0)) {
			Vec3 around = lastKnown.add(random.nextGaussian() * 6.0, 0.0, random.nextGaussian() * 6.0);
			getNavigation().moveTo(around.x, around.y, around.z, 0.9);
		}
	}

	// ======================================================================================== melee

	/** A heavy hit with a half-second wind-up (the swing sound and arm are the warning). */
	private boolean melee(ServerLevel level, Player target, double distance) {
		if (meleeWindup > 0) {
			getNavigation().stop();
			if (--meleeWindup == 0) {
				if (distanceTo(target) < 3.4 && hasLineOfSight(target)) {
					swing(InteractionHand.MAIN_HAND);
					if (doHurtTarget(level, target)) {
						playSound(DLSounds.ASESINO_HIT.value(), 1.2F, 0.9F + random.nextFloat() * 0.2F);
						if (!target.isAlive()) {
							killed(level);
						}
					}
				}
				meleeCooldown = 16;
			}
			return true;
		}
		if (meleeCooldown == 0 && distance < 2.6) {
			meleeWindup = MELEE_WINDUP;
			playSound(DLSounds.ASESINO_SWING.value(), 1.1F, 0.95F + random.nextFloat() * 0.1F);
			getNavigation().stop();
			return true;
		}
		return false;
	}

	private void killed(ServerLevel level) {
		level.playSound(null, getX(), getY(), getZ(), DLSounds.ASESINO_EXECUTION.value(), SoundSource.HOSTILE, 1.5F, 1.0F);
		setMode(Mode.ROAM);
	}

	// ======================================================================================== abilities

	/** Pounce Cut: a 0.6 s crouch, then a leap: 10 damage and four seconds of bleeding. A miss leaves him stunned for 1.5 s. */
	private void tickPounce(ServerLevel level) {
		Player target = preyPlayer(level);
		if (pounceCrouch > 0) {
			getNavigation().stop();
			if (target != null) {
				getLookControl().setLookAt(target, 40.0F, 40.0F);
			}
			if (--pounceCrouch == 0) {
				setPose(Pose.STANDING);
				if (target == null) {
					pounceCooldown = POUNCE_COOLDOWN;
					return;
				}
				Vec3 to = target.position().add(target.getDeltaMovement().scale(6.0)).subtract(position());
				Vec3 flat = new Vec3(to.x, 0.0, to.z);
				double reach = Math.min(flat.length(), 9.0);
				Vec3 leap = flat.normalize().scale(0.25 + reach * 0.11);
				setDeltaMovement(leap.x, 0.42, leap.z);
				hurtMarked = true;
				pounceFlight = POUNCE_FLIGHT;
				pounceHit = false;
				playSound(DLSounds.ASESINO_POUNCE.value(), 1.3F, 1.0F);
			}
			return;
		}
		pounceFlight--;
		if (!pounceHit && target != null && getBoundingBox().inflate(0.6).intersects(target.getBoundingBox())) {
			pounceHit = true;
			swing(InteractionHand.MAIN_HAND);
			target.hurtServer(level, damageSources().mobAttack(this), 10.0F);
			bleeding.put(target.getUUID(), 80);
			level.playSound(null, getX(), getY(), getZ(), DLSounds.ASESINO_POUNCE_HIT.value(), SoundSource.HOSTILE, 1.3F, 1.0F);
			if (!target.isAlive()) {
				killed(level);
			}
		}
		if (pounceFlight <= 0 || pounceHit && onGround()) {
			pounceFlight = 0;
			pounceCooldown = POUNCE_COOLDOWN;
			if (!pounceHit) {
				stunned = STUN_TICKS;
				setPose(Pose.CROUCHING);
				level.playSound(null, getX(), getY(), getZ(), DLSounds.ASESINO_POUNCE_MISS.value(), SoundSource.HOSTILE, 1.2F, 1.0F);
				level.playSound(null, getX(), getY(), getZ(), DLSounds.ASESINO_STUN.value(), SoundSource.HOSTILE, 1.2F, 1.0F);
			}
		}
	}

	private void tickBleeding(ServerLevel level) {
		Iterator<Map.Entry<UUID, Integer>> it = bleeding.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Integer> entry = it.next();
			int left = entry.getValue() - 1;
			Player player = level.getPlayerByUUID(entry.getKey());
			if (player == null || !player.isAlive() || left <= 0) {
				it.remove();
				continue;
			}
			entry.setValue(left);
			if (left % 20 == 0) {
				player.hurtServer(level, damageSources().generic(), 1.0F);
			}
		}
	}

	/** Breach: a 1.2 s wind-up, then he smashes the door, gate, trapdoor or barricade (an iron door takes three). */
	private void tickBreach(ServerLevel level) {
		getNavigation().stop();
		if (breaching != null) {
			getLookControl().setLookAt(Vec3.atCenterOf(breaching));
		}
		if (--breachWindup > 0) {
			return;
		}
		breachCooldown = BREACH_COOLDOWN;
		BlockPos block = breaching;
		breaching = null;
		if (block == null || !breakable(block)) {
			return;
		}
		swing(InteractionHand.MAIN_HAND);
		level.playSound(null, block, DLSounds.ASESINO_BREACH.value(), SoundSource.HOSTILE, 1.4F, 0.8F);
		BlockState state = level.getBlockState(block);
		int needed = state.is(Blocks.IRON_DOOR) || state.is(Blocks.IRON_TRAPDOOR) ? 3 : 1;
		int hits = breachHits.merge(block.immutable(), 1, Integer::sum);
		level.destroyBlockProgress(getId(), block, Math.min(9, hits * 10 / needed));
		if (hits >= needed) {
			breachHits.remove(block);
			level.destroyBlockProgress(getId(), block, -1);
			level.destroyBlock(block, true, this);
			breachCooldown = 20; // straight on through
		}
	}

	private boolean blocked() {
		return horizontalCollision || getNavigation().isStuck() || getNavigation().isDone();
	}

	private boolean griefing(ServerLevel level) {
		return ConfigManager.get().zombies.breakBarricades && level.getGameRules().get(GameRules.MOB_GRIEFING);
	}

	private boolean breakable(BlockPos pos) {
		BlockState state = level().getBlockState(pos);
		return state.is(DLTags.INFECTED_BREAKABLE) || state.is(DLTags.BRUTE_BREAKABLE) || state.is(Blocks.IRON_DOOR) || state.is(Blocks.IRON_TRAPDOOR);
	}

	/** A breakable block on the next steps of his path, or straight ahead toward the goal. */
	private @Nullable BlockPos findBarricade(Vec3 goal) {
		Path path = getNavigation().getPath();
		if (path != null && !path.isDone()) {
			int end = Math.min(path.getNextNodeIndex() + 2, path.getNodeCount());
			for (int i = path.getNextNodeIndex(); i < end; i++) {
				Node node = path.getNode(i);
				for (int dy = 0; dy <= 1; dy++) {
					BlockPos pos = new BlockPos(node.x, node.y + dy, node.z);
					if (pos.distToCenterSqr(position()) < 9.0 && breakable(pos)) {
						return pos;
					}
				}
			}
		}
		Vec3 ahead = goal.subtract(position()).multiply(1.0, 0.0, 1.0);
		if (ahead.lengthSqr() < 1.0E-4) {
			return null;
		}
		ahead = ahead.normalize();
		for (double step = 0.8; step <= 2.0; step += 0.6) {
			for (int dy = 0; dy <= 1; dy++) {
				BlockPos pos = BlockPos.containing(getX() + ahead.x * step, getY() + dy + 0.1, getZ() + ahead.z * step);
				if (breakable(pos)) {
					return pos;
				}
			}
		}
		return null;
	}

	/** Scent of Fear: for six seconds he knows where every survivor within 40 blocks is; they feel it. */
	private void scentOfFear(ServerLevel level) {
		scentCooldown = SCENT_COOLDOWN;
		scenting = SCENT_TICKS;
		level.playSound(null, getX(), getY(), getZ(), DLSounds.ASESINO_SCENT.value(), SoundSource.HOSTILE, 1.0F, 0.6F);
		for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(40.0), this::huntable)) {
			player.connection.send(new ClientboundSetActionBarTextPacket(Component.translatable("entity.deadlands.asesino.watched").withStyle(ChatFormatting.DARK_RED)));
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), DLSounds.ASESINO_SCENT.value(), SoundSource.HOSTILE, 0.5F, 0.5F);
			if (mode == Mode.ROAM && prey == null && company(player, level.players()) < 2) {
				prey = player.getUUID();
				lastKnown = player.position();
				setMode(Mode.STALK);
			}
		}
	}

	/** Frenzy: +35% speed for five seconds. */
	private void frenzy() {
		frenzyCooldown = FRENZY_COOLDOWN;
		frenzy = FRENZY_TICKS;
		getAttribute(Attributes.MOVEMENT_SPEED).addOrReplacePermanentModifier(
			new AttributeModifier(FRENZY, 0.35, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		playSound(DLSounds.ASESINO_FRENZY.value(), 1.5F, 1.0F);
	}

	/** Dread Shriek: after a one-second wind-up, Slowness II (3 s) and Darkness (2 s) for everyone within 12 blocks. */
	private void tickShriek(ServerLevel level) {
		getNavigation().stop();
		if (shriekWindup == SHRIEK_WINDUP) {
			level.playSound(null, getX(), getY(), getZ(), DLSounds.ASESINO_SHRIEK.value(), SoundSource.HOSTILE, 2.0F, 0.85F);
		}
		if (--shriekWindup > 0) {
			return;
		}
		shriekCooldown = SHRIEK_COOLDOWN;
		for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(12.0), this::huntable)) {
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1), this);
			player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0), this);
		}
	}

	// ======================================================================================== the rest

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && source.getEntity() instanceof Player player && huntable(player) && (mode == Mode.ROAM || mode == Mode.STALK)) {
			prey = player.getUUID();
			startChase(level, player);
		}
		return hurt;
	}

	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide() && onGround() && getDeltaMovement().horizontalDistanceSqr() > 0.002 && ++stepTicks % (mode == Mode.CHASE ? 6 : 10) == 0) {
			playSound(DLSounds.ASESINO_STEP.value(), mode == Mode.CHASE ? 0.9F : 0.5F, 0.95F + random.nextFloat() * 0.1F);
		}
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		// His own footsteps (tick).
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return mode == Mode.STALK ? null : DLSounds.ASESINO_AMBIENT.value(); // silent while stalking
	}

	@Override
	public int getAmbientSoundInterval() {
		return 400;
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
	public boolean removeWhenFarAway(double distance) {
		return mode == Mode.ROAM && distance > 128.0 * 128.0;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}
}
