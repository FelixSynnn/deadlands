package com.deadlands.registry;

import com.deadlands.Deadlands;
import com.deadlands.event.SupplyDropEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** World event entities (Milestone 9): the parachuted supply crate. */
public final class DLEvents {
	public static final EntityType<SupplyDropEntity> SUPPLY_DROP = register();

	private DLEvents() {
	}

	private static EntityType<SupplyDropEntity> register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Deadlands.id("supply_drop"));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
			EntityType.Builder.<SupplyDropEntity>of(SupplyDropEntity::new, MobCategory.MISC).sized(1.0F, 1.0F).clientTrackingRange(16).build(key));
	}

	public static void init() {
		// Class loading registers everything above.
	}
}
