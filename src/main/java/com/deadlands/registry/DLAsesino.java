package com.deadlands.registry;

import com.deadlands.Deadlands;
import com.deadlands.entity.AsesinoEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

/** The Asesino (Phase 5): its entity type, default attributes (from the config) and spawn egg. Call after {@link DLItems}. */
public final class DLAsesino {
	public static final EntityType<AsesinoEntity> ASESINO = register();
	public static final Item SPAWN_EGG = DLItems.register("asesino_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(ASESINO), DLTab.SPAWN_EGGS);

	private DLAsesino() {
	}

	private static EntityType<AsesinoEntity> register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Deadlands.id("asesino"));
		EntityType<AsesinoEntity> type = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
			EntityType.Builder.of(AsesinoEntity::new, MobCategory.MONSTER).sized(0.6F, 1.95F).eyeHeight(1.74F).clientTrackingRange(10)
				.notInPeaceful().build(key));
		FabricDefaultAttributeRegistry.register(type, AsesinoEntity.createAttributes());
		return type;
	}

	public static void init() {
		// Class loading registers everything.
	}
}
