package com.deadlands.vehicle;

import com.deadlands.item.FuelCanItem;
import com.deadlands.registry.DLVehicles;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Fitting parts to a vehicle. Engine parts add {@link #ENGINE_PER_PART} to the engine, a tire
 * {@link #TIRES_PER_TIRE} to the tires (four make a full set), a body panel {@link #BODY_PER_PANEL}
 * to the body; a battery is fitted once. All of them need a wrench somewhere in the inventory
 * (it is not used up). A fuel can pours into the tank and needs no tools.
 */
public final class VehicleParts {
	public static final float ENGINE_PER_PART = 25.0F;
	public static final float TIRES_PER_TIRE = 25.0F;
	public static final float BODY_PER_PANEL = 30.0F;

	private VehicleParts() {
	}

	public static boolean isPart(ItemStack stack) {
		return stack.is(DLVehicles.ENGINE_PARTS) || stack.is(DLVehicles.TIRE) || stack.is(DLVehicles.CAR_BATTERY) || stack.is(DLVehicles.BODY_PANEL)
			|| stack.getItem() instanceof FuelCanItem;
	}

	/** Server side: fit or pour {@code stack}. Tells the player why not when it cannot be done. */
	public static void fit(VehicleEntity vehicle, Player player, ItemStack stack) {
		if (vehicle.burned()) {
			tell(player, "vehicle.deadlands.need.burned");
			return;
		}
		if (stack.getItem() instanceof FuelCanItem) {
			float room = vehicle.kind().tank() - vehicle.fuel();
			float poured = Math.min(room, FuelCanItem.litres(stack));
			if (poured <= 0.0F) {
				tell(player, FuelCanItem.litres(stack) <= 0.0F ? "vehicle.deadlands.can_empty" : "vehicle.deadlands.tank_full");
				return;
			}
			FuelCanItem.setLitres(stack, FuelCanItem.litres(stack) - poured);
			vehicle.setFuel(vehicle.fuel() + poured);
			player.displayClientMessage(Component.translatable("vehicle.deadlands.fuelled", Math.round(vehicle.fuel()), Math.round(vehicle.kind().tank())), true);
			return;
		}
		if (!hasWrench(player)) {
			tell(player, "vehicle.deadlands.need_wrench");
			return;
		}
		if (stack.is(DLVehicles.ENGINE_PARTS)) {
			if (vehicle.engine() >= 100.0F) {
				tell(player, "vehicle.deadlands.not_needed");
				return;
			}
			vehicle.setEngine(vehicle.engine() + ENGINE_PER_PART);
		} else if (stack.is(DLVehicles.TIRE)) {
			if (vehicle.tires() >= 100.0F) {
				tell(player, "vehicle.deadlands.not_needed");
				return;
			}
			vehicle.setTires(vehicle.tires() + TIRES_PER_TIRE);
		} else if (stack.is(DLVehicles.BODY_PANEL)) {
			if (vehicle.body() >= 100.0F) {
				tell(player, "vehicle.deadlands.not_needed");
				return;
			}
			vehicle.setBody(vehicle.body() + BODY_PER_PANEL);
		} else if (stack.is(DLVehicles.CAR_BATTERY)) {
			if (vehicle.hasBattery()) {
				tell(player, "vehicle.deadlands.not_needed");
				return;
			}
			vehicle.setFlag(VehicleEntity.FLAG_BATTERY, true);
		} else {
			return;
		}
		stack.consume(1, player);
		vehicle.fitted(player);
	}

	private static boolean hasWrench(Player player) {
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (inventory.getItem(slot).is(DLVehicles.WRENCH)) {
				return true;
			}
		}
		return false;
	}

	private static void tell(Player player, String key) {
		player.displayClientMessage(Component.translatable(key), true);
	}
}
