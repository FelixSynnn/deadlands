package com.deadlands.nest;

import com.deadlands.config.ConfigManager;
import com.deadlands.persistence.ChronicleEntry;
import com.deadlands.persistence.DeadlandsWorldData;
import com.deadlands.progression.WorldClock;
import com.deadlands.registry.DLNests;
import com.deadlands.registry.DLSounds;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A nest core: a pulsing mound of biomass the infected shelter in. The state lives in
 * {@link NestBlockEntity}; this block draws its idle breathing and spores and reports its
 * destruction to the world chronicle.
 */
public class NestBlock extends BaseEntityBlock {
	public static final MapCodec<NestBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		NestKind.CODEC.fieldOf("kind").forGetter(NestBlock::kind),
		propertiesCodec()
	).apply(instance, NestBlock::new));
	private static final VoxelShape SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 13.0, 15.0);

	private final NestKind kind;

	public NestBlock(NestKind kind, Properties properties) {
		super(properties);
		this.kind = kind;
	}

	public NestKind kind() {
		return kind;
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new NestBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null : createTickerHelper(type, DLNests.NEST, NestBlockEntity::serverTick);
	}

	/** Slow breathing pulses and drifting spores (client only). */
	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(kind == NestKind.ADVANCED ? 2 : 4) == 0) {
			level.addParticle(new DustParticleOptions(kind == NestKind.ADVANCED ? 0x8C3A2E : 0x5A3E30, 0.9F),
				pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 0.7 + random.nextDouble() * 0.5, pos.getZ() + 0.2 + random.nextDouble() * 0.6,
				0.0, 0.01, 0.0);
		}
		if (random.nextInt(kind == NestKind.ADVANCED ? 3 : 6) == 0) {
			level.addParticle(com.deadlands.registry.DLParticles.SPORE, pos.getX() + random.nextDouble(), pos.getY() + 1.0 + random.nextDouble() * 0.5,
				pos.getZ() + random.nextDouble(), (random.nextDouble() - 0.5) * 0.02, 0.015, (random.nextDouble() - 0.5) * 0.02);
		}
		if (random.nextInt(60) == 0) {
			level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, DLSounds.NEST_PULSE.value(), SoundSource.HOSTILE,
				0.5F + random.nextFloat() * 0.3F, 0.85F + random.nextFloat() * 0.3F, false);
		}
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (level instanceof ServerLevel server && !player.isCreative()) {
			DeadlandsWorldData.get(server.getServer()).addChronicle(new ChronicleEntry(WorldClock.currentDay(server.getServer()),
				"chronicle.deadlands.nest_destroyed." + kind.id(), List.of(player.getName().getString())),
				ConfigManager.get().general.chronicleLength);
		}
		return super.playerWillDestroy(level, pos, state, player);
	}
}
