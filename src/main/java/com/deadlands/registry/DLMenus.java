package com.deadlands.registry;

import com.deadlands.Deadlands;
import com.deadlands.container.ContainerOpenData;
import com.deadlands.container.DLContainerMenu;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

/** Menu types. The loot container menu carries its row count and style to the client. */
public final class DLMenus {
	public static final ExtendedScreenHandlerType<DLContainerMenu, ContainerOpenData> LOOT_CONTAINER = Registry.register(
		BuiltInRegistries.MENU,
		Deadlands.id("loot_container"),
		new ExtendedScreenHandlerType<>(DLContainerMenu::client, ContainerOpenData.STREAM_CODEC)
	);

	private DLMenus() {
	}

	public static void init() {
		// Class loading registers everything above.
	}
}
