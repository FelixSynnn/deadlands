package com.deadlands.registry;

import com.deadlands.Deadlands;
import com.deadlands.entity.DotEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

/** D.O.T., Patient-4: his entity type (fire-proof), default attributes (from the config) and spawn egg. Call after {@link DLItems}. */
public final class DLDot {
	public static final EntityType<DotEntity> DOT = register();
	public static final Item SPAWN_EGG = DLItems.register("dot_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(DOT), DLTab.SPAWN_EGGS);

	private DLDot() {
	}

	private static EntityType<DotEntity> register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Deadlands.id("dot"));
		EntityType<DotEntity> type = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
			EntityType.Builder.of(DotEntity::new, MobCategory.MONSTER).sized(0.9F, 2.7F).eyeHeight(2.35F).clientTrackingRange(12)
				.fireImmune().notInPeaceful().build(key));
		FabricDefaultAttributeRegistry.register(type, DotEntity.createAttributes());
		return type;
	}

	public static void init() {
		// Class loading registers everything.
	}
}
