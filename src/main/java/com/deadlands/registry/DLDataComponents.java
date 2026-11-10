package com.deadlands.registry;

import com.deadlands.Deadlands;
import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;

/** Item data components. */
public final class DLDataComponents {
	/** Which lock a key opens, e.g. "ranger_station". Set by loot tables with minecraft:set_components. */
	public static final DataComponentType<String> KEY_ID = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE,
		Deadlands.id("key_id"),
		DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build()
	);

	/** Rounds loaded in a firearm. */
	public static final DataComponentType<Integer> LOADED = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE,
		Deadlands.id("loaded"),
		DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build()
	);

	/** Litres of fuel in a fuel can. */
	public static final DataComponentType<Float> FUEL = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE,
		Deadlands.id("fuel"),
		DataComponentType.<Float>builder().persistent(Codec.FLOAT).networkSynchronized(ByteBufCodecs.FLOAT).build()
	);

	private DLDataComponents() {
	}

	public static void init() {
		// Class loading registers everything above.
	}
}
