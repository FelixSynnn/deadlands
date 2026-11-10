package com.deadlands.registry;

import com.deadlands.Deadlands;
import com.deadlands.block.DecorKind;
import com.deadlands.item.FuelCanItem;
import com.deadlands.vehicle.FuelPumpBlockEntity;
import com.deadlands.vehicle.VehicleEntity;
import com.deadlands.vehicle.VehicleKind;
import java.util.EnumMap;
import java.util.Map;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Vehicles (Milestone 6): one entity type per kind, the parts that restore them, the fuel can and
 * the fuel pump's block entity. Call after {@link DLItems} and {@link DLDecor}.
 */
public final class DLVehicles {
	private static final Map<VehicleKind, EntityType<VehicleEntity>> TYPES = new EnumMap<>(VehicleKind.class);

	static {
		for (VehicleKind kind : VehicleKind.values()) {
			ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Deadlands.id(kind.id()));
			EntityType<VehicleEntity> type = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
				EntityType.Builder.<VehicleEntity>of(VehicleEntity::new, MobCategory.MISC)
					.sized(kind.width(), kind.height())
					.eyeHeight(kind.height() * 0.6F)
					.clientTrackingRange(10)
					.updateInterval(2)
					.noLootTable()
					.build(key));
			VehicleKind.bind(kind, type);
			TYPES.put(kind, type);
		}
	}

	public static final Item FUEL_CAN = DLItems.register("fuel_can", FuelCanItem::new, new Item.Properties().stacksTo(1), DLTab.VEHICLES);
	public static final Item WRENCH = DLItems.register("wrench", Item::new, new Item.Properties().stacksTo(1), DLTab.VEHICLES);
	public static final Item ENGINE_PARTS = DLItems.register("engine_parts", Item::new, new Item.Properties().stacksTo(16), DLTab.VEHICLES);
	public static final Item TIRE = DLItems.register("tire", Item::new, new Item.Properties().stacksTo(4), DLTab.VEHICLES);
	public static final Item CAR_BATTERY = DLItems.register("car_battery", Item::new, new Item.Properties().stacksTo(1), DLTab.VEHICLES);
	public static final Item TOW_ROPE = DLItems.register("tow_rope", Item::new, new Item.Properties().stacksTo(1), DLTab.VEHICLES);
	public static final Item BODY_PANEL = DLItems.register("body_panel", Item::new, new Item.Properties().stacksTo(8), DLTab.VEHICLES);

	public static final BlockEntityType<FuelPumpBlockEntity> FUEL_PUMP = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE,
		Deadlands.id("fuel_pump"),
		FabricBlockEntityTypeBuilder.create(FuelPumpBlockEntity::new, DLDecor.get(DecorKind.FUEL_PUMP)).build()
	);

	private DLVehicles() {
	}

	public static EntityType<VehicleEntity> type(VehicleKind kind) {
		return TYPES.get(kind);
	}

	public static void init() {
		// Class loading registers everything above.
	}
}
