package com.deadlands.item;

import com.deadlands.registry.DLDataComponents;
import com.deadlands.registry.DLItems;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Every survivor has a flashlight: one is handed over on joining and after every respawn if they
 * have none. Once a second, flashlights held and on lose a second of charge (switching off when
 * empty) and every other flashlight a survivor carries regains one.
 */
public final class Flashlights {
	private Flashlights() {
	}

	public static void init() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> ensureFlashlight(handler.getPlayer()));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> ensureFlashlight(newPlayer));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 == 7) {
				tick(server);
			}
		});
	}

	static void ensureFlashlight(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (inventory.getItem(slot).getItem() instanceof FlashlightItem) {
				return;
			}
		}
		ItemStack flashlight = new ItemStack(DLItems.FLASHLIGHT);
		if (!inventory.add(flashlight)) {
			player.drop(flashlight, false);
		}
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Inventory inventory = player.getInventory();
			ItemStack mainHand = player.getMainHandItem();
			ItemStack offHand = player.getOffhandItem();
			for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
				ItemStack stack = inventory.getItem(slot);
				if (!(stack.getItem() instanceof FlashlightItem)) {
					continue;
				}
				int charge = FlashlightItem.charge(stack);
				boolean held = stack == mainHand || stack == offHand;
				if (held && FlashlightItem.isOn(stack)) {
					charge = Math.max(0, charge - 1);
					stack.set(DLDataComponents.BATTERY, charge);
					if (charge == 0) {
						stack.set(DLDataComponents.FLASHLIGHT_ON, false); // dead: it recharges while off
					}
				} else if (charge < FlashlightItem.MAX_CHARGE) {
					stack.set(DLDataComponents.BATTERY, charge + 1);
				}
			}
		}
	}
}
