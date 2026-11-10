package com.deadlands.event;

import com.deadlands.Deadlands;
import com.deadlands.container.LootContainerBlockEntity;
import com.deadlands.noise.Loudness;
import com.deadlands.noise.NoiseSystem;
import com.deadlands.registry.DLContainers;
import com.deadlands.registry.DLParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * A supply crate coming down under a parachute. It sways as it falls, slowly; on the ground it becomes
 * a military crate full of supplies ({@code deadlands:events/supply_drop}), puts up a smoke flare, and
 * the landing is loud enough to bring every infected for a long way around.
 */
public class SupplyDropEntity extends Entity {
	private static final double FALL_SPEED = 0.16;

	public SupplyDropEntity(EntityType<? extends SupplyDropEntity> type, Level level) {
		super(type, level);
		this.noPhysics = false;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	public void tick() {
		super.tick();
		double sway = Math.sin(tickCount * 0.05) * 0.03;
		setDeltaMovement(sway, -FALL_SPEED, Math.cos(tickCount * 0.04) * 0.03);
		move(MoverType.SELF, getDeltaMovement());
		if (level() instanceof ServerLevel level) {
			if (onGround() || isInWater()) {
				land(level);
			} else if (tickCount > 20 * 120) {
				discard(); // it has drifted somewhere it will never land
			}
		}
	}

	private void land(ServerLevel level) {
		BlockPos pos = blockPosition();
		if (!level.getBlockState(pos).canBeReplaced()) {
			pos = pos.above();
		}
		if (level.getBlockState(pos).canBeReplaced()) {
			level.setBlock(pos, DLContainers.MILITARY_CRATE.defaultBlockState(), Block.UPDATE_ALL);
			if (level.getBlockEntity(pos) instanceof LootContainerBlockEntity crate) {
				crate.setLootTable(ResourceKey.create(Registries.LOOT_TABLE, Deadlands.id("events/supply_drop")), random.nextLong());
			}
		}
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 1.2F, 0.6F);
		level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, getX(), getY() + 1.0, getZ(), 20, 0.3, 0.5, 0.3, 0.02);
		level.sendParticles(DLParticles.EMBER, getX(), getY() + 1.0, getZ(), 12, 0.3, 0.3, 0.3, 0.05);
		NoiseSystem.emit(level, position(), Loudness.LOUD, null, false);
		discard();
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 256.0 * 256.0;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	/** Where it is heading (for the announcement). */
	public Vec3 landing() {
		return new Vec3(getX(), getY(), getZ());
	}
}
