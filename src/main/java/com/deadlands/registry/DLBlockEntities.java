package com.deadlands.registry;

import com.deadlands.Deadlands;
import com.deadlands.container.LootContainerBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Block entity types. Call after {@link DLContainers}. */
public final class DLBlockEntities {
	public static final BlockEntityType<LootContainerBlockEntity> LOOT_CONTAINER = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE,
		Deadlands.id("loot_container"),
		FabricBlockEntityTypeBuilder.create(LootContainerBlockEntity::new, DLContainers.all().values().toArray(Block[]::new)).build()
	);

	private DLBlockEntities() {
	}

	public static void init() {
		// Class loading registers everything above.
	}
}
