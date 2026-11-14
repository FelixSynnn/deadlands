package com.deadlands.world.subway;

import com.deadlands.Deadlands;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jspecify.annotations.Nullable;

/**
 * Where Adam is found: most of the time ({@link #ADAM_UNDERGROUND}) in the subway station nearest Harley's
 * camp, in one of three places, each dressed with what he has been doing down there:
 *
 * <ul>
 *   <li><b>the maintenance room</b>: his lab: a desk under a lantern, notes everywhere, a bedroll in the corner;</li>
 *   <li><b>the far end of the south platform</b>: a camp in the open, a crate for a table, papers weighed down
 *       with a lantern, as far from the stairs as he could get;</li>
 *   <li><b>the office</b>: notes on the whiteboard and the floor, the door blocked behind him, a bedroll under
 *       the desk.</li>
 * </ul>
 * The spots are in the station template's own coordinates (tools/assetgen/gen_subway.py), turned with the
 * station; what is placed only replaces air.
 */
public final class SubwayPlaces {
	/** How often Adam is underground (otherwise he is placed as before). */
	public static final float ADAM_UNDERGROUND = 0.8F;
	public static final TagKey<Structure> SUBWAY = TagKey.create(Registries.STRUCTURE, Deadlands.id("subway"));
	/** Template spots (x, y, z) where Adam stands; y = 2 is the floor's walking level. */
	private static final List<BlockPos> SPOTS = List.of(new BlockPos(28, 2, 24), new BlockPos(60, 2, 17), new BlockPos(45, 2, 24));

	private SubwayPlaces() {
	}

	/** A place for Adam in the subway near {@code from}, dressed for him, or null (none near, or not this time). */
	public static @Nullable BlockPos adam(ServerLevel level, BlockPos from, RandomSource random) {
		if (random.nextFloat() >= ADAM_UNDERGROUND) {
			return null;
		}
		BlockPos found = level.findNearestMapStructure(SUBWAY, from, 40, false);
		if (found == null) {
			return null;
		}
		Structure station = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(SubwayAmbience.STATION);
		ChunkAccess chunk = level.getChunk(found.getX() >> 4, found.getZ() >> 4, ChunkStatus.STRUCTURE_STARTS, true);
		StructureStart start = station == null || chunk == null ? null : chunk.getStartForStructure(station);
		if (start == null || !start.isValid() || start.getPieces().isEmpty()) {
			return null;
		}
		StructurePiece first = start.getPieces().getFirst();
		if (!(first instanceof SubwayStructure.Piece piece)) {
			return null;
		}
		int which = random.nextInt(SPOTS.size());
		BlockPos spot = toWorld(piece, SPOTS.get(which));
		level.getChunk(spot); // make sure the station is built there before anything is placed
		dress(level, piece, which);
		return spot;
	}

	private static BlockPos toWorld(SubwayStructure.Piece piece, BlockPos local) {
		return StructureTemplate.transform(local, Mirror.NONE, piece.rotation(), BlockPos.ZERO).offset(piece.origin());
	}

	/** His things around the spot (template coordinates, turned with the station). */
	private static void dress(ServerLevel level, SubwayStructure.Piece piece, int which) {
		switch (which) {
			case 0 -> { // the maintenance room: his lab
				put(level, piece, 26, 2, 27, block("desk"));
				put(level, piece, 26, 3, 27, Blocks.LANTERN.defaultBlockState());
				put(level, piece, 27, 2, 26, block("chair"));
				put(level, piece, 30, 2, 27, Blocks.BROWN_CARPET.defaultBlockState());
				put(level, piece, 30, 2, 26, Blocks.WHITE_CARPET.defaultBlockState());
				papers(level, piece, new int[][] {{25, 25}, {28, 22}, {27, 23}});
			}
			case 1 -> { // the far end of the south platform: a camp in the open
				put(level, piece, 61, 2, 18, Blocks.BARREL.defaultBlockState());
				put(level, piece, 61, 3, 18, Blocks.LANTERN.defaultBlockState());
				put(level, piece, 62, 2, 16, Blocks.GRAY_CARPET.defaultBlockState());
				put(level, piece, 62, 2, 15, Blocks.WHITE_CARPET.defaultBlockState());
				papers(level, piece, new int[][] {{59, 18}, {60, 15}});
			}
			default -> { // the office, door blocked behind him
				put(level, piece, 46, 2, 27, Blocks.GREEN_CARPET.defaultBlockState());
				put(level, piece, 46, 3, 26, Blocks.LANTERN.defaultBlockState());
				put(level, piece, 44, 2, 21, Blocks.BARREL.defaultBlockState());
				papers(level, piece, new int[][] {{43, 23}, {47, 24}, {45, 22}});
			}
		}
	}

	private static void papers(ServerLevel level, SubwayStructure.Piece piece, int[][] at) {
		BlockState papers = block("scattered_papers");
		for (int[] xz : at) {
			put(level, piece, xz[0], 2, xz[1], papers);
		}
	}

	private static BlockState block(String name) {
		Block block = BuiltInRegistries.BLOCK.getValue(Deadlands.id(name));
		return block == Blocks.AIR ? Blocks.AIR.defaultBlockState() : block.defaultBlockState();
	}

	private static void put(ServerLevel level, SubwayStructure.Piece piece, int x, int y, int z, BlockState state) {
		BlockPos pos = toWorld(piece, new BlockPos(x, y, z));
		if (level.getBlockState(pos).isAir() && !state.isAir()) {
			level.setBlock(pos, state.hasProperty(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING)
				? state.rotate(piece.rotation()) : state, 2);
		}
	}
}
