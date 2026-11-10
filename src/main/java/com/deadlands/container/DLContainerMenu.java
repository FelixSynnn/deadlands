package com.deadlands.container;

import com.deadlands.registry.DLMenus;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;

/**
 * Container menu for DEADLANDS loot containers. Slot layout and shift-click behaviour come from
 * vanilla's {@link ChestMenu}; the separate menu type gives these containers their own screen.
 */
public class DLContainerMenu extends ChestMenu {
	private final ContainerStyle style;

	public DLContainerMenu(int syncId, Inventory inventory, Container container, int rows, ContainerStyle style) {
		super(DLMenus.LOOT_CONTAINER, syncId, inventory, container, rows);
		this.style = style;
	}

	/** Client-side constructor: the real inventory lives on the server. */
	public static DLContainerMenu client(int syncId, Inventory inventory, ContainerOpenData data) {
		return new DLContainerMenu(syncId, inventory, new SimpleContainer(data.rows() * 9), data.rows(), ContainerStyle.byId(data.style()));
	}

	public ContainerStyle style() {
		return style;
	}
}
