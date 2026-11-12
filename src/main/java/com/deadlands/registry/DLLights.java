package com.deadlands.registry;

import com.deadlands.block.EmergencyLightBlock;
import com.deadlands.block.FluorescentLightBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * Light fixtures (Phase 3 lighting): flickering fluorescents and battery emergency lights. Models,
 * textures, blockstates, loot, names and mining tags come from tools/assetgen/gen_visuals.py.
 */
public final class DLLights {
	public static final Block FLUORESCENT_LIGHT = DLBlocks.register("fluorescent_light", FluorescentLightBlock::new,
		fixture().lightLevel(state -> state.getValue(FluorescentLightBlock.LIT) ? 14 : 0), DLTab.FURNITURE);
	public static final Block EMERGENCY_LIGHT = DLBlocks.register("emergency_light", EmergencyLightBlock::new,
		fixture().lightLevel(state -> state.getValue(EmergencyLightBlock.LIT) ? (state.getValue(EmergencyLightBlock.PULSE) ? 10 : 4) : 0), DLTab.FURNITURE);

	private DLLights() {
	}

	private static BlockBehaviour.Properties fixture() {
		return BlockBehaviour.Properties.of()
			.mapColor(MapColor.METAL)
			.strength(0.8F, 1.6F)
			.sound(SoundType.METAL)
			.noOcclusion()
			.randomTicks()
			.isSuffocating(Blocks::never)
			.isViewBlocking(Blocks::never);
	}

	public static void init() {
		// Class loading registers everything above.
	}
}
