package com.deadlands.entity;

import com.deadlands.Deadlands;
import com.deadlands.config.ConfigManager;
import com.deadlands.horde.BossDirector;
import com.deadlands.npc.NpcEntity;
import com.deadlands.progression.BossKind;
import com.deadlands.registry.DLBosses;
import com.deadlands.registry.DLSounds;
import com.deadlands.registry.DLStory;
import com.deadlands.registry.DLSurvivalItems;
import com.deadlands.registry.DLWeapons;
import com.deadlands.spawning.SpawnFinder;
import com.deadlands.story.Lore;
import com.deadlands.story.LoreEntry;
import com.deadlands.survival.SurvivalAttachments;
import com.deadlands.survival.SurvivalData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * A boss: one of the five large forms ({@link BossKind}), each its own entity type sharing this class.
 *
 * <p>They hunt the nearest survivor across 64 blocks and fight with a basic swing plus their own
 * attacks, chosen by distance and cooldown, each with a wind-up the client animates from
 * {@link #attack()} (synced) before it lands:
 *
 * <ul>
 *   <li><b>The Abomination</b> (Ward C): SLAM, a ground blow that throws everyone near; THROW, a
 *       grab and hurl; and at three-quarters, half and a quarter health it sheds crawlers.</li>
 *   <li><b>The Butcher</b> (Leon Vask): HOOK, a chain thrown to drag a survivor in from range;
 *       his cleaver makes wounds bleed; below half health he enrages and moves faster.</li>
 *   <li><b>The Warden</b> (Colonel Hale): a riot shield that turns most of a blow from the front;
 *       BASH, which knocks back and slows; POUND, a stamp that hurts everyone near; CALL, which brings
 *       runners.</li>
 *   <li><b>The Experiment</b> (Subject E-12): LEAP across the gap; SPIT, a caustic jet that
 *       poisons; it regenerates when it has not been hurt for three seconds, unless burning.</li>
 *   <li><b>Patient Zero</b> (Elias): BLINK, gone and back beside its target; SUMMON, infected rise
 *       around it (more often below half health); its blows drain life back into it; and it brings
 *       darkness over everyone near.</li>
 * </ul>
 *
 * <p>Health and damage scale with the boss settings and the escalation tier (half again per pass
 * through the roster). A boss bar shows to everyone in range. Defeated, it drops what the story needs
 * (its trophy and its lore), rounds and medicine.
 */
public class BossEntity extends Monster {
	public static final byte NONE = 0;
	public static final byte SWING = 1;
	public static final byte SLAM = 2;
	public static final byte THROW = 3;
	public static final byte HOOK = 4;
	public static final byte BASH = 6;
	public static final byte CALL = 7;
	public static final byte LEAP = 8;
	public static final byte SPIT = 9;
	public static final byte BLINK = 10;
	public static final byte SUMMON = 11;
	public static final byte ROAR = 12;
	public static final byte POUND = 13;

	private static final EntityDataAccessor<Byte> DATA_ATTACK = SynchedEntityData.defineId(BossEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Integer> DATA_TIER = SynchedEntityData.defineId(BossEntity.class, EntityDataSerializers.INT);

	private final BossKind kind;
	private final ServerBossEvent bossBar;
	private int attackTicks;
	private int attackLength;
	private int impactAt;
	private final int[] cooldowns = new int[16];
	private int shed;
	private boolean enraged;
	private int lastHurtTick;
	private long encounterDay;
	/** Client: when the current attack began, for the animation. */
	private int attackStartTick;

	public BossEntity(EntityType<? extends BossEntity> type, Level level) {
		super(type, level);
		BossKind bound = DLBosses.kindOf(type);
		this.kind = bound == null ? BossKind.ABOMINATION : bound;
		this.bossBar = new ServerBossEvent(kind.displayName(), kind == BossKind.PATIENT_ZERO ? BossEvent.BossBarColor.PURPLE : BossEvent.BossBarColor.RED,
			BossEvent.BossBarOverlay.NOTCHED_10);
		this.xpReward = 150;
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes(BossKind kind) {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, baseHealth(kind))
			.add(Attributes.ATTACK_DAMAGE, baseDamage(kind))
			.add(Attributes.MOVEMENT_SPEED, switch (kind) {
				case ABOMINATION -> 0.21;
				case BUTCHER -> 0.27;
				case WARDEN -> 0.25;
				case EXPERIMENT -> 0.31;
				case PATIENT_ZERO -> 0.28;
			})
			.add(Attributes.ARMOR, kind == BossKind.WARDEN ? 14.0 : kind == BossKind.ABOMINATION ? 6.0 : 4.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, kind == BossKind.EXPERIMENT ? 0.6 : 1.0)
			.add(Attributes.FOLLOW_RANGE, 64.0)
			.add(Attributes.STEP_HEIGHT, 1.0);
	}

	private static double baseHealth(BossKind kind) {
		return switch (kind) {
			case ABOMINATION -> 320.0;
			case BUTCHER -> 240.0;
			case WARDEN -> 280.0;
			case EXPERIMENT -> 220.0;
			case PATIENT_ZERO -> 400.0;
		};
	}

	private static double baseDamage(BossKind kind) {
		return switch (kind) {
			case ABOMINATION -> 10.0;
			case BUTCHER -> 12.0;
			case WARDEN -> 9.0;
			case EXPERIMENT -> 8.0;
			case PATIENT_ZERO -> 11.0;
		};
	}

	public BossKind kind() {
		return kind;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_ATTACK, NONE);
		builder.define(DATA_TIER, 0);
	}

	/** Scale to the settings and the escalation tier; called once when the encounter begins. */
	public void prepare(int tier, long day) {
		entityData.set(DATA_TIER, tier);
		this.encounterDay = day;
		double scale = 1.0 + 0.5 * tier;
		getAttribute(Attributes.MAX_HEALTH).setBaseValue(baseHealth(kind) * ConfigManager.get().bosses.healthMultiplier * scale);
		getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(baseDamage(kind) * ConfigManager.get().bosses.damageMultiplier * scale);
		setHealth(getMaxHealth());
	}

	public int tier() {
		return entityData.get(DATA_TIER);
	}

	public long encounterDay() {
		return encounterDay;
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 24.0F));
		goalSelector.addGoal(9, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
		targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, NpcEntity.class, false));
	}

	// ======================================================================================== attacks

	/** The attack under way (one of the constants), 0 for none. Synced. */
	public byte attack() {
		return entityData.get(DATA_ATTACK);
	}

	/** Client: 0 to 1 through the current attack's animation. */
	public float attackProgress(float partialTick) {
		int length = attackLength(attack());
		return length <= 0 ? 0.0F : Mth.clamp((tickCount - attackStartTick + partialTick) / length, 0.0F, 1.0F);
	}

	private static int attackLength(byte attack) {
		return switch (attack) {
			case SWING -> 16;
			case SLAM -> 30;
			case THROW -> 24;
			case HOOK -> 26;
			case BASH -> 20;
			case CALL, ROAR, SUMMON -> 30;
			case LEAP -> 30;
			case SPIT -> 20;
			case BLINK -> 12;
			case POUND -> 26;
			default -> 0;
		};
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (DATA_ATTACK.equals(accessor)) {
			attackStartTick = tickCount;
		}
	}

	private void begin(byte attack, int impact, int cooldownSlot, int cooldown) {
		entityData.set(DATA_ATTACK, attack);
		attackLength = attackLength(attack);
		attackTicks = attackLength;
		impactAt = attackLength - impact;
		if (cooldownSlot >= 0) {
			cooldowns[cooldownSlot] = cooldown;
		}
		getNavigation().stop();
		if (attack != SWING) {
			playSound(sound(attack == ROAR || attack == CALL || attack == SUMMON ? "roar" : "attack"), 2.0F, 0.9F + random.nextFloat() * 0.2F);
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		bossBar.setProgress(getHealth() / getMaxHealth());
		for (int i = 0; i < cooldowns.length; i++) {
			if (cooldowns[i] > 0) {
				cooldowns[i]--;
			}
		}
		passives(level);
		LivingEntity target = getTarget();
		if (attackTicks > 0) {
			attackTicks--;
			if (target != null) {
				getLookControl().setLookAt(target, 30.0F, 30.0F);
			}
			if (attackTicks == impactAt) {
				impact(level, attack(), target);
			}
			if (attackTicks == 0) {
				entityData.set(DATA_ATTACK, NONE);
			}
			return;
		}
		if (target == null || !target.isAlive()) {
			return;
		}
		double distance = distanceTo(target);
		if (!chooseSpecial(level, target, distance)) {
			double reach = getBbWidth() * 0.9 + 1.4;
			if (distance <= reach && cooldowns[0] == 0) {
				begin(SWING, 8, 0, 18);
			} else {
				getNavigation().moveTo(target, 1.0);
				if (getNavigation().isDone()) {
					getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), 1.0);
				}
			}
		}
	}

	/** Pick a special attack if one is ready and fits; true if one began. */
	private boolean chooseSpecial(ServerLevel level, LivingEntity target, double distance) {
		boolean sees = hasLineOfSight(target);
		switch (kind) {
			case ABOMINATION -> {
				if (distance < 6.0 && cooldowns[1] == 0) {
					begin(SLAM, 12, 1, 160);
					return true;
				}
				if (distance < 3.8 && cooldowns[2] == 0) {
					begin(THROW, 12, 2, 200);
					return true;
				}
			}
			case BUTCHER -> {
				if (distance > 6.0 && distance < 18.0 && sees && cooldowns[1] == 0) {
					begin(HOOK, 14, 1, 140);
					return true;
				}
			}
			case WARDEN -> {
				if (cooldowns[3] == 0) {
					begin(CALL, 15, 3, 400);
					return true;
				}
				if (distance < 4.0 && cooldowns[1] == 0) {
					begin(BASH, 10, 1, 100);
					return true;
				}
				if (distance < 5.0 && cooldowns[2] == 0) {
					begin(POUND, 13, 2, 220);
					return true;
				}
			}
			case EXPERIMENT -> {
				if (distance > 5.0 && distance < 16.0 && cooldowns[1] == 0 && onGround()) {
					begin(LEAP, 24, 1, 120);
					return true;
				}
				if (distance > 4.0 && distance < 20.0 && sees && cooldowns[2] == 0) {
					begin(SPIT, 10, 2, 90);
					return true;
				}
			}
			case PATIENT_ZERO -> {
				boolean phaseTwo = getHealth() < getMaxHealth() * 0.5F;
				if (cooldowns[2] == 0) {
					begin(SUMMON, 15, 2, phaseTwo ? 300 : 500);
					return true;
				}
				if ((distance > 10.0 || cooldowns[1] == 0) && cooldowns[1] == 0) {
					begin(BLINK, 8, 1, 200);
					return true;
				}
			}
		}
		return false;
	}

	private void impact(ServerLevel level, byte attack, LivingEntity target) {
		double damage = getAttributeValue(Attributes.ATTACK_DAMAGE);
		switch (attack) {
			case SWING -> {
				if (target != null && distanceTo(target) <= getBbWidth() * 0.9 + 2.0 && doHurtTarget(level, target)) {
					afterHit(target);
				}
			}
			case SLAM, POUND -> {
				double radius = attack == SLAM ? 5.0 : 4.0;
				level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.2, getZ(), 6, radius * 0.4, 0.1, radius * 0.4, 0.0);
				playSound(DLSounds.VEHICLE_IMPACT.value(), 2.0F, 0.5F);
				for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(radius, 1.0, radius),
					entity -> entity != this && entity.isAlive() && !(entity instanceof InfectedEntity) && !(entity instanceof BossEntity))) {
					victim.hurtServer(level, damageSources().mobAttack(this), (float) (damage * (attack == SLAM ? 1.2 : 0.9)));
					Vec3 away = victim.position().subtract(position()).normalize();
					victim.push(away.x * 1.2, 0.7, away.z * 1.2);
					victim.hurtMarked = true;
				}
			}
			case THROW -> {
				if (target != null && distanceTo(target) < 4.5) {
					target.hurtServer(level, damageSources().mobAttack(this), (float) (damage * 0.8));
					Vec3 away = target.position().subtract(position()).normalize();
					target.push(away.x * 2.2, 1.0, away.z * 2.2);
					target.hurtMarked = true;
				}
			}
			case HOOK -> {
				if (target != null && hasLineOfSight(target)) {
					Vec3 pull = position().subtract(target.position()).normalize();
					target.push(pull.x * 1.8, 0.45, pull.z * 1.8);
					target.hurtMarked = true;
					target.hurtServer(level, damageSources().mobAttack(this), 4.0F);
					level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 1.0, target.getZ(), 10, 0.3, 0.3, 0.3, 0.1);
				}
			}
			case BASH -> {
				if (target != null && distanceTo(target) < 4.5) {
					target.hurtServer(level, damageSources().mobAttack(this), (float) damage);
					Vec3 away = target.position().subtract(position()).normalize();
					target.push(away.x * 1.6, 0.4, away.z * 1.6);
					target.hurtMarked = true;
					target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2));
				}
			}
			case CALL -> summon(level, InfectedKind.RUNNER, 3);
			case SUMMON -> summon(level, null, getHealth() < getMaxHealth() * 0.5F ? 6 : 4);
			case LEAP -> {
				if (target != null) {
					Vec3 jump = target.position().subtract(position());
					double flat = Math.sqrt(jump.x * jump.x + jump.z * jump.z);
					setDeltaMovement(jump.x / Math.max(1.0, flat) * Math.min(1.6, flat * 0.16), 0.75, jump.z / Math.max(1.0, flat) * Math.min(1.6, flat * 0.16));
					hurtMarked = true;
					cooldowns[4] = 20; // land check window
				}
			}
			case SPIT -> {
				if (target != null && hasLineOfSight(target)) {
					Vec3 from = getEyePosition();
					Vec3 to = target.getEyePosition();
					for (int i = 0; i <= 12; i++) {
						Vec3 at = from.lerp(to, i / 12.0);
						level.sendParticles(ParticleTypes.SPIT, at.x, at.y, at.z, 2, 0.05, 0.05, 0.05, 0.0);
					}
					target.hurtServer(level, damageSources().mobAttack(this), (float) (damage * 0.6));
					target.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1));
				}
			}
			case BLINK -> {
				if (target != null) {
					level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.0, getZ(), 20, 0.4, 0.8, 0.4, 0.02);
					for (int attempt = 0; attempt < 10; attempt++) {
						double angle = random.nextDouble() * Math.PI * 2.0;
						double r = 3.0 + random.nextDouble() * 2.0;
						if (randomTeleport(target.getX() + Math.cos(angle) * r, target.getY(), target.getZ() + Math.sin(angle) * r, true)) {
							break;
						}
					}
					level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.0, getZ(), 20, 0.4, 0.8, 0.4, 0.02);
				}
			}
			default -> {
			}
		}
	}

	/** What landing a blow does beyond damage. */
	private void afterHit(LivingEntity target) {
		if (kind == BossKind.BUTCHER && target instanceof ServerPlayer player) {
			SurvivalData body = SurvivalAttachments.get(player);
			body.setBleeding(Math.max(body.bleeding(), 1));
		}
		if (kind == BossKind.PATIENT_ZERO) {
			heal(4.0F);
		}
	}

	/** Shedding, enraging, regenerating, darkness: what goes on whatever the boss is doing. */
	private void passives(ServerLevel level) {
		float health = getHealth() / getMaxHealth();
		if (kind == BossKind.ABOMINATION) {
			for (int stage = 0; stage < 3; stage++) {
				float threshold = 0.75F - 0.25F * stage;
				if (health < threshold && (shed & (1 << stage)) == 0) {
					shed |= 1 << stage;
					summon(level, InfectedKind.CRAWLER, 3);
					playSound(sound("hurt"), 2.0F, 0.7F);
				}
			}
		}
		if (kind == BossKind.BUTCHER && !enraged && health < 0.5F) {
			enraged = true;
			getAttribute(Attributes.MOVEMENT_SPEED).addOrReplacePermanentModifier(new AttributeModifier(Deadlands.id("butcher_enraged"), 0.4,
				AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
			begin(ROAR, 15, -1, 0);
		}
		if (kind == BossKind.EXPERIMENT && tickCount % 20 == 0 && tickCount - lastHurtTick > 60 && !isOnFire() && health < 1.0F) {
			heal(1.5F);
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 1.5, getZ(), 2, 0.4, 0.6, 0.4, 0.0);
		}
		if (kind == BossKind.PATIENT_ZERO && tickCount % 200 == 0) {
			for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(24.0), p -> !p.isSpectator())) {
				player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0));
			}
		}
		if (kind == BossKind.EXPERIMENT && cooldowns[4] > 0 && onGround() && cooldowns[4] < 18) {
			// Landing from a leap hurts whoever it lands near.
			cooldowns[4] = 0;
			for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(2.0),
				entity -> entity != this && entity.isAlive() && !(entity instanceof InfectedEntity))) {
				victim.hurtServer(level, damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE));
			}
		}
	}

	private void summon(ServerLevel level, InfectedKind fixed, int count) {
		InfectedKind[] common = {InfectedKind.WALKER, InfectedKind.RUNNER, InfectedKind.CRAWLER, InfectedKind.SCREAMER};
		for (int i = 0; i < count; i++) {
			BlockPos spot = SpawnFinder.around(level, blockPosition(), 2.0, 6.0, random, false);
			if (spot != null) {
				InfectedEntity infected = SpawnFinder.spawn(level, fixed != null ? fixed : common[random.nextInt(common.length)], spot,
					EntitySpawnReason.MOB_SUMMONED, null);
				if (infected != null && getTarget() != null) {
					infected.setTarget(getTarget());
				}
			}
		}
		level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.5, getZ(), 30, 2.0, 0.5, 2.0, 0.02);
	}

	// ======================================================================================== damage, death

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (kind == BossKind.WARDEN && source.getEntity() != null && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR)) {
			// The shield turns most of a blow that comes from in front.
			Vec3 toAttacker = source.getEntity().position().subtract(position()).normalize();
			Vec3 facing = Vec3.directionFromRotation(0.0F, getYRot());
			if (toAttacker.x * facing.x + toAttacker.z * facing.z > 0.5) {
				amount *= 0.3F;
				playSound(net.minecraft.sounds.SoundEvents.SHIELD_BLOCK.value(), 1.0F, 0.8F);
			}
		}
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt) {
			lastHurtTick = tickCount;
			level.sendParticles(com.deadlands.registry.DLParticles.BLOOD, getX(), getY() + getBbHeight() * 0.6, getZ(), 10, getBbWidth() * 0.3, getBbHeight() * 0.2, getBbWidth() * 0.3, 0.1);
		}
		return hurt;
	}

	@Override
	public void die(DamageSource source) {
		boolean first = !dead;
		super.die(source);
		if (first && level() instanceof ServerLevel level) {
			BossDirector.onDefeated(level, this, source.getEntity() instanceof ServerPlayer player ? player : null);
		}
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
		super.dropCustomDeathLoot(level, source, recentlyHit);
		spawnAtLocation(level, new ItemStack(DLStory.trophies().get(kind.ordinal())));
		for (LoreEntry entry : Lore.all()) {
			if (entry.places().contains("boss_" + kind.id())) {
				spawnAtLocation(level, Lore.document(entry, java.util.List.of()));
			}
		}
		spawnAtLocation(level, new ItemStack(DLWeapons.PISTOL_AMMO, 10 + random.nextInt(11)));
		spawnAtLocation(level, new ItemStack(DLSurvivalItems.MEDKIT));
		if (kind == BossKind.PATIENT_ZERO || random.nextFloat() < 0.3F) {
			spawnAtLocation(level, new ItemStack(DLSurvivalItems.ANTIVIRAL));
		}
	}

	// ======================================================================================== bar, sounds, saving

	@Override
	public void startSeenByPlayer(ServerPlayer player) {
		super.startSeenByPlayer(player);
		bossBar.addPlayer(player);
	}

	@Override
	public void stopSeenByPlayer(ServerPlayer player) {
		super.stopSeenByPlayer(player);
		bossBar.removePlayer(player);
	}

	@Override
	public void setCustomName(Component name) {
		super.setCustomName(name);
		bossBar.setName(getDisplayName());
	}

	private SoundEvent sound(String what) {
		return DLSounds.boss(kind, what);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return sound("roar");
	}

	@Override
	public int getAmbientSoundInterval() {
		return 240;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return sound("hurt");
	}

	@Override
	protected SoundEvent getDeathSound() {
		return sound("death");
	}

	@Override
	protected float getSoundVolume() {
		return 2.5F;
	}

	@Override
	public SoundSource getSoundSource() {
		return SoundSource.HOSTILE;
	}

	@Override
	public boolean removeWhenFarAway(double distanceSqr) {
		return false;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("Tier", tier());
		output.putLong("EncounterDay", encounterDay);
		output.putInt("Shed", shed);
		output.putBoolean("Enraged", enraged);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		entityData.set(DATA_TIER, input.getIntOr("Tier", 0));
		encounterDay = input.getLongOr("EncounterDay", 0L);
		shed = input.getIntOr("Shed", 0);
		enraged = input.getBooleanOr("Enraged", false);
		if (hasCustomName()) {
			bossBar.setName(getDisplayName());
		}
	}
}
