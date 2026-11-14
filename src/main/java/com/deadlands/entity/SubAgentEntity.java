package com.deadlands.entity;

import com.deadlands.config.ConfigManager;
import com.deadlands.config.DeadlandsConfig;
import com.deadlands.registry.DLParticles;
import com.deadlands.registry.DLSounds;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * A sub-agent: one of Mythos' minions, a small, fast, fragile skitterer of corrupted code. They come in threes and
 * fours ({@code MythosEntity}'s Spawn Sub-agents), never more than {@code mythos.maxSubAgents} at once, swarm the
 * nearest survivor and bite. Two or three hits each with a sword. Without their Mythos near they break apart.
 */
public class SubAgentEntity extends Monster {
	private static final EntityDataAccessor<Integer> DATA_ATTACKS = SynchedEntityData.defineId(SubAgentEntity.class, EntityDataSerializers.INT);
	/** Client: the tick its last bite began (for the attack animation). */
	private int attackStart = -100;
	private @Nullable UUID owner;
	private int orphanTicks;

	public SubAgentEntity(EntityType<? extends SubAgentEntity> type, Level level) {
		super(type, level);
		xpReward = 3;
		if (!level.isClientSide()) {
			DeadlandsConfig.Mythos config = ConfigManager.get().mythos;
			getAttribute(Attributes.MAX_HEALTH).setBaseValue(config.subAgentHealth);
			getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(config.subAgentDamage);
			setHealth(getMaxHealth());
		}
	}

	public static AttributeSupplier.Builder createAttributes() {
		DeadlandsConfig.Mythos config = ConfigManager.get().mythos;
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, config.subAgentHealth)
			.add(Attributes.ATTACK_DAMAGE, config.subAgentDamage)
			.add(Attributes.MOVEMENT_SPEED, 0.36)
			.add(Attributes.FOLLOW_RANGE, 32.0)
			.add(Attributes.STEP_HEIGHT, 1.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_ATTACKS, 0);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);
		if (DATA_ATTACKS.equals(key)) {
			attackStart = tickCount;
		}
	}

	/** Client: ticks since its last bite began. */
	public float attackAge(float partialTick) {
		return tickCount - attackStart + partialTick;
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.25, true));
		goalSelector.addGoal(5, new RandomStrollGoal(this, 0.8));
		targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	/** Whose minion it is. */
	public void setOwner(MythosEntity mythos) {
		owner = mythos.getUUID();
	}

	public boolean ownedBy(MythosEntity mythos) {
		return mythos.getUUID().equals(owner);
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		entityData.set(DATA_ATTACKS, entityData.get(DATA_ATTACKS) + 1);
		boolean hit = super.doHurtTarget(level, target);
		if (hit) {
			level.playSound(null, getX(), getY(), getZ(), DLSounds.SUB_AGENT_HIT.value(), SoundSource.HOSTILE, 1.0F, 0.9F + random.nextFloat() * 0.25F);
		}
		return hit;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (tickCount % 6 == 0) {
			level.sendParticles(DLParticles.GLITCH, getX(), getY() + 0.4, getZ(), 1, 0.25, 0.15, 0.25, 0.01);
		}
		// Without its Mythos (dead, gone, or never had one) it breaks apart after a few seconds.
		if (tickCount % 20 == 0) {
			Entity mythos = owner == null ? null : level.getEntity(owner);
			orphanTicks = mythos != null && mythos.isAlive() && mythos.distanceTo(this) < 96.0 ? 0 : orphanTicks + 20;
			if (orphanTicks > 100 && owner != null) {
				breakApart(level);
			}
		}
	}

	/** Gone in a burst of corruption (no loot). */
	public void breakApart(ServerLevel level) {
		level.sendParticles(DLParticles.GLITCH, getX(), getY() + 0.3, getZ(), 14, 0.3, 0.2, 0.3, 0.06);
		level.playSound(null, getX(), getY(), getZ(), DLSounds.SUB_AGENT_DEATH.value(), SoundSource.HOSTILE, 0.8F, 1.2F);
		discard();
	}

	/** Dies over 15 ticks (the death animation: it flips and curls up) rather than Minecraft's 20-tick fall. */
	@Override
	protected void tickDeath() {
		deathTime++;
		if (deathTime >= 15 && level() instanceof ServerLevel level && !isRemoved()) {
			level.sendParticles(DLParticles.GLITCH, getX(), getY() + 0.3, getZ(), 10, 0.3, 0.2, 0.3, 0.05);
			remove(RemovalReason.KILLED);
		}
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return DLSounds.SUB_AGENT_IDLE.value();
	}

	@Override
	public int getAmbientSoundInterval() {
		return 120;
	}

	@Override
	protected @Nullable SoundEvent getHurtSound(DamageSource source) {
		return DLSounds.SUB_AGENT_HIT.value();
	}

	@Override
	protected @Nullable SoundEvent getDeathSound() {
		return DLSounds.SUB_AGENT_DEATH.value();
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		playSound(DLSounds.SUB_AGENT_STEP.value(), 0.35F, 1.0F + random.nextFloat() * 0.2F);
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return owner == null;
	}

	@Override
	protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput output) {
		super.addAdditionalSaveData(output);
		if (owner != null) {
			output.putString("Owner", owner.toString());
		}
	}

	@Override
	protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput input) {
		super.readAdditionalSaveData(input);
		String id = input.getStringOr("Owner", "");
		try {
			owner = id.isEmpty() ? null : UUID.fromString(id);
		} catch (IllegalArgumentException e) {
			owner = null;
		}
	}
}
