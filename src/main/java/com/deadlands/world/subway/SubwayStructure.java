package com.deadlands.world.subway;

import com.deadlands.Deadlands;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * The subway station structure ({@code deadlands:subway}): one of the station templates
 * (tools/assetgen/gen_subway.py), placed only where it can lie fully underground.
 *
 * <p>Before anything is placed, the ground is sampled over the whole footprint (turned the way the station will
 * be turned): it is built only where the ground is level to within {@link #MAX_SLOPE} blocks and above sea level,
 * so it never ends up in a hillside or a mountain with its halls in the open air. The street entrance (the kiosk
 * at the template's corner) is set on the ground where it stands, and the halls are {@link #DEPTH} blocks below,
 * with at least half a dozen blocks of earth over their ceilings.
 */
public final class SubwayStructure extends Structure {
	public static final MapCodec<SubwayStructure> CODEC = simpleCodec(SubwayStructure::new);
	public static final StructureType<SubwayStructure> TYPE = () -> CODEC;
	public static final StructurePieceType.StructureTemplateType PIECE = Piece::new;

	/** The template's ground level (the surface) is this many blocks above its floor. */
	public static final int DEPTH = 16;
	private static final int SIZE_X = 96;
	private static final int SIZE_Z = 30;
	private static final int MAX_SLOPE = 5;
	/** The kiosk's ground, in template coordinates. */
	private static final BlockPos KIOSK = new BlockPos(2, 0, 23);
	private static final List<String> VARIANTS = List.of("station_a", "station_b", "station_c", "station_d", "station_e");

	public SubwayStructure(Structure.StructureSettings settings) {
		super(settings);
	}

	public static void register() {
		Registry.register(BuiltInRegistries.STRUCTURE_TYPE, Deadlands.id("subway"), TYPE);
		Registry.register(BuiltInRegistries.STRUCTURE_PIECE, Deadlands.id("subway_station"), PIECE);
	}

	@Override
	public Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
		ChunkPos chunk = context.chunkPos();
		RandomSource random = context.random();
		Rotation rotation = Rotation.getRandom(random);
		BlockPos origin = new BlockPos(chunk.getMinBlockX(), 0, chunk.getMinBlockZ());
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		// The ground over the whole footprint, every 12 blocks along and 15 across.
		for (int x = 0; x < SIZE_X; x += 12) {
			for (int z = 0; z < SIZE_Z; z += 14) {
				int h = height(context, origin, rotation, x, z);
				min = Math.min(min, h);
				max = Math.max(max, h);
			}
		}
		int far = height(context, origin, rotation, SIZE_X - 1, SIZE_Z - 1);
		min = Math.min(min, far);
		max = Math.max(max, far);
		int sea = context.chunkGenerator().getSeaLevel();
		if (max - min > MAX_SLOPE || min <= sea + 1) {
			return Optional.empty(); // a hillside, a mountain or a lake: no station here
		}
		int ground = height(context, origin, rotation, KIOSK.getX(), KIOSK.getZ());
		BlockPos base = origin.atY(ground - DEPTH);
		String variant = VARIANTS.get(random.nextInt(VARIANTS.size()));
		return Optional.of(new Structure.GenerationStub(base, builder ->
			builder.addPiece(new Piece(context.structureTemplateManager(), Deadlands.id("subway/" + variant), base, rotation))));
	}

	private static int height(Structure.GenerationContext context, BlockPos origin, Rotation rotation, int x, int z) {
		BlockPos at = StructureTemplate.transform(new BlockPos(x, 0, z), Mirror.NONE, rotation, BlockPos.ZERO).offset(origin);
		return context.chunkGenerator().getFirstOccupiedHeight(at.getX(), at.getZ(), Heightmap.Types.WORLD_SURFACE_WG,
			context.heightAccessor(), context.randomState());
	}

	@Override
	public StructureType<?> type() {
		return TYPE;
	}

	/** The station template, turned and placed. */
	public static final class Piece extends TemplateStructurePiece {
		public Piece(StructureTemplateManager templates, Identifier template, BlockPos pos, Rotation rotation) {
			super(PIECE, 0, templates, template, template.toString(), settings(rotation), pos);
		}

		public Piece(StructureTemplateManager templates, CompoundTag tag) {
			super(PIECE, tag, templates, id -> settings(tag.read("Rot", Rotation.LEGACY_CODEC).orElse(Rotation.NONE)));
		}

		private static StructurePlaceSettings settings(Rotation rotation) {
			return new StructurePlaceSettings().setRotation(rotation).setMirror(Mirror.NONE)
				.addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK).setLiquidSettings(LiquidSettings.IGNORE_WATERLOGGING);
		}

		public Rotation rotation() {
			return placeSettings.getRotation();
		}

		public BlockPos origin() {
			return templatePosition;
		}

		@Override
		protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
			super.addAdditionalSaveData(context, tag);
			tag.store("Rot", Rotation.LEGACY_CODEC, placeSettings.getRotation());
		}

		@Override
		protected void handleDataMarker(String marker, BlockPos pos, ServerLevelAccessor level, RandomSource random, BoundingBox box) {
		}
	}
}
