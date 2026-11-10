package com.deadlands.registry;

import com.deadlands.Deadlands;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/** Block tags read by DEADLANDS code (the tag files are written by tools/assetgen/gen_infected.py). */
public final class DLTags {
	/** Barricades every infected can tear down: doors, trapdoors, fences, windows, boarded planks. */
	public static final TagKey<Block> INFECTED_BREAKABLE = block("infected_breakable");
	/** Extra blocks only a Brute can smash: planks, iron bars and iron doors. */
	public static final TagKey<Block> BRUTE_BREAKABLE = block("brute_breakable");
	/** Blocks that count as part of a nest (biomass and nest cores). */
	public static final TagKey<Block> NEST_MATERIAL = block("nest_material");

	/** Paved ground: vehicles reach their full speed on it. */
	public static final TagKey<Block> ROAD_SURFACE = block("road_surface");

	private DLTags() {
	}

	private static TagKey<Block> block(String name) {
		return TagKey.create(Registries.BLOCK, Deadlands.id(name));
	}
}
