package com.deadlands.world.town;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;

/**
 * A place where a town stands: its placement region, the chunk its structure starts in, its
 * centre (the middle of that chunk, on the surface), its size and its condition.
 */
public record TownSite(int regionX, int regionZ, int chunkX, int chunkZ, int centerX, int centerY, int centerZ, TownSize size, TownCondition condition) {
	/** Stable identity of the town in its world (the chunk its structure starts in). */
	public long key() {
		return ChunkPos.asLong(chunkX, chunkZ);
	}

	public BlockPos center() {
		return new BlockPos(centerX, centerY, centerZ);
	}

	public int gateX(Direction side) {
		return centerX + side.getStepX() * size.gateRadius();
	}

	public int gateZ(Direction side) {
		return centerZ + side.getStepZ() * size.gateRadius();
	}

	public double distanceTo(TownSite other) {
		return Math.hypot(other.centerX - centerX, other.centerZ - centerZ);
	}

	/** The side of this town facing another town, rounded to a cardinal direction. */
	public Direction sideFacing(TownSite other) {
		int dx = other.centerX - centerX;
		int dz = other.centerZ - centerZ;
		if (Math.abs(dx) >= Math.abs(dz)) {
			return dx >= 0 ? Direction.EAST : Direction.WEST;
		}
		return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
	}
}
