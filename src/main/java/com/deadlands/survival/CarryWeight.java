package com.deadlands.survival;

import com.deadlands.skill.Skills;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

/**
 * How much a survivor is carrying, in kilograms, and how much they can.
 *
 * <p>An item's weight follows how Minecraft already treats it: things that stack to 64 are light
 * (0.1 kg each; blocks 0.25), things that stack to 16 weigh 0.5, and things that do not stack
 * (tools, weapons, armour, kits) weigh 2, or 4 if they are armour. Capacity is
 * {@link #BASE_CAPACITY} plus the Survivalist bonus and the best backpack carried. Carrying more than that slows you in
 * proportion (down to 40% speed at double the capacity) and makes sprinting cost more.
 */
public final class CarryWeight {
	public static final float BASE_CAPACITY = 45.0F;

	private CarryWeight() {
	}

	public static float of(ItemStack stack) {
		if (stack.isEmpty()) {
			return 0.0F;
		}
		float each;
		int max = stack.getMaxStackSize();
		if (max >= 64) {
			each = stack.getItem() instanceof BlockItem ? 0.25F : 0.1F;
		} else if (max > 1) {
			each = 0.5F;
		} else {
			each = stack.has(DataComponents.EQUIPPABLE) ? 4.0F : 2.0F;
		}
		float total = each * stack.getCount();
		// What is inside a pack is still on your back.
		net.minecraft.world.item.component.ItemContainerContents inside = stack.get(DataComponents.CONTAINER);
		if (inside != null) {
			for (ItemStack held : inside.nonEmptyItems()) {
				total += of(held);
			}
		}
		return total;
	}

	/** Everything in the inventory, armour and off hand included. */
	public static float carried(Player player) {
		Inventory inventory = player.getInventory();
		float total = 0.0F;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			total += of(inventory.getItem(slot));
		}
		return total;
	}

	public static float capacity(Player player) {
		return BASE_CAPACITY + Skills.extraCapacity(player) + packBonus(player);
	}

	/** The best backpack carried counts (they do not add up). */
	public static float packBonus(Player player) {
		Inventory inventory = player.getInventory();
		float best = 0.0F;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (inventory.getItem(slot).getItem() instanceof com.deadlands.item.BackpackItem pack) {
				best = Math.max(best, pack.capacityBonus());
			}
		}
		return best;
	}

	/** Speed multiplier for a load: 1 up to capacity, falling to 0.4 at twice the capacity. */
	public static double speedFactor(float load, float capacity) {
		if (load <= capacity || capacity <= 0.0F) {
			return 1.0;
		}
		return Math.max(0.4, 1.0 - 0.6 * (load - capacity) / capacity);
	}
}
