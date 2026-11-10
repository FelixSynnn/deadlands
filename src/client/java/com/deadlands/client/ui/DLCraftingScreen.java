package com.deadlands.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.CraftingMenu;

/**
 * The 3x3 crafting grid (workbenches and crafting tables) in DEADLANDS dress: the same menu and
 * slots, a panel, slot wells and an arrow to the result. No recipe book.
 */
public class DLCraftingScreen extends DLMenuScreen<CraftingMenu> {
	public DLCraftingScreen(CraftingMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		this.titleLabelX = 29;
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		super.renderBg(graphics, partialTick, mouseX, mouseY);
		// Arrow from the grid to the result: a shaft and a head, stepped a pixel at a time.
		int ax = leftPos + 91;
		int ay = topPos + 42;
		graphics.fill(ax, ay - 1, ax + 14, ay + 2, DLTheme.TEXT_DIM);
		for (int i = 0; i < 6; i++) {
			graphics.fill(ax + 14 + i, ay - 5 + i, ax + 15 + i, ay + 6 - i, DLTheme.TEXT_DIM);
		}
		divider(graphics, leftPos + 7, leftPos + imageWidth - 7, topPos + 76);
	}
}
