package com.deadlands.npc;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/** Finding somewhere a character can safely stand (Harley's camp, and anyone else placed for the story). */
public final class SafeSpots {
	private SafeSpots() {
	}

	/**
	 * The nearest safe spot to {@code origin}, searched ring by ring from {@code minRadius} to
	 * {@code maxRadius} blocks, preferring spots within ten blocks of the origin's height. Null if none.
	 */
	public static @Nullable BlockPos find(ServerLevel level, BlockPos origin, int minRadius, int maxRadius, RandomSource random) {
		for (int pass = 0; pass < 2; pass++) {
			int maxRise = pass == 0 ? 10 : 64;
			for (int r = minRadius; r <= maxRadius; r++) {
				List<BlockPos> ring = new ArrayList<>();
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
							continue;
						}
						int x = origin.getX() + dx;
						int z = origin.getZ() + dz;
						int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
						BlockPos pos = new BlockPos(x, y, z);
						if (Math.abs(y - origin.getY()) <= maxRise && safe(level, pos)) {
							ring.add(pos);
						}
					}
				}
				if (!ring.isEmpty()) {
					return ring.get(random.nextInt(ring.size()));
				}
			}
		}
		return null;
	}

	/** Solid footing, two blocks of room, no fluid, and nothing that burns or cuts within reach. */
	public static boolean safe(ServerLevel level, BlockPos feet) {
		BlockState ground = level.getBlockState(feet.below());
		if (!ground.isFaceSturdy(level, feet.below(), Direction.UP) || ground.is(BlockTags.LEAVES) || ground.is(Blocks.MAGMA_BLOCK)
			|| ground.is(Blocks.CACTUS) || ground.is(Blocks.POWDER_SNOW) || !level.getFluidState(feet.below()).isEmpty()) {
			return false;
		}
		for (BlockPos p : new BlockPos[] {feet, feet.above()}) {
			BlockState state = level.getBlockState(p);
			if (!state.getCollisionShape(level, p).isEmpty() || !level.getFluidState(p).isEmpty() || state.is(BlockTags.FIRE)
				|| state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.CACTUS)) {
				return false;
			}
		}
		for (BlockPos p : BlockPos.betweenClosed(feet.offset(-2, -1, -2), feet.offset(2, 1, 2))) {
			if (level.getFluidState(p).is(FluidTags.LAVA) || level.getBlockState(p).is(BlockTags.FIRE)) {
				return false;
			}
		}
		return true;
	}
}
