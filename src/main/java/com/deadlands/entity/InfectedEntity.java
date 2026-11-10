package com.deadlands.entity;

import com.deadlands.Deadlands;
import com.deadlands.bridge.ClientBridge;
import com.deadlands.config.ConfigManager;
import com.deadlands.entity.ai.BreachGoal;
import com.deadlands.entity.ai.FollowPackGoal;
import com.deadlands.entity.ai.HuntGoal;
import com.deadlands.entity.ai.InfectedAttackGoal;
import com.deadlands.entity.ai.InfectedRoamGoal;
import com.deadlands.entity.ai.InfectedTargetGoal;
import com.deadlands.entity.ai.InvestigateGoal;
import com.deadlands.entity.ai.MigrateGoal;
import com.deadlands.entity.ai.ReturnToNestGoal;
import com.deadlands.entity.ai.SearchGoal;
import com.deadlands.entity.ai.SeekShelterGoal;
import com.deadlands.noise.Loudness;
import com.deadlands.registry.DLSounds;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Base class of every infected. Holds what all kinds share:
 *
 * <ul>
 *   <li><b>Perception.</b> Sight through {@link InfectedTargetGoal} (farther at night, less for
 *       crouching survivors), hearing through {@link #hearNoise}, and at night a pull toward
 *       artificial light ({@link #scanForLight}).</li>
 *   <li><b>Group behaviour.</b> Infected spawned together share a pack and follow its leader;
 *       one that spots a survivor alerts those around it ({@link #alertNearby}).</li>
 *   <li><b>Memory.</b> The last place a target was seen ({@link SearchGoal}), a home nest to
 *       return to ({@link ReturnToNestGoal}), horde and migration membership.</li>
 *   <li><b>Distance-based AI.</b> {@code InfectedManager} gives the nearest infected full-rate AI
 *       and the rest an update every few ticks ({@link #setAiInterval}); between updates they keep
 *       walking with their last input.</li>
 *   <li><b>Looks.</b> A synced variant (outfit and body texture) and physique (build and height),
 *       and animation states the client starts from entity events and flags.</li>
 * </ul>
 */
public class InfectedEntity extends Monster {
	private static final EntityDataAccessor<Integer> DATA_VARIANT = SynchedEntityData.defineId(InfectedEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_PHYSIQUE = SynchedEntityData.defineId(InfectedEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Byte> DATA_FLAGS = SynchedEntityData.defineId(InfectedEntity.class, EntityDataSerializers.BYTE);

	public static final byte EVENT_ATTACK = 4;
	public static final byte EVENT_SLAM = 62;
	public static final byte EVENT_POUNCE = 63;
	public static final byte EVENT_SCREAM = 64;
	public static final byte EVENT_EMERGE = 66;

	public static final int FLAG_BREACHING = 1;
	public static final int FLAG_SCREAMING = 2;
	public static final int FLAG_CLIMBING = 4;
	public static final int FLAG_SWELLING = 8;
	public static final int FLAG_SPRINTING = 16;

	/** Physique: five builds (width) and five heights, packed into the low 8 bits. */
	private static final float[] BUILDS = {0.90F, 0.95F, 1.0F, 1.06F, 1.12F};
	private static final float[] HEIGHTS = {0.94F, 0.97F, 1.0F, 1.03F, 1.06F};

	private static final int ALERT_COOLDOWN = 60;
	private static final double ALERT_RADIUS = 12.0;
	private static final double PACK_ALERT_RADIUS = 24.0;
	private static final int LIGHT_SCAN_INTERVAL = 100;
	private static final int TARGET_MEMORY_TICKS = 600;

	// Client animation (started from entity events and synced flags).
	public final AnimationState attackAnimationState = new AnimationState();
	public final AnimationState screamAnimationState = new AnimationState();
	public final AnimationState slamAnimationState = new AnimationState();
	public final AnimationState pounceAnimationState = new AnimationState();
	public final AnimationState breachAnimationState = new AnimationState();
	public final AnimationState emergeAnimationState = new AnimationState();
	public final AnimationState swellAnimationState = new AnimationState();

	// Membership and memory (saved).
	private long hordeDay;
	private int migrationId;
	private int packId;
	private @Nullable BlockPos homeNest;

	// Perception (not saved: forgetting what you were doing across a restart is fine).
	private @Nullable Vec3 investigatePos;
	private int investigatePriority;
	private long investigateUntil;
	private double investigateSpeed = 1.0;
	private @Nullable Vec3 lastKnownTargetPos;
	private long lastKnownTargetTime;
	private long lastAlertTime;
	private long busySince;

	// Distance-based AI.
	private int aiInterval = 1;
	private boolean aiThrottled;
	private float heldXxa;
	private float heldYya;
	private float heldZza;

	public InfectedEntity(EntityType<? extends InfectedEntity> type, Level level) {
		super(type, level);
		this.xpReward = kind().xp();
		this.getNavigation().setCanOpenDoors(true);
	}

	public static AttributeSupplier.Builder createAttributes(InfectedKind kind) {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, kind.health())
			.add(Attributes.MOVEMENT_SPEED, kind.speed())
			.add(Attributes.ATTACK_DAMAGE, kind.damage())
			.add(Attributes.ARMOR, kind.armor())
			.add(Attributes.KNOCKBACK_RESISTANCE, kind.knockbackResistance())
			.add(Attributes.FOLLOW_RANGE, kind.followRange())
			.add(Attributes.ATTACK_KNOCKBACK, kind.attackKnockback());
	}

	/** This entity's kind, looked up from its type (safe during construction). */
	public final InfectedKind kind() {
		InfectedKind kind = InfectedKind.of(getType());
		return kind != null ? kind : InfectedKind.WALKER;
	}

	// ======================================================================================== goals

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.addKindGoals();
		this.goalSelector.addGoal(2, new BreachGoal(this));
		this.addAttackGoal();
		this.goalSelector.addGoal(4, new HuntGoal(this));
		this.goalSelector.addGoal(5, new SearchGoal(this));
		this.goalSelector.addGoal(6, new InvestigateGoal(this));
		this.goalSelector.addGoal(7, new ReturnToNestGoal(this));
		this.goalSelector.addGoal(7, new SeekShelterGoal(this));
		this.goalSelector.addGoal(8, new MigrateGoal(this));
		this.goalSelector.addGoal(8, new FollowPackGoal(this));
		this.goalSelector.addGoal(9, new InfectedRoamGoal(this));
		this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Player.class, 10.0F));
		this.goalSelector.addGoal(10, new RandomLookAroundGoal(this));

		this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers(InfectedEntity.class));
		this.targetSelector.addGoal(2, new InfectedTargetGoal(this));
		// Survivors are prey too.
		this.targetSelector.addGoal(3, new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(this, com.deadlands.npc.NpcEntity.class, true));
	}

	/** Kind-specific goals at priority 1 (scream, slam, pounce, swell...). */
	protected void addKindGoals() {
	}

	/** The melee goal at priority 3. The Screamer replaces it with keeping its distance. */
	protected void addAttackGoal() {
		this.goalSelector.addGoal(3, new InfectedAttackGoal(this, chaseSpeed(), 20, 6));
	}

	/** Speed multiplier while chasing a target. */
	public double chaseSpeed() {
		return 1.0;
	}

	// ======================================================================================== spawning

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
		@Nullable SpawnGroupData groupData) {
		SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, groupData);
		RandomSource random = level.getRandom();
		this.busySince = level.getLevel().getGameTime();
		setVariant(pickVariant(level.getBiome(blockPosition()), random));
		setPhysique(random.nextInt(BUILDS.length) | random.nextInt(HEIGHTS.length) << 4);
		if (groupData instanceof GroupData pack) {
			this.packId = pack.packId();
			return groupData;
		}
		this.packId = newPackId(random);
		return result;
	}

	/** Shared by infected spawned together, so they follow one leader. */
	public record GroupData(int packId) implements SpawnGroupData {
	}

	public static int newPackId(RandomSource random) {
		return 1 + random.nextInt(Integer.MAX_VALUE - 1);
	}

	private int pickVariant(Holder<Biome> biome, RandomSource random) {
		List<InfectedVariants.Variant> variants = kind().variants();
		if (variants.isEmpty()) {
			return 0;
		}
		String biomeId = biome.unwrapKey().map(key -> key.identifier().getPath()).orElse("");
		int total = 0;
		int[] weights = new int[variants.size()];
		for (int i = 0; i < weights.length; i++) {
			weights[i] = variants.get(i).biomes().contains(biomeId) ? 4 : 1;
			total += weights[i];
		}
		int roll = random.nextInt(total);
		for (int i = 0; i < weights.length; i++) {
			roll -= weights[i];
			if (roll < 0) {
				return i;
			}
		}
		return 0;
	}

	// ======================================================================================== synced data

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_VARIANT, 0);
		builder.define(DATA_PHYSIQUE, 2 | 2 << 4);
		builder.define(DATA_FLAGS, (byte) 0);
	}

	public int variantIndex() {
		int index = this.entityData.get(DATA_VARIANT);
		int count = kind().variants().size();
		return count == 0 ? 0 : Math.floorMod(index, count);
	}

	public InfectedVariants.@Nullable Variant variant() {
		List<InfectedVariants.Variant> variants = kind().variants();
		return variants.isEmpty() ? null : variants.get(variantIndex());
	}

	public void setVariant(int index) {
		this.entityData.set(DATA_VARIANT, index);
	}

	public void setPhysique(int packed) {
		this.entityData.set(DATA_PHYSIQUE, packed & 0xFF);
	}

	public float buildScale() {
		return BUILDS[Mth.clamp(this.entityData.get(DATA_PHYSIQUE) & 0xF, 0, BUILDS.length - 1)];
	}

	public float heightScale() {
		return HEIGHTS[Mth.clamp(this.entityData.get(DATA_PHYSIQUE) >> 4 & 0xF, 0, HEIGHTS.length - 1)];
	}

	public boolean hasFlag(int flag) {
		return (this.entityData.get(DATA_FLAGS) & flag) != 0;
	}

	public void setFlag(int flag, boolean on) {
		byte flags = this.entityData.get(DATA_FLAGS);
		byte updated = (byte) (on ? flags | flag : flags & ~flag);
		if (updated != flags) {
			this.entityData.set(DATA_FLAGS, updated);
		}
	}

	// ======================================================================================== persistence

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("Variant", this.entityData.get(DATA_VARIANT));
		output.putInt("Physique", this.entityData.get(DATA_PHYSIQUE));
		if (hordeDay > 0) {
			output.putLong("HordeDay", hordeDay);
		}
		if (migrationId != 0) {
			output.putInt("Migration", migrationId);
		}
		output.putInt("Pack", packId);
		if (homeNest != null) {
			output.store("Nest", BlockPos.CODEC, homeNest);
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		setVariant(input.getIntOr("Variant", 0));
		setPhysique(input.getIntOr("Physique", 2 | 2 << 4));
		this.hordeDay = input.getLongOr("HordeDay", 0L);
		this.migrationId = input.getIntOr("Migration", 0);
		this.packId = input.getIntOr("Pack", 0);
		this.homeNest = input.read("Nest", BlockPos.CODEC).orElse(null);
	}

	// ======================================================================================== membership

	public long hordeDay() {
		return hordeDay;
	}

	public void setHordeDay(long day) {
		this.hordeDay = day;
	}

	public boolean isHordeMember() {
		return hordeDay > 0;
	}

	public int migrationId() {
		return migrationId;
	}

	public void setMigrationId(int id) {
		this.migrationId = id;
	}

	public int packId() {
		return packId;
	}

	public void setPackId(int packId) {
		this.packId = packId;
	}

	public @Nullable BlockPos homeNest() {
		return homeNest;
	}

	public void setHomeNest(@Nullable BlockPos pos) {
		this.homeNest = pos == null ? null : pos.immutable();
	}

	/** Horde and migration members stay loaded until their system lets them go. */
	@Override
	public boolean requiresCustomPersistence() {
		return super.requiresCustomPersistence() || hordeDay > 0 || migrationId != 0;
	}

	@Override
	public boolean removeWhenFarAway(double distanceSqr) {
		return hordeDay <= 0 && migrationId == 0;
	}

	// ======================================================================================== perception

	/**
	 * A noise reached this infected. Chasing infected ignore it; otherwise a louder (or equally
	 * loud) noise replaces whatever it was investigating. The exact spot is blurred with distance,
	 * so a crowd spreads out around the source instead of stacking on one block.
	 */
	public void hearNoise(Vec3 pos, Loudness loudness, boolean byThreat, double distance) {
		LivingEntity target = getTarget();
		if (target != null && target.isAlive()) {
			return;
		}
		long now = level().getGameTime();
		if (investigatePos != null && now < investigateUntil && loudness.priority() < investigatePriority) {
			return;
		}
		double spread = Math.min(8.0, distance * 0.12);
		investigate(pos.add((random.nextDouble() - 0.5) * 2.0 * spread, 0.0, (random.nextDouble() - 0.5) * 2.0 * spread),
			loudness.priority(), loudness.attentionTicks(), loudness.priority() >= Loudness.LOUD.priority() ? 1.2 : loudness == Loudness.MODERATE ? 1.0 : 0.85);
	}

	public void investigate(Vec3 pos, int priority, int ticks, double speed) {
		this.investigatePos = pos;
		this.investigatePriority = priority;
		this.investigateUntil = level().getGameTime() + ticks;
		this.investigateSpeed = speed;
	}

	public @Nullable Vec3 investigatePos() {
		if (investigatePos != null && level().getGameTime() >= investigateUntil) {
			investigatePos = null;
		}
		return investigatePos;
	}

	public double investigateSpeed() {
		return investigateSpeed;
	}

	public void clearInvestigation() {
		this.investigatePos = null;
		this.investigatePriority = 0;
	}

	public @Nullable Vec3 lastKnownTargetPos() {
		if (lastKnownTargetPos != null && level().getGameTime() - lastKnownTargetTime > TARGET_MEMORY_TICKS) {
			lastKnownTargetPos = null;
		}
		return lastKnownTargetPos;
	}

	public void forgetLastKnownTarget() {
		this.lastKnownTargetPos = null;
	}

	/** Base sight range: farther at night, shorter in daylight and rain. */
	public double detectionRange() {
		double range = getAttributeValue(Attributes.FOLLOW_RANGE);
		Level level = level();
		if (level.isDarkOutside()) {
			range *= 1.15;
		} else {
			range *= 0.8;
		}
		if (level.isRaining()) {
			range *= 0.85;
		}
		return range;
	}

	/**
	 * At night an idle infected looks for artificial light (block light 11+) nearby and walks
	 * toward it. A few random samples every five seconds keep this cheap.
	 */
	protected void scanForLight(ServerLevel level) {
		if (!level.isDarkOutside() || getTarget() != null || investigatePos() != null) {
			return;
		}
		BlockPos origin = blockPosition();
		for (int i = 0; i < 6; i++) {
			BlockPos sample = origin.offset(random.nextInt(41) - 20, random.nextInt(9) - 4, random.nextInt(41) - 20);
			if (level.isLoaded(sample) && level.getBrightness(LightLayer.BLOCK, sample) >= 11) {
				investigate(Vec3.atBottomCenterOf(sample), Loudness.QUIET.priority(), 400, 0.85);
				return;
			}
		}
	}

	// ======================================================================================== alerts

	@Override
	public void setTarget(@Nullable LivingEntity target) {
		LivingEntity previous = getTarget();
		super.setTarget(target);
		if (target != null && target != previous && !level().isClientSide()) {
			clearInvestigation();
			onTargetSpotted(target);
			alertNearby(target);
		}
	}

	/** Set a target passed on by another infected without alerting further (no chain reactions). */
	public void acceptAlert(LivingEntity target) {
		if (getTarget() == null && target.isAlive()) {
			super.setTarget(target);
			clearInvestigation();
		}
	}

	/** Kind reaction to a new target (a Runner's shriek, a Brute's roar). */
	protected void onTargetSpotted(LivingEntity target) {
	}

	protected void alertNearby(LivingEntity target) {
		long now = level().getGameTime();
		if (now - lastAlertTime < ALERT_COOLDOWN || !(level() instanceof ServerLevel level)) {
			return;
		}
		lastAlertTime = now;
		for (InfectedEntity other : level.getEntitiesOfClass(InfectedEntity.class, getBoundingBox().inflate(PACK_ALERT_RADIUS, 6.0, PACK_ALERT_RADIUS),
			e -> e != this && e.isAlive() && e.getTarget() == null)) {
			boolean packmate = packId != 0 && other.packId == packId;
			double distance = other.distanceTo(this);
			if (!packmate && distance > ALERT_RADIUS) {
				continue;
			}
			if (distance < 8.0 || other.getSensing().hasLineOfSight(target)) {
				other.acceptAlert(target);
			} else {
				other.investigate(target.position(), Loudness.MODERATE.priority(), 400, 1.1);
			}
		}
	}

	// ======================================================================================== ticking

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			breachAnimationState.animateWhen(hasFlag(FLAG_BREACHING), tickCount);
			swellAnimationState.animateWhen(hasFlag(FLAG_SWELLING), tickCount);
			if (!hasFlag(FLAG_SCREAMING)) {
				screamAnimationState.stop();
			}
			ClientBridge.infectedTick(this);
		}
	}

	@Override
	public void aiStep() {
		if (!level().isClientSide()) {
			aiThrottled = aiInterval > 1 && (tickCount + getId()) % aiInterval != 0;
		}
		super.aiStep();
	}

	/** Skip the AI on throttled ticks (see {@link #setAiInterval}). */
	@Override
	protected boolean isImmobile() {
		return super.isImmobile() || aiThrottled;
	}

	/** On throttled ticks keep walking with the input of the last AI update. */
	@Override
	public void travel(Vec3 input) {
		if (aiThrottled && !level().isClientSide()) {
			super.travel(new Vec3(heldXxa, heldYya, heldZza));
			return;
		}
		heldXxa = (float) input.x;
		heldYya = (float) input.y;
		heldZza = (float) input.z;
		super.travel(input);
	}

	/** 1 = AI every tick; N = AI every N ticks. Set by the infected manager from distance to players. */
	public void setAiInterval(int interval) {
		this.aiInterval = Math.max(1, interval);
	}

	public int aiInterval() {
		return aiInterval;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		LivingEntity target = getTarget();
		long now = level.getGameTime();
		if (target != null && target.isAlive() && getSensing().hasLineOfSight(target)) {
			lastKnownTargetPos = target.position();
			lastKnownTargetTime = now;
		}
		if (target != null || investigatePos != null) {
			busySince = now;
		}
		if ((tickCount + getId()) % LIGHT_SCAN_INTERVAL == 0) {
			scanForLight(level);
			getNavigation().setCanOpenDoors(ConfigManager.get().zombies.breakBarricades);
		}
	}

	/** Ticks since this infected last chased or investigated something. */
	public long idleTicks() {
		return level().getGameTime() - busySince;
	}

	// ======================================================================================== combat

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		if (hit && target instanceof LivingEntity living) {
			onHit(level, living);
		}
		return hit;
	}

	/** Kind effect of a landed hit (fire, slowness...). */
	protected void onHit(ServerLevel level, LivingEntity target) {
	}

	/** Start the attack animation on clients; the damage lands a few ticks later (see the attack goal). */
	public void startAttackAnimation() {
		level().broadcastEntityEvent(this, EVENT_ATTACK);
	}

	@Override
	public void handleEntityEvent(byte id) {
		switch (id) {
			case EVENT_ATTACK -> attackAnimationState.start(tickCount);
			case EVENT_SLAM -> slamAnimationState.start(tickCount);
			case EVENT_POUNCE -> pounceAnimationState.start(tickCount);
			case EVENT_SCREAM -> screamAnimationState.start(tickCount);
			case EVENT_EMERGE -> emergeAnimationState.start(tickCount);
			default -> super.handleEntityEvent(id);
		}
	}

	// ======================================================================================== sounds

	/**
	 * Idle voices are chosen on each client (distance, walls and the listener's surroundings change
	 * what is heard), so the server plays none and sends no packets for them.
	 */
	@Override
	public void playAmbientSound() {
		if (level().isClientSide() && kind().vocal()) {
			ClientBridge.infectedAmbient(this);
		}
	}

	@Override
	public int getAmbientSoundInterval() {
		return 160;
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return kind().ambientSound().value();
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return kind().hurtSound().value();
	}

	@Override
	protected SoundEvent getDeathSound() {
		return kind().deathSound().value();
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		SoundType ground = state.getSoundType();
		this.playSound(ground.getStepSound(), ground.getVolume() * 0.12F, ground.getPitch());
		this.playSound(DLSounds.INFECTED_STEP.value(), 0.35F, 0.9F + random.nextFloat() * 0.2F);
	}

	@Override
	protected float getSoundVolume() {
		return 0.9F;
	}

	// ======================================================================================== loot

	/** Rolls the kind's table, then the outfit's table (a police officer may carry a key...). */
	@Override
	protected void dropFromLootTable(ServerLevel level, DamageSource source, boolean playerKill) {
		super.dropFromLootTable(level, source, playerKill);
		InfectedVariants.Variant variant = variant();
		if (variant != null && !variant.outfit().isEmpty()) {
			dropFromLootTable(level, source, playerKill,
				ResourceKey.create(Registries.LOOT_TABLE, Deadlands.id("entities/outfit/" + variant.outfit())));
		}
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	public boolean hurtServer(net.minecraft.server.level.ServerLevel level, net.minecraft.world.damagesource.DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && amount > 0.5F) {
			level.sendParticles(com.deadlands.registry.DLParticles.BLOOD, getX(), getY() + getBbHeight() * 0.6, getZ(), Math.min(12, 3 + (int) amount),
				getBbWidth() * 0.3, getBbHeight() * 0.2, getBbWidth() * 0.3, 0.08);
		}
		return hurt;
	}
}
