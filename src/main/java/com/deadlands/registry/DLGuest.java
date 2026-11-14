package com.deadlands.registry;

import com.deadlands.Deadlands;
import com.deadlands.entity.GuestEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

/** The Guest (Phase 5): its entity type, default attributes (from the config) and spawn egg. Call after {@link DLItems}. */
public final class DLGuest {
	public static final EntityType<GuestEntity> GUEST = register();
	public static final Item SPAWN_EGG = DLItems.register("guest_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(GUEST), DLTab.SPAWN_EGGS);

	private DLGuest() {
	}

	private static EntityType<GuestEntity> register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Deadlands.id("guest"));
		EntityType<GuestEntity> type = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
			EntityType.Builder.of(GuestEntity::new, MobCategory.MONSTER).sized(1.3F, 3.3F).eyeHeight(2.8F).clientTrackingRange(12)
				.notInPeaceful().build(key));
		FabricDefaultAttributeRegistry.register(type, GuestEntity.createAttributes());
		return type;
	}

	public static void init() {
		// Class loading registers everything.
	}
}
