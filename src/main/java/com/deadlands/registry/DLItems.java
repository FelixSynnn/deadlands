package com.deadlands.registry;

import com.deadlands.Deadlands;
import com.deadlands.item.KeyItem;
import com.deadlands.item.KeycardItem;
import com.deadlands.item.SurvivorJournalItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;

/**
 * Item registry. Every item declares its creative category at registration and must ship a
 * texture, model and item-model definition (checked by the game tests).
 */
public final class DLItems {
	private static final List<Item> ALL = new ArrayList<>();
	private static final List<BlockItem> BLOCK_ITEMS = new ArrayList<>();

	public static final Item SURVIVOR_JOURNAL = register("survivor_journal", SurvivorJournalItem::new,
		new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON), DLTab.LORE);

	// Keys open containers locked with the same key id; keycards open keycard locks up to their level.
	public static final Item KEY = register("key", KeyItem::new, new Item.Properties().stacksTo(1), DLTab.QUEST_ITEMS);
	public static final Item KEYCARD_GREEN = register("keycard_green", p -> new KeycardItem(1, p), new Item.Properties().stacksTo(1), DLTab.QUEST_ITEMS);
	public static final Item KEYCARD_BLUE = register("keycard_blue", p -> new KeycardItem(2, p),
		new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON), DLTab.QUEST_ITEMS);
	public static final Item KEYCARD_RED = register("keycard_red", p -> new KeycardItem(3, p),
		new Item.Properties().stacksTo(1).rarity(Rarity.RARE), DLTab.QUEST_ITEMS);

	/** Restarts a dead power generator (Milestone 3 power restoration). */
	public static final Item FLASHLIGHT = register("flashlight", com.deadlands.item.FlashlightItem::new, new Item.Properties(), DLTab.SURVIVAL_GEAR);
	public static final Item FUSE = register("fuse", Item::new, new Item.Properties().stacksTo(16), DLTab.CRAFTING_MATERIALS);

	private DLItems() {
	}

	static <T extends Item> T register(String name, Function<Item.Properties, T> factory, Item.Properties properties, DLTab tab) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Deadlands.id(name));
		T item = factory.apply(properties.setId(key));
		Registry.register(BuiltInRegistries.ITEM, key, item);
		tab.add(item);
		ALL.add(item);
		return item;
	}

	/** Registers the item form of a block under the same id (called by the block registries). */
	static BlockItem registerBlockItem(Block block, DLTab tab) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, BuiltInRegistries.BLOCK.getKey(block));
		BlockItem item = new BlockItem(block, new Item.Properties().useBlockDescriptionPrefix().setId(key));
		item.registerBlocks(Item.BY_BLOCK, item);
		Registry.register(BuiltInRegistries.ITEM, key, item);
		tab.add(item);
		BLOCK_ITEMS.add(item);
		return item;
	}

	/** Items that are not block forms (block items are listed by {@link #blockItems()}). */
	public static List<Item> all() {
		return Collections.unmodifiableList(ALL);
	}

	public static List<BlockItem> blockItems() {
		return Collections.unmodifiableList(BLOCK_ITEMS);
	}

	public static void init() {
		// Class loading registers everything above.
	}
}
