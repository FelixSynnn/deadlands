package com.deadlands.registry;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;

/**
 * Interior finishes (Phase 3): wallpapers, panelling, carpet and linoleum, so rooms stop looking
 * alike. Textures, models, states, loot, names and tags come from tools/assetgen/gen_interiors.py;
 * town buildings use them through gen_towns.interior_finish.
 */
public final class DLInteriors {
	public static final Block FADED_WALLPAPER = DLBlocks.register("faded_wallpaper", Block::new, wall(MapColor.SAND), DLTab.BUILDING_MATERIALS);
	public static final Block STRIPED_WALLPAPER = DLBlocks.register("striped_wallpaper", Block::new, wall(MapColor.COLOR_GREEN), DLTab.BUILDING_MATERIALS);
	public static final Block WOOD_PANELING = DLBlocks.register("wood_paneling", Block::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).sound(SoundType.WOOD).ignitedByLava(),
		DLTab.BUILDING_MATERIALS);
	public static final Block STAINED_CARPET = DLBlocks.register("stained_carpet", Block::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_RED).strength(1.0F, 2.0F).sound(SoundType.WOOL).ignitedByLava(), DLTab.BUILDING_MATERIALS);
	public static final Block LINOLEUM_TILES = DLBlocks.register("linoleum_tiles", Block::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).instrument(NoteBlockInstrument.BASEDRUM).strength(1.4F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.STONE),
		DLTab.BUILDING_MATERIALS);

	private DLInteriors() {
	}

	private static BlockBehaviour.Properties wall(MapColor colour) {
		return BlockBehaviour.Properties.of().mapColor(colour).instrument(NoteBlockInstrument.BASS).strength(1.2F, 3.0F).sound(SoundType.WOOD);
	}

	public static void init() {
		// Class loading registers everything above.
	}
}
