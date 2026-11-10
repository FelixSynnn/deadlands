package com.deadlands.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;

/**
 * Chest-style storage (backpacks, and Minecraft's own chests and barrels) in DEADLANDS dress: the
 * same menu and slots for one to six rows, on a DEADLANDS panel.
 */
public class DLChestScreen extends DLMenuScreen<ChestMenu> {
	private final int rows;

	public DLChestScreen(ChestMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		this.rows = menu.getRowCount();
		this.imageHeight = 114 + rows * 18;
		this.inventoryLabelY = this.imageHeight - 94;
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		super.renderBg(graphics, partialTick, mouseX, mouseY);
		divider(graphics, leftPos + 7, leftPos + imageWidth - 7, topPos + 17 + rows * 18 + 3);
	}
}
