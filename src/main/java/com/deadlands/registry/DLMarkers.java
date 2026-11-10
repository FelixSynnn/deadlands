package com.deadlands.registry;

import com.deadlands.Deadlands;
import com.deadlands.block.SpawnMarkerBlock;
import com.deadlands.npc.NpcEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;

/**
 * The survivor entity type (Milestone 7) and the spawn marker block that structure templates and
 * town buildings use to place survivors and vehicles. Call after {@link DLBlocks}.
 */
public final class DLMarkers {
	public static final Block SPAWN_MARKER = DLBlocks.register("spawn_marker", SpawnMarkerBlock::new,
		BlockBehaviour.Properties.of().noCollision().noOcclusion().noLootTable().strength(-1.0F, 3600000.0F).pushReaction(PushReaction.BLOCK).replaceable(),
		DLTab.CUSTOM_BLOCKS);

	public static final BlockEntityType<SpawnMarkerBlock.MarkerEntity> MARKER = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE,
		Deadlands.id("spawn_marker"),
		FabricBlockEntityTypeBuilder.create(SpawnMarkerBlock.MarkerEntity::new, SPAWN_MARKER).build()
	);

	public static final EntityType<NpcEntity> SURVIVOR = registerSurvivor();

	private DLMarkers() {
	}

	private static EntityType<NpcEntity> registerSurvivor() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Deadlands.id("survivor"));
		EntityType<NpcEntity> type = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
			EntityType.Builder.<NpcEntity>of(NpcEntity::new, MobCategory.MISC).sized(0.6F, 1.8F).eyeHeight(1.62F).clientTrackingRange(10).build(key));
		FabricDefaultAttributeRegistry.register(type, NpcEntity.createAttributes());
		return type;
	}

	public static void init() {
		// Class loading registers everything above.
	}
}
