package com.deadlands.registry;

import com.deadlands.Deadlands;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.ItemLike;

/**
 * DEADLANDS creative inventory categories, in display order. Content registers itself into a
 * category; categories that have no content yet are not registered at all, so the creative
 * inventory never shows empty placeholder tabs.
 */
public enum DLTab {
	WEAPONS("weapons"),
	AMMUNITION("ammunition"),
	FOOD_AND_DRINK("food_and_drink"),
	MEDICAL("medical"),
	SURVIVAL_GEAR("survival_gear"),
	RADIO("radio"),
	LORE("lore"),
	QUEST_ITEMS("quest_items"),
	CRAFTING_MATERIALS("crafting_materials"),
	FURNITURE("furniture"),
	CONTAINERS("containers"),
	BUILDING_MATERIALS("building_materials"),
	VEHICLES("vehicles"),
	CUSTOM_BLOCKS("custom_blocks"),
	SPAWN_EGGS("spawn_eggs");

	private final String id;
	private final List<ItemLike> entries = new ArrayList<>();

	DLTab(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	public String translationKey() {
		return "itemGroup.deadlands." + id;
	}

	public ResourceKey<CreativeModeTab> key() {
		return ResourceKey.create(Registries.CREATIVE_MODE_TAB, Deadlands.id(id));
	}

	void add(ItemLike item) {
		entries.add(item);
	}

	public List<ItemLike> entries() {
		return Collections.unmodifiableList(entries);
	}
}
