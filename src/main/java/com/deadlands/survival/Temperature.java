package com.deadlands.survival;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What the surroundings do to a survivor's body temperature. {@link #ambient} gives the
 * temperature the body drifts toward, on the same -100 (deadly cold) to 100 (deadly heat) scale as
 * {@link SurvivalData#temperature()}:
 *
 * <ul>
 *   <li>the biome: frozen biomes about -55, temperate ones near 0, deserts about +45;</li>
 *   <li>night is colder (up to -18 under open sky), noon warmer (+8);</li>
 *   <li>height: -1 for every 8 blocks above sea level;</li>
 *   <li>a roof overhead takes the edge off both extremes;</li>
 *   <li>being wet (rain, snow, swimming): -25, and it stops heat from building up;</li>
 *   <li>fire nearby (campfire, fire, lava, a lit furnace) within 5 blocks: up to +45;</li>
 *   <li>clothing: each piece of armour worn is +5 against the cold (and +2 in the heat);</li>
 *   <li>sprinting warms a little; being on fire a lot.</li>
 * </ul>
 */
public final class Temperature {
	private static final int HEAT_RADIUS = 5;

	private Temperature() {
	}

	public static float ambient(ServerLevel level, ServerPlayer player) {
		BlockPos pos = player.blockPosition();
		float biome = level.getBiome(pos).value().getBaseTemperature();
		// Vanilla biome temperatures: about -0.5 (frozen) to 2.0 (desert); 0.8 is plains.
		float value = Mth.clamp((biome - 0.7F) * 42.0F, -60.0F, 55.0F);

		boolean openSky = level.canSeeSky(pos.above());
		float sun = Mth.cos((level.getDayTime() % 24000L) / 24000.0F * Mth.TWO_PI - Mth.TWO_PI * 0.25F); // 1 at noon, -1 at midnight
		float daily = sun > 0.0F ? sun * 8.0F : sun * 18.0F;
		value += openSky ? daily : daily * 0.4F;

		value -= Math.max(0, pos.getY() - level.getSeaLevel()) / 8.0F;
		if (!openSky) {
			value *= 0.75F; // shelter moderates
		}
		boolean wet = player.isInWaterOrRain() || player.isInPowderSnow;
		if (wet) {
			value = Math.min(value, 20.0F) - 25.0F;
		}
		float clothing = 0.0F;
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			if (!player.getItemBySlot(slot).isEmpty()) {
				clothing += 1.0F;
			}
		}
		value += clothing * (value < 0.0F ? 5.0F : 2.0F);
		value += heat(level, pos);
		if (player.isSprinting()) {
			value += 6.0F;
		}
		if (player.isOnFire()) {
			value += 60.0F;
		}
		return Mth.clamp(value, -SurvivalData.MAX, SurvivalData.MAX);
	}

	/** Warmth from the nearest fire within {@link #HEAT_RADIUS} blocks: 45 right next to it, fading with distance. */
	static float heat(ServerLevel level, BlockPos center) {
		double nearest = Double.MAX_VALUE;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int dx = -HEAT_RADIUS; dx <= HEAT_RADIUS; dx++) {
			for (int dy = -2; dy <= 2; dy++) {
				for (int dz = -HEAT_RADIUS; dz <= HEAT_RADIUS; dz++) {
					cursor.setWithOffset(center, dx, dy, dz);
					if (isHeatSource(level.getBlockState(cursor))) {
						nearest = Math.min(nearest, Math.sqrt(dx * dx + dy * dy + dz * dz));
					}
				}
			}
		}
		if (nearest > HEAT_RADIUS) {
			return 0.0F;
		}
		return 45.0F * (float) (1.0 - Math.max(0.0, nearest - 1.0) / HEAT_RADIUS);
	}

	private static boolean isHeatSource(BlockState state) {
		if (state.isAir()) {
			return false;
		}
		if (state.is(BlockTags.CAMPFIRES)) {
			return state.getValue(CampfireBlock.LIT);
		}
		return state.is(BlockTags.FIRE) || state.is(Blocks.LAVA) || state.is(Blocks.MAGMA_BLOCK)
			|| (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)
				&& state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)
				&& (state.is(Blocks.FURNACE) || state.is(Blocks.BLAST_FURNACE) || state.is(Blocks.SMOKER)));
	}
}
