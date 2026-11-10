package com.deadlands.item;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * A backpack. Carried anywhere in the inventory it raises carrying capacity; held in the main
 * hand and used, it opens its own storage (two rows, or three for the military pack). What is
 * inside is kept on the item itself, still counts toward the weight carried, and goes with the
 * pack if it is dropped or put in a container.
 *
 * <p>While a pack is open its hotbar slot is locked, so it cannot be moved out from under its own
 * contents. Packs do not go inside packs: any found inside when it closes are handed back.
 * The screen is Minecraft's chest screen until the DEADLANDS inventory exists.
 */
public class BackpackItem extends Item {
	private final int rows;
	private final float capacityBonus;

	public BackpackItem(int rows, float capacityBonus, Properties properties) {
		super(properties);
		this.rows = rows;
		this.capacityBonus = capacityBonus;
	}

	/** Extra carrying capacity in kg while this pack is in the inventory. */
	public float capacityBonus() {
		return capacityBonus;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer survivor) {
			ItemStack pack = player.getItemInHand(hand);
			int hotbarSlot = player.getInventory().getSelectedSlot();
			SimpleContainer storage = new SimpleContainer(rows * 9);
			pack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(storage.getItems());
			MenuType<ChestMenu> type = rows == 2 ? MenuType.GENERIC_9x2 : MenuType.GENERIC_9x3;
			survivor.openMenu(new SimpleMenuProvider((id, inventory, opener) -> new Menu(type, id, inventory, storage, rows, hotbarSlot), pack.getHoverName()));
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.7F, 1.1F);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag) {
		String id = BuiltInRegistries.ITEM.getKey(this).getPath();
		lines.accept(Component.translatable("item.deadlands." + id + ".desc").withStyle(ChatFormatting.GRAY));
	}

	/** A chest menu over the pack's storage that writes back to the pack when it closes. */
	private static final class Menu extends ChestMenu {
		private final SimpleContainer storage;
		private final int hotbarSlot;
		private final int lockedMenuSlot;

		Menu(MenuType<ChestMenu> type, int id, Inventory inventory, SimpleContainer storage, int rows, int hotbarSlot) {
			super(type, id, inventory, storage, rows);
			this.storage = storage;
			this.hotbarSlot = hotbarSlot;
			this.lockedMenuSlot = rows * 9 + 27 + hotbarSlot; // storage, then the 27 inventory slots, then the hotbar
		}

		@Override
		public void clicked(int slot, int button, ClickType type, Player player) {
			if (slot == lockedMenuSlot || (type == ClickType.SWAP && button == hotbarSlot)) {
				return; // the open pack stays where it is
			}
			super.clicked(slot, button, type, player);
		}

		@Override
		public boolean stillValid(Player player) {
			return player.getInventory().getItem(hotbarSlot).getItem() instanceof BackpackItem;
		}

		@Override
		public void removed(Player player) {
			super.removed(player);
			if (player.level().isClientSide()) {
				return;
			}
			NonNullList<ItemStack> items = storage.getItems();
			for (int i = 0; i < items.size(); i++) {
				if (items.get(i).getItem() instanceof BackpackItem) {
					player.getInventory().placeItemBackInInventory(items.get(i));
					items.set(i, ItemStack.EMPTY);
				}
			}
			ItemStack pack = player.getInventory().getItem(hotbarSlot);
			if (pack.getItem() instanceof BackpackItem) {
				pack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
			} else {
				// The pack is gone (it should not be): never lose what was in it.
				for (ItemStack stack : items) {
					player.getInventory().placeItemBackInInventory(stack);
				}
			}
		}
	}
}
