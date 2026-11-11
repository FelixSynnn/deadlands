package com.deadlands.registry;

import com.deadlands.Deadlands;
import com.deadlands.block.DecalBlock;
import com.deadlands.block.RoadLineBlock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.UntintedParticleLeavesBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * Building and ground blocks. Each one has generated textures (with LabPBR maps), a blockstate,
 * models, a loot table, a mining tag and an English name (tools/assetgen/gen_blocks.py); the
 * asset-completeness game test enforces that for every block registered here.
 */
public final class DLBlocks {
	private static final List<Block> ALL = new ArrayList<>();
	private static final List<Block> DECALS = new ArrayList<>();

	// Roads and pavements.
	public static final Block ASPHALT = register("asphalt", Block::new, stone(MapColor.COLOR_BLACK, 1.6F), DLTab.BUILDING_MATERIALS);
	public static final Block CRACKED_ASPHALT = register("cracked_asphalt", Block::new, stone(MapColor.COLOR_BLACK, 1.4F), DLTab.BUILDING_MATERIALS);
	public static final Block ASPHALT_YELLOW_LINE = register("asphalt_yellow_line", RoadLineBlock::new, stone(MapColor.COLOR_BLACK, 1.6F), DLTab.BUILDING_MATERIALS);
	public static final Block ASPHALT_WHITE_LINE = register("asphalt_white_line", RoadLineBlock::new, stone(MapColor.COLOR_BLACK, 1.6F), DLTab.BUILDING_MATERIALS);
	public static final Block SIDEWALK = register("sidewalk", Block::new, stone(MapColor.STONE, 1.8F), DLTab.BUILDING_MATERIALS);

	// Concrete.
	public static final Block CONCRETE = register("concrete", Block::new, stone(MapColor.STONE, 1.8F), DLTab.BUILDING_MATERIALS);
	public static final Block CRACKED_CONCRETE = register("cracked_concrete", Block::new, stone(MapColor.STONE, 1.5F), DLTab.BUILDING_MATERIALS);
	public static final Block STAINED_CONCRETE = register("stained_concrete", Block::new, stone(MapColor.STONE, 1.8F), DLTab.BUILDING_MATERIALS);
	public static final Block MOSSY_CONCRETE = register("mossy_concrete", Block::new, stone(MapColor.STONE, 1.6F), DLTab.BUILDING_MATERIALS);

	// Masonry and interiors.
	public static final Block WEATHERED_BRICKS = register("weathered_bricks", Block::new, stone(MapColor.TERRACOTTA_RED, 2.0F), DLTab.BUILDING_MATERIALS);
	public static final Block SOOTY_BRICKS = register("sooty_bricks", Block::new, stone(MapColor.TERRACOTTA_BLACK, 2.0F), DLTab.BUILDING_MATERIALS);
	public static final Block PLASTER_WALL = register("plaster_wall", Block::new, stone(MapColor.SAND, 1.2F), DLTab.BUILDING_MATERIALS);
	public static final Block PEELING_PLASTER = register("peeling_plaster", Block::new, stone(MapColor.COLOR_LIGHT_GREEN, 1.0F), DLTab.BUILDING_MATERIALS);
	public static final Block HOSPITAL_TILES = register("hospital_tiles", Block::new, stone(MapColor.QUARTZ, 1.5F), DLTab.BUILDING_MATERIALS);
	public static final Block CRACKED_HOSPITAL_TILES = register("cracked_hospital_tiles", Block::new, stone(MapColor.QUARTZ, 1.2F), DLTab.BUILDING_MATERIALS);

	// Wood.
	public static final Block BOARDED_PLANKS = register("boarded_planks", Block::new, wood(MapColor.WOOD), DLTab.BUILDING_MATERIALS);
	public static final Block WEATHERED_SIDING = register("weathered_siding", Block::new, wood(MapColor.COLOR_LIGHT_BLUE), DLTab.BUILDING_MATERIALS);

	// Metal.
	public static final Block RUSTED_SHEET_METAL = register("rusted_sheet_metal", Block::new, metal(MapColor.TERRACOTTA_ORANGE), DLTab.BUILDING_MATERIALS);
	public static final Block CORRUGATED_METAL = register("corrugated_metal", Block::new, metal(MapColor.METAL), DLTab.BUILDING_MATERIALS);
	public static final Block DIAMOND_PLATE = register("diamond_plate", Block::new, metal(MapColor.METAL), DLTab.BUILDING_MATERIALS);

	// Ground and debris.
	public static final Block RUBBLE = register("rubble", Block::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(1.2F, 4.0F).requiresCorrectToolForDrops().sound(SoundType.GRAVEL), DLTab.CUSTOM_BLOCKS);
	public static final Block ASH = register("ash", Block::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GRAY).strength(0.4F).sound(SoundType.SAND), DLTab.CUSTOM_BLOCKS);
	public static final Block DRY_DIRT = register("dry_dirt", Block::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).strength(0.5F).sound(SoundType.ROOTED_DIRT), DLTab.CUSTOM_BLOCKS);
	public static final Block DEAD_GRASS_BLOCK = register("dead_grass_block", Block::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_YELLOW).strength(0.6F).sound(SoundType.GRASS), DLTab.CUSTOM_BLOCKS);
	public static final Block CRACKED_EARTH = register("cracked_earth", Block::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE).strength(0.6F).sound(SoundType.PACKED_MUD), DLTab.CUSTOM_BLOCKS);

	// Dead vegetation (Milestone 3 biomes).
	public static final Block DEAD_LOG = register("dead_log", RotatedPillarBlock::new,
		Blocks.logProperties(MapColor.COLOR_LIGHT_GRAY, MapColor.COLOR_GRAY, SoundType.WOOD), DLTab.CUSTOM_BLOCKS);
	public static final Block CHARRED_LOG = register("charred_log", RotatedPillarBlock::new,
		Blocks.logProperties(MapColor.COLOR_BLACK, MapColor.COLOR_BLACK, SoundType.WOOD), DLTab.CUSTOM_BLOCKS);
	public static final Block DEAD_LEAVES = register("dead_leaves",
		properties -> new UntintedParticleLeavesBlock(0.02F, ColorParticleOption.create(ParticleTypes.TINTED_LEAVES, 0xFF7A5C3A), properties),
		Blocks.leavesProperties(SoundType.GRASS).mapColor(MapColor.COLOR_BROWN), DLTab.CUSTOM_BLOCKS);

	// Glass.
	public static final Block BROKEN_GLASS = register("broken_glass", TransparentBlock::new,
		BlockBehaviour.Properties.of().instrument(NoteBlockInstrument.HAT).strength(0.3F).sound(SoundType.GLASS).noOcclusion()
			.isRedstoneConductor(Blocks::never).isSuffocating(Blocks::never).isViewBlocking(Blocks::never), DLTab.BUILDING_MATERIALS);

	// Atmosphere overhaul: more of an abandoned town's surfaces (tools/assetgen/gen_blocks.py).
	public static final Block DIRTY_CONCRETE = register("dirty_concrete", Block::new, stone(MapColor.STONE, 1.8F), DLTab.BUILDING_MATERIALS);
	public static final Block BROKEN_CONCRETE = register("broken_concrete", Block::new, stone(MapColor.STONE, 1.3F), DLTab.BUILDING_MATERIALS);
	public static final Block POTHOLED_ASPHALT = register("potholed_asphalt", Block::new, stone(MapColor.COLOR_BLACK, 1.4F), DLTab.BUILDING_MATERIALS);
	public static final Block ROTTING_PLANKS = register("rotting_planks", Block::new, wood(MapColor.COLOR_BROWN), DLTab.BUILDING_MATERIALS);
	public static final Block FADED_PLANKS = register("faded_planks", Block::new, wood(MapColor.COLOR_LIGHT_GRAY), DLTab.BUILDING_MATERIALS);
	public static final Block CORRODED_METAL = register("corroded_metal", Block::new, metal(MapColor.TERRACOTTA_GREEN), DLTab.BUILDING_MATERIALS);
	public static final Block INDUSTRIAL_PANEL = register("industrial_panel", Block::new, metal(MapColor.COLOR_GREEN), DLTab.BUILDING_MATERIALS);
	public static final Block DIRTY_GLASS = glass("dirty_glass");
	public static final Block CRACKED_GLASS = glass("cracked_glass");
	public static final Block DUSTY_GLASS = glass("dusty_glass");

	// Slabs for roads on slopes and building trim.
	public static final Block ASPHALT_SLAB = slab("asphalt_slab", ASPHALT);
	public static final Block CRACKED_ASPHALT_SLAB = slab("cracked_asphalt_slab", CRACKED_ASPHALT);
	public static final Block CONCRETE_SLAB = slab("concrete_slab", CONCRETE);
	public static final Block SIDEWALK_SLAB = slab("sidewalk_slab", SIDEWALK);

	// Environmental storytelling: flat marks for floors, walls and ceilings.
	public static final Block DRIED_BLOOD_SPOTS = decal("dried_blood_spots");
	public static final Block DRIED_BLOOD_POOL = decal("dried_blood_pool");
	public static final Block BLOOD_SMEAR = decal("blood_smear");
	public static final Block BLOODY_HANDPRINT = decal("bloody_handprint");
	public static final Block DRAG_MARKS = decal("drag_marks");
	public static final Block FADED_STAIN = decal("faded_stain");
	public static final Block SCORCH_MARK = decal("scorch_mark");
	public static final Block SURVIVOR_MARK = decal("survivor_mark");
	public static final Block EVACUATION_NOTICE = decal("evacuation_notice");
	public static final Block SCATTERED_PAPERS = decal("scattered_papers");

	private DLBlocks() {
	}

	private static Block decal(String name) {
		return registerDecal(name, DecalBlock::new, BlockBehaviour.Properties.of().noCollision().noOcclusion().strength(0.1F)
			.noLootTable().replaceable().pushReaction(PushReaction.DESTROY).sound(SoundType.WOOL));
	}

	/** Registers a flat mark (translucent, floor/wall/ceiling) such as blood or nest veins. */
	static Block registerDecal(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
		Block block = register(name, factory, properties, DLTab.CUSTOM_BLOCKS);
		DECALS.add(block);
		return block;
	}

	public static List<Block> decals() {
		return Collections.unmodifiableList(DECALS);
	}

	private static Block glass(String name) {
		return register(name, TransparentBlock::new, BlockBehaviour.Properties.of().instrument(NoteBlockInstrument.HAT).strength(0.3F).sound(SoundType.GLASS)
			.noOcclusion().isRedstoneConductor(Blocks::never).isSuffocating(Blocks::never).isViewBlocking(Blocks::never), DLTab.BUILDING_MATERIALS);
	}

	private static BlockBehaviour.Properties stone(MapColor colour, float hardness) {
		return BlockBehaviour.Properties.of().mapColor(colour).instrument(NoteBlockInstrument.BASEDRUM)
			.strength(hardness, 6.0F).requiresCorrectToolForDrops().sound(SoundType.STONE);
	}

	private static BlockBehaviour.Properties wood(MapColor colour) {
		return BlockBehaviour.Properties.of().mapColor(colour).instrument(NoteBlockInstrument.BASS)
			.strength(2.0F, 3.0F).sound(SoundType.WOOD).ignitedByLava();
	}

	private static BlockBehaviour.Properties metal(MapColor colour) {
		return BlockBehaviour.Properties.of().mapColor(colour).strength(3.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.METAL);
	}

	private static Block slab(String name, Block base) {
		return register(name, SlabBlock::new, BlockBehaviour.Properties.ofLegacyCopy(base), DLTab.BUILDING_MATERIALS);
	}

	static Block register(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties, DLTab tab) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Deadlands.id(name));
		Block block = factory.apply(properties.setId(key));
		Registry.register(BuiltInRegistries.BLOCK, key, block);
		DLItems.registerBlockItem(block, tab);
		ALL.add(block);
		return block;
	}

	public static List<Block> all() {
		return Collections.unmodifiableList(ALL);
	}

	public static void init() {
		// Class loading registers everything above.
	}
}
