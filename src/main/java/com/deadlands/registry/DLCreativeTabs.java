package com.deadlands.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/** Registers one creative tab per {@link DLTab} that has content. Call after all content registries. */
public final class DLCreativeTabs {
	private static final List<DLTab> REGISTERED = new ArrayList<>();

	private DLCreativeTabs() {
	}

	public static void init() {
		for (DLTab tab : DLTab.values()) {
			if (tab.entries().isEmpty()) {
				continue;
			}
			CreativeModeTab group = FabricItemGroup.builder()
				.title(Component.translatable(tab.translationKey()))
				.icon(() -> new ItemStack(tab.entries().getFirst()))
				.displayItems((parameters, output) -> tab.entries().forEach(output::accept))
				.build();
			Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, tab.key(), group);
			REGISTERED.add(tab);
		}
	}

	public static List<DLTab> registered() {
		return Collections.unmodifiableList(REGISTERED);
	}
}
