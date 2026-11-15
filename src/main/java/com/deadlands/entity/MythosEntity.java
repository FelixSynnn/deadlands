package com.deadlands.entity;

import com.deadlands.config.ConfigManager;
import com.deadlands.config.DeadlandsConfig;
import com.deadlands.cutscene.Cutscenes;
import com.deadlands.registry.DLMythos;
import com.deadlands.registry.DLParticles;
import com.deadlands.registry.DLSounds;
import com.deadlands.story.Lore;
import com.deadlands.story.LoreEntry;
import com.deadlands.world.disaster.MythosTornado;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
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
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Mythos ("Myth"): a rogue virus out of Kessler Regional's predictive AI of the same name, built a body out of the
 * storm that carries it (lore option A: {@code lore.deadlands.mythos_file}). A boss: tough but very winnable. He comes
 * down with his tornado ({@code world.disaster.MythosTornado}) or from a spawn egg.
 *
 * <p>Phases: 1 above half health; 2 from half (a roar that throws survivors back, no damage; Mass Infection and
 * Rejuvenate join in; four sub-agents a call instead of three); 3 from a fifth (enraged: cooldowns 30% shorter, 10%
 * faster, Fragment Blink, no more healing). He is always slower than a sprint: you can run.
 *
 * <ul>
 *   <li><b>Corrupted Slash</b>: a 2.5-block arc of the claws for {@code mythos.damage}, after a three-quarter-second
 *       windup you can see (his arm goes up).</li>
 *   <li><b>Entanglement</b>: a code-tether thrown at you; dodgeable. A hit: 4 damage, Slowness IV for 2 s, pulled 3 blocks
 *       toward him.</li>
 *   <li><b>Spawn Sub-agents</b>: 3 (4 from phase 2), never more than {@code mythos.maxSubAgents} alive.</li>
 *   <li><b>Mass Infection</b> (phase 2+): five corrupted packets lobbed in a spread; each leaves a 4-second puddle of
 *       infected data (2 damage a second and nausea).</li>
 *   <li><b>Rejuvenate</b> (phase 2): a 3-second channel that heals 8% of his health; three hits break it.</li>
 *   <li><b>Fragment Blink</b> (phase 3): an afterimage, and he is 6 to 10 blocks behind you, slashing.</li>
 * </ul>
 * A purple boss bar while he is near; his own music by his terror radius ({@code audio.mythosTerrorRadius}). Killed, he
 * plays his death (2.5 s), the defeat cutscene runs, the storm breaks up, and he leaves the Haxxed Blade, 300 XP and his
 * lore file.
 */
public class MythosEntity extends Monster {
	public enum Action {
		NONE, SLASH, ENTANGLE, MASS_INFECTION, SUMMON, REJUVENATE, BLINK, PHASE, ENTRANCE // append only
	}

	private static final EntityDataAccessor<Integer> DATA_ACTION = SynchedEntityData.defineId(MythosEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(MythosEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_PREY = SynchedEntityData.defineId(MythosEntity.class, EntityDataSerializers.INT);
	/** Ticks of the death animation (he dies over this long). */
	public static final int DEATH_TICKS = 50;
	private static final DustParticleOptions MAGENTA = new DustParticleOptions(0xFF4FD6, 1.6F);
	private static final DustParticleOptions VIOLET = new DustParticleOptions(0x5A1AA8, 2.0F);

	private final ServerBossEvent bossBar = new ServerBossEvent(Component.translatable("boss.deadlands.mythos"), BossEvent.BossBarColor.PURPLE,
		BossEvent.BossBarOverlay.NOTCHED_10);
	private int actionLeft;
	private int actionAge;
	private int actionStartTick;
	private int entranceLeft;
	private int slashCd = 20;
	private int entangleCd = 60;
	private int summonCd = 100;
	private int massCd = 60;
	private int rejuvenateCd = 200;
	private int blinkCd = 60;
	private int rejuvenateHits;
	private boolean slashed;
	private final List<Shot> shots = new ArrayList<>();
	private final List<Puddle> puddles = new ArrayList<>();
	/** Client: eased 0 (walking) to 1 (chasing). */
	private float chase;
	private float chaseO;
	/** Client: through a stride, shared by the walk and the chase. */
	private float walkPhase;
	private float walkPhaseO;

	/** A thrown tether (entangle) or a lobbed packet (mass infection), moved and drawn on the server. */
	private static final class Shot {
		Vec3 pos;
		Vec3 velocity;
		final boolean packet;
		int life;

		Shot(Vec3 pos, Vec3 velocity, boolean packet, int life) {
			this.pos = pos;
			this.velocity = velocity;
			this.packet = packet;
			this.life = life;
		}
	}

	/** A puddle of infected data. */
	private record Puddle(Vec3 at, int until) {
	}

	public MythosEntity(EntityType<? extends MythosEntity> type, Level level) {
		super(type, level);
		xpReward = 300;
		if (!level.isClientSide()) {
			DeadlandsConfig.Mythos config = ConfigManager.get().mythos;
			getAttribute(Attributes.MAX_HEALTH).setBaseValue(config.health);
			getAttribute(Attributes.ARMOR).setBaseValue(config.armor);
			getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(config.damage);
			getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(config.walkSpeed);
			setHealth(getMaxHealth());
		}
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		DeadlandsConfig.Mythos config = ConfigManager.get().mythos;
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, config.health)
			.add(Attributes.ARMOR, config.armor)
			.add(Attributes.ATTACK_DAMAGE, config.damage)
			.add(Attributes.MOVEMENT_SPEED, config.walkSpeed)
			.add(Attributes.FOLLOW_RANGE, 64.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
			.add(Attributes.STEP_HEIGHT, 1.5);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_ACTION, 0);
		builder.define(DATA_PHASE, 1);
		builder.define(DATA_PREY, -1);
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

	public Action action() {
		return Action.values()[Mth.clamp(entityData.get(DATA_ACTION), 0, Action.values().length - 1)];
	}

	public float actionAge(float partialTick) {
		return tickCount - actionStartTick + partialTick;
	}

	public int phase() {
		return entityData.get(DATA_PHASE);
	}

	/** The id of the survivor he is fighting, or -1. */
	public int preyId() {
		return entityData.get(DATA_PREY);
	}

	public float chase(float partialTick) {
		return Mth.lerp(partialTick, chaseO, chase);
	}

	public float walkPhase(float partialTick) {
		return Mth.lerp(partialTick, walkPhaseO, walkPhase);
	}

	// ======================================================================================== starting

	/** Arriving with the tornado: he unfolds and roars (3 s), and doesn't act or take harm for 5 s. */
	public void arrive() {
		entranceLeft = 100;
		act(Action.ENTRANCE, 60);
	}

	private void act(Action action, int ticks) {
		entityData.set(DATA_ACTION, action.ordinal());
		actionLeft = ticks;
		actionAge = 0;
		slashed = false;
	}

	// ======================================================================================== the fight

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		bossBar.setProgress(Mth.clamp(getHealth() / getMaxHealth(), 0.0F, 1.0F));
		tickShots(level);
		tickPuddles(level);
		if (tickCount % 4 == 0) {
			int phase = phase();
			level.sendParticles(DLParticles.GLITCH, getX(), getY() + 2.0, getZ(), phase == 3 ? 4 : 2, 0.6, 1.4, 0.6, 0.02);
		}
		if (entranceLeft > 0) {
			entranceLeft--;
			tickAction(level, null);
			getNavigation().stop();
			return;
		}
		slashCd = Math.max(0, slashCd - 1);
		entangleCd = Math.max(0, entangleCd - 1);
		summonCd = Math.max(0, summonCd - 1);
		massCd = Math.max(0, massCd - 1);
		rejuvenateCd = Math.max(0, rejuvenateCd - 1);
		blinkCd = Math.max(0, blinkCd - 1);

		Player target = target(level);
		entityData.set(DATA_PREY, target == null ? -1 : target.getId());
		// Phases.
		float health = getHealth() / getMaxHealth();
		if (phase() == 1 && health <= 0.5F || phase() == 2 && health <= 0.2F) {
			enterPhase(level, phase() + 1);
			return;
		}
		if (actionLeft > 0) {
			tickAction(level, target);
			return;
		}
		if (target == null) {
			getNavigation().stop();
			return;
		}
		double distance = distanceTo(target);
		getLookControl().setLookAt(target, 30.0F, 30.0F);
		boolean sees = hasLineOfSight(target);
		DeadlandsConfig.Mythos config = ConfigManager.get().mythos;
		int phase = phase();
		if (phase == 3 && blinkCd == 0 && distance > 5.0 && distance < 24.0) {
			act(Action.BLINK, 10);
			blinkCd = cooldown(config.blinkCooldown);
		} else if (phase == 2 && rejuvenateCd == 0 && health < 0.45F) {
			act(Action.REJUVENATE, 60);
			rejuvenateHits = 0;
			rejuvenateCd = cooldown(config.rejuvenateCooldown);
			level.playSound(null, getX(), getY(), getZ(), DLSounds.MYTHOS_REJUVENATE.value(), SoundSource.HOSTILE, 2.0F, 1.0F);
		} else if (summonCd == 0 && minions(level) < config.maxSubAgents) {
			act(Action.SUMMON, 25);
			summonCd = cooldown(config.summonCooldown);
		} else if (phase >= 2 && massCd == 0 && sees && distance > 5.0 && distance < 18.0) {
			act(Action.MASS_INFECTION, 20);
			massCd = cooldown(config.massInfectionCooldown);
			level.playSound(null, getX(), getY(), getZ(), DLSounds.MYTHOS_MASS_INFECTION.value(), SoundSource.HOSTILE, 2.2F, 1.0F);
			for (ServerPlayer far : level.players()) {
				if (far.distanceTo(this) > 32.0 && far.distanceTo(this) < 128.0) {
					far.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(DLSounds.MYTHOS_MASS_INFECTION_FAR, SoundSource.HOSTILE,
						far.getX(), far.getY(), far.getZ(), 1.0F, 1.0F, random.nextLong())); // heard only by those far off
				}
			}
		} else if (entangleCd == 0 && sees && distance > 5.0 && distance < 16.0) {
			act(Action.ENTANGLE, 15);
			entangleCd = cooldown(config.entangleCooldown);
		} else if (slashCd == 0 && distance < 4.0) {
			act(Action.SLASH, 18);
			slashCd = cooldown(config.slashCooldown);
			level.playSound(null, getX(), getY(), getZ(), DLSounds.MYTHOS_SLASH.value(), SoundSource.HOSTILE, 1.6F, 1.0F);
		}
		if (actionLeft > 0) {
			getNavigation().stop();
			return;
		}
		// After them: always slower than a sprint (a tenth faster when enraged).
		double speed = config.chaseSpeed * (phase == 3 ? 1.1 : 1.0);
		getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(speed);
		if (distance > 2.5 && ((tickCount + getId()) % 5 == 0 || getNavigation().isDone())) {
			getNavigation().moveTo(target, 1.0);
		}
	}

	private int cooldown(double seconds) {
		return (int) Math.round(seconds * 20.0 * (phase() == 3 ? 0.7 : 1.0));
	}

	private @Nullable Player target(ServerLevel level) {
		Player best = null;
		double bestDistance = 64.0 * 64.0;
		for (ServerPlayer player : level.players()) {
			if (player.isCreative() || player.isSpectator() || !player.isAlive()) {
				continue;
			}
			double d = player.distanceToSqr(this);
			if (player.getId() == preyId()) {
				d *= 0.5; // he keeps to the one he is fighting
			}
			if (d < bestDistance) {
				bestDistance = d;
				best = player;
			}
		}
		return best;
	}

	private void tickAction(ServerLevel level, @Nullable Player target) {
		if (actionLeft <= 0) {
			return;
		}
		actionAge++;
		Action action = action();
		switch (action) {
			case SLASH -> {
				if (target != null && actionAge < 9) {
					getLookControl().setLookAt(target, 40.0F, 40.0F);
				}
				if (actionAge == 9 && !slashed) {
					slashed = true;
					slash(level);
				}
			}
			case ENTANGLE -> {
				if (target != null) {
					getLookControl().setLookAt(target, 40.0F, 40.0F);
				}
				if (actionAge == 8 && target != null) {
					Vec3 from = position().add(0.0, 2.8, 0.0);
					Vec3 dir = target.getEyePosition().subtract(from).normalize();
					shots.add(new Shot(from, dir.scale(1.3), false, 16));
					level.playSound(null, getX(), getY(), getZ(), DLSounds.MYTHOS_ENTANGLE.value(), SoundSource.HOSTILE, 1.8F, 1.0F);
				}
			}
			case MASS_INFECTION -> {
				if (actionAge == 12 && target != null) {
					Vec3 from = position().add(0.0, 3.0, 0.0);
					Vec3 to = target.position();
					for (int i = -2; i <= 2; i++) {
						Vec3 aim = to.subtract(position()).yRot(i * 0.3F).add(position());
						double flat = Math.sqrt(aim.subtract(from).x * aim.subtract(from).x + aim.subtract(from).z * aim.subtract(from).z);
						double time = Math.max(10.0, flat / 0.7);
						Vec3 v = new Vec3((aim.x - from.x) / time, (aim.y - from.y) / time + 0.03 * time, (aim.z - from.z) / time);
						shots.add(new Shot(from, v, true, 80));
					}
					level.playSound(null, getX(), getY(), getZ(), DLSounds.MYTHOS_PACKET.value(), SoundSource.HOSTILE, 1.6F, 1.0F);
				}
			}
			case SUMMON -> {
				if (actionAge == 15) {
					summon(level);
				}
			}
			case REJUVENATE -> {
				getNavigation().stop();
				if (actionAge % 5 == 0) {
					level.sendParticles(MAGENTA, getX(), getY() + 2.0, getZ(), 6, 0.8, 1.2, 0.8, 0.02);
				}
				if (actionAge >= 59) {
					heal(getMaxHealth() * 0.08F);
					level.sendParticles(DLParticles.GLITCH, getX(), getY() + 2.0, getZ(), 30, 0.8, 1.2, 0.8, 0.08);
				}
			}
			case BLINK -> {
				if (actionAge == 3 && target != null) {
					blink(level, target);
				}
			}
			case PHASE -> {
				if (actionAge == 5) {
					for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(7.0))) {
						Vec3 away = player.position().subtract(position()).multiply(1.0, 0.0, 1.0).normalize();
						player.push(away.x * 1.4, 0.45, away.z * 1.4);
						player.hurtMarked = true;
					}
					level.sendParticles(DLParticles.GLITCH, getX(), getY() + 2.0, getZ(), 60, 2.0, 1.5, 2.0, 0.2);
				}
			}
			case ENTRANCE -> {
				if (actionAge == 45) {
					level.playSound(null, getX(), getY(), getZ(), DLSounds.MYTHOS_PHASE.value(), SoundSource.HOSTILE, 3.0F, 0.85F);
				}
			}
			case NONE -> {
			}
		}
		if (--actionLeft == 0) {
			entityData.set(DATA_ACTION, Action.NONE.ordinal());
			if (action == Action.BLINK) {
				slashCd = 0; // straight into a slash from behind
			}
		}
	}

	private void slash(ServerLevel level) {
		Vec3 facing = Vec3.directionFromRotation(0.0F, getYRot());
		float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(3.5, 1.0, 3.5), e -> e != this && e.isAlive()
			&& !(e instanceof SubAgentEntity))) {
			Vec3 to = victim.position().subtract(position()).multiply(1.0, 0.0, 1.0);
			if (to.length() > 3.6 || to.length() > 0.5 && to.normalize().dot(facing) < 0.25) {
				continue; // outside the arc in front of him
			}
			if (victim instanceof Player player && (player.isCreative() || player.isSpectator())) {
				continue;
			}
			if (victim.hurtServer(level, damageSources().mobAttack(this), damage)) {
				level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), DLSounds.MYTHOS_SLASH_HIT.value(), SoundSource.HOSTILE, 1.4F, 1.0F);
				if (!victim.isAlive() && victim instanceof Player) {
					level.playSound(null, getX(), getY(), getZ(), DLSounds.MYTHOS_KILL.value(), SoundSource.HOSTILE, 2.5F, 1.0F);
				}
			}
		}
	}

	private int minions(ServerLevel level) {
		return level.getEntitiesOfClass(SubAgentEntity.class, getBoundingBox().inflate(96.0), a -> a.isAlive() && a.ownedBy(this)).size();
	}

	private void summon(ServerLevel level) {
		int room = ConfigManager.get().mythos.maxSubAgents - minions(level);
		int count = Math.min(room, phase() >= 2 ? 4 : 3);
		level.playSound(null, getX(), getY(), getZ(), DLSounds.MYTHOS_SUMMON.value(), SoundSource.HOSTILE, 2.0F, 1.0F);
		for (int i = 0; i < count; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			BlockPos probe = BlockPos.containing(getX() + Math.cos(angle) * 2.5, getY(), getZ() + Math.sin(angle) * 2.5);
			BlockPos at = com.deadlands.entity.ai.Stalking.standable(level, probe, 1);
			if (at == null) {
				continue;
			}
			SubAgentEntity agent = DLMythos.SUB_AGENT.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (agent == null) {
				return;
			}
			agent.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
			agent.setOwner(this);
			level.addFreshEntity(agent);
			level.sendParticles(DLParticles.GLITCH, agent.getX(), agent.getY() + 0.3, agent.getZ(), 12, 0.3, 0.3, 0.3, 0.05);
		}
	}

	private void blink(ServerLevel level, Player target) {
		Vec3 behind = target.getViewVector(1.0F).multiply(-1.0, 0.0, -1.0);
		behind = behind.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : behind.normalize();
		for (int attempt = 0; attempt < 8; attempt++) {
			double r = 6.0 + random.nextDouble() * 4.0;
			Vec3 dir = behind.yRot((float) ((random.nextDouble() - 0.5) * 1.2));
			BlockPos at = com.deadlands.entity.ai.Stalking.standable(level, BlockPos.containing(target.position().add(dir.scale(r))), 5);
			if (at == null) {
				continue;
			}
			level.sendParticles(DLParticles.GLITCH, getX(), getY() + 2.0, getZ(), 40, 0.7, 1.6, 0.7, 0.05);
			level.sendParticles(VIOLET, getX(), getY() + 2.0, getZ(), 20, 0.6, 1.6, 0.6, 0.0); // the afterimage
			level.playSound(null, getX(), getY(), getZ(), DLSounds.MYTHOS_BLINK.value(), SoundSource.HOSTILE, 2.0F, 1.0F);
			teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
			getLookControl().setLookAt(target, 180.0F, 180.0F);
			level.sendParticles(DLParticles.GLITCH, getX(), getY() + 2.0, getZ(), 40, 0.7, 1.6, 0.7, 0.05);
			return;
		}
	}

	private void enterPhase(ServerLevel level, int phase) {
		entityData.set(DATA_PHASE, phase);
		act(Action.PHASE, 20);
		bossBar.setColor(phase == 3 ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.PINK);
		level.playSound(null, getX(), getY(), getZ(), DLSounds.MYTHOS_PHASE.value(), SoundSource.HOSTILE, 3.0F, phase == 3 ? 0.75F : 0.9F);
		shots.clear();
	}

	// ---------------------------------------------------------------------------------------- tethers, packets, puddles

	private void tickShots(ServerLevel level) {
		Iterator<Shot> it = shots.iterator();
		while (it.hasNext()) {
			Shot shot = it.next();
			Vec3 next = shot.pos.add(shot.velocity);
			if (shot.packet) {
				shot.velocity = shot.velocity.add(0.0, -0.06, 0.0);
			}
			level.sendParticles(shot.packet ? VIOLET : MAGENTA, next.x, next.y, next.z, 3, 0.08, 0.08, 0.08, 0.0);
			level.sendParticles(DLParticles.GLITCH, next.x, next.y, next.z, 1, 0.1, 0.1, 0.1, 0.0);
			boolean done = --shot.life <= 0;
			if (!shot.packet) {
				for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, new AABB(next, next).inflate(1.2))) {
					if (!player.isCreative() && !player.isSpectator()) {
						entangle(level, player);
						done = true;
						break;
					}
				}
			} else {
				BlockState at = level.getBlockState(BlockPos.containing(next));
				if (!at.getCollisionShape(level, BlockPos.containing(next)).isEmpty() || next.y < level.getMinY()) {
					puddles.add(new Puddle(new Vec3(next.x, Math.floor(next.y) + 1.0, next.z), tickCount + 80));
					level.playSound(null, next.x, next.y, next.z, DLSounds.MYTHOS_PUDDLE.value(), SoundSource.HOSTILE, 1.2F, 1.0F);
					done = true;
				}
			}
			if (!level.getBlockState(BlockPos.containing(next)).getCollisionShape(level, BlockPos.containing(next)).isEmpty() && !shot.packet) {
				done = true; // the tether hits a wall
			}
			shot.pos = next;
			if (done) {
				it.remove();
			}
		}
	}

	private void entangle(ServerLevel level, ServerPlayer player) {
		player.hurtServer(level, damageSources().indirectMagic(this, this), 4.0F);
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 3, false, true));
		Vec3 pull = position().subtract(player.position()).multiply(1.0, 0.0, 1.0).normalize().scale(0.9);
		player.push(pull.x, 0.25, pull.z);
		player.hurtMarked = true;
		level.playSound(null, player.getX(), player.getY(), player.getZ(), DLSounds.MYTHOS_ENTANGLE_HIT.value(), SoundSource.HOSTILE, 1.6F, 1.0F);
	}

	private void tickPuddles(ServerLevel level) {
		Iterator<Puddle> it = puddles.iterator();
		while (it.hasNext()) {
			Puddle puddle = it.next();
			if (tickCount >= puddle.until()) {
				it.remove();
				continue;
			}
			if (tickCount % 4 == 0) {
				level.sendParticles(VIOLET, puddle.at().x, puddle.at().y + 0.1, puddle.at().z, 4, 0.9, 0.05, 0.9, 0.0);
				level.sendParticles(DLParticles.GLITCH, puddle.at().x, puddle.at().y + 0.2, puddle.at().z, 2, 0.8, 0.1, 0.8, 0.01);
			}
			if (tickCount % 10 == 0) {
				for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, new AABB(puddle.at(), puddle.at()).inflate(1.8, 1.0, 1.8))) {
					if (!player.isCreative() && !player.isSpectator()) {
						player.hurtServer(level, damageSources().indirectMagic(this, this), 1.0F);
						player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 80, 0, false, true));
					}
				}
			}
		}
	}

	// ---------------------------------------------------------------------------------------- harm and death

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (entranceLeft > 0 && !source.is(DamageTypes.FELL_OUT_OF_WORLD) && !source.is(DamageTypes.GENERIC_KILL)) {
			return false; // still coming down out of the storm
		}
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && action() == Action.REJUVENATE && source.getEntity() instanceof Player && ++rejuvenateHits >= 3) {
			actionLeft = 0; // broken
			entityData.set(DATA_ACTION, Action.NONE.ordinal());
			level.sendParticles(DLParticles.GLITCH, getX(), getY() + 2.0, getZ(), 25, 0.8, 1.2, 0.8, 0.1);
			level.playSound(null, getX(), getY(), getZ(), DLSounds.MYTHOS_HURT.value(), SoundSource.HOSTILE, 2.0F, 0.7F);
		}
		return hurt;
	}

	@Override
	public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
		return source.is(DamageTypes.FALL) || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.DROWN) || super.isInvulnerableTo(level, source);
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		shots.clear();
		puddles.clear();
		bossBar.removeAllPlayers();
		for (SubAgentEntity agent : level.getEntitiesOfClass(SubAgentEntity.class, getBoundingBox().inflate(96.0), a -> a.ownedBy(this))) {
			agent.breakApart(level);
		}
		MythosTornado.mythosGone(level, true);
		if (ConfigManager.get().mythos.cutscenes) {
			for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(48.0))) {
				Cutscenes.replay(player, "mythos_defeat", position(), getYRot());
			}
		}
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
		super.dropCustomDeathLoot(level, source, recentlyHit);
		spawnAtLocation(level, new ItemStack(DLMythos.HAXXED_BLADE));
		for (LoreEntry entry : Lore.all()) {
			if (entry.places().contains("boss_mythos")) {
				spawnAtLocation(level, Lore.document(entry, List.of()));
			}
		}
	}

	/** He dies over {@link #DEATH_TICKS} (the death animation: stagger, knees, slump, glitch, collapse). */
	@Override
	protected void tickDeath() {
		deathTime++;
		if (level() instanceof ServerLevel level) {
			if (deathTime % 3 == 0) {
				level.sendParticles(DLParticles.GLITCH, getX(), getY() + 1.5, getZ(), 6, 0.8, 1.0, 0.8, 0.04);
			}
			if (deathTime >= DEATH_TICKS && !isRemoved()) {
				level.sendParticles(DLParticles.GLITCH, getX(), getY() + 1.0, getZ(), 120, 1.2, 1.0, 1.2, 0.15);
				level.sendParticles(VIOLET, getX(), getY() + 1.0, getZ(), 40, 1.0, 0.8, 1.0, 0.0);
				remove(RemovalReason.KILLED);
			}
		}
	}

	/** Escaped, or his storm gone: he breaks up and leaves, no reward. */
	public void leave(ServerLevel level) {
		level.sendParticles(DLParticles.GLITCH, getX(), getY() + 2.0, getZ(), 80, 1.0, 2.0, 1.0, 0.1);
		level.playSound(null, getX(), getY(), getZ(), DLSounds.MYTHOS_BLINK.value(), SoundSource.HOSTILE, 2.0F, 0.6F);
		for (SubAgentEntity agent : level.getEntitiesOfClass(SubAgentEntity.class, getBoundingBox().inflate(96.0), a -> a.ownedBy(this))) {
			agent.breakApart(level);
		}
		bossBar.removeAllPlayers();
		discard();
	}

	// ======================================================================================== client blends, bar, sounds, saving

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			chaseO = chase;
			walkPhaseO = walkPhase;
			chase = Mth.approach(chase, preyId() >= 0 && walkAnimation.speed() > 0.2F ? 1.0F : 0.0F, 0.05F);
			walkPhase += walkAnimation.speed() / Mth.lerp(chase, 15.0F, 10.0F);
			if (walkPhase > 64.0F) {
				walkPhase -= 64.0F;
				walkPhaseO -= 64.0F;
			}
		}
	}

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
	protected @Nullable SoundEvent getAmbientSound() {
		return DLSounds.MYTHOS_IDLE.value();
	}

	@Override
	public int getAmbientSoundInterval() {
		return 140;
	}

	@Override
	protected @Nullable SoundEvent getHurtSound(DamageSource source) {
		return DLSounds.MYTHOS_HURT.value();
	}

	@Override
	protected @Nullable SoundEvent getDeathSound() {
		return DLSounds.MYTHOS_DEATH.value();
	}

	@Override
	protected float getSoundVolume() {
		return 2.0F;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		playSound(DLSounds.MYTHOS_STEP.value(), 1.0F, 0.9F + random.nextFloat() * 0.15F);
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("Phase", phase());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		entityData.set(DATA_PHASE, Mth.clamp(input.getIntOr("Phase", 1), 1, 3));
		bossBar.setColor(phase() == 3 ? BossEvent.BossBarColor.RED : phase() == 2 ? BossEvent.BossBarColor.PINK : BossEvent.BossBarColor.PURPLE);
	}
}
