package com.deadlands.client.ui;

import com.deadlands.Deadlands;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

/**
 * Base for DEADLANDS versions of Minecraft's own inventory screens. The menu, its slots and where
 * they sit are Minecraft's (so every click, shift-click and drag behaves exactly as before); only
 * the look changes: a riveted panel, a recessed well under every slot, and DEADLANDS labels.
 */
public abstract class DLMenuScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {
	protected static final Identifier SLOT = Deadlands.id("widget/slot");

	protected DLMenuScreen(T menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		renderTooltip(graphics, mouseX, mouseY);
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, DLTheme.PANEL, leftPos - 4, topPos - 4, imageWidth + 8, imageHeight + 8);
		for (Slot slot : menu.slots) {
			if (slot.isActive()) {
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT, leftPos + slot.x - 1, topPos + slot.y - 1, 18, 18);
			}
		}
	}

	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		graphics.drawString(font, title, titleLabelX, titleLabelY, DLTheme.ACCENT, true);
		graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, DLTheme.TEXT_DIM, true);
	}

	/** A thin engraved line across the panel (screen coordinates). */
	protected static void divider(GuiGraphics graphics, int x0, int x1, int y) {
		graphics.fill(x0, y, x1, y + 1, 0x66000000);
		graphics.fill(x0, y + 1, x1, y + 2, 0x22FFFFFF);
	}
}
