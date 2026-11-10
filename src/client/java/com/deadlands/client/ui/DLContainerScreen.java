package com.deadlands.client.ui;

import com.deadlands.Deadlands;
import com.deadlands.container.ContainerStyle;
import com.deadlands.container.DLContainerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Inventory screen for DEADLANDS containers. The background is assembled from nine-slice sprites
 * (wooden, metal or medical panel plus recessed slots), so it fits any row count without a
 * hand-drawn texture per size. Slot positions are vanilla's chest layout.
 */
public class DLContainerScreen extends AbstractContainerScreen<DLContainerMenu> {
	private static final Identifier SLOT = Deadlands.id("widget/slot");
	private static final Identifier PANEL_WOOD = Deadlands.id("widget/panel_wood");
	private static final Identifier PANEL_MEDICAL = Deadlands.id("widget/panel_medical");
	private final int rows;

	public DLContainerScreen(DLContainerMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		this.rows = menu.getRowCount();
		this.imageHeight = 114 + rows * 18;
		this.inventoryLabelY = this.imageHeight - 94;
	}

	private Identifier panel() {
		return switch (menu.style()) {
			case WOOD -> PANEL_WOOD;
			case MEDICAL -> PANEL_MEDICAL;
			case METAL -> DLTheme.PANEL;
		};
	}

	private int titleColour() {
		return menu.style() == ContainerStyle.MEDICAL ? DLTheme.INK_RED : DLTheme.ACCENT;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		renderTooltip(graphics, mouseX, mouseY);
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, panel(), leftPos - 4, topPos - 4, imageWidth + 8, imageHeight + 8);
		for (Slot slot : menu.slots) {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT, leftPos + slot.x - 1, topPos + slot.y - 1, 18, 18);
		}
		int dividerY = topPos + 17 + rows * 18 + 3;
		graphics.fill(leftPos + 7, dividerY, leftPos + imageWidth - 7, dividerY + 1, 0x66000000);
		graphics.fill(leftPos + 7, dividerY + 1, leftPos + imageWidth - 7, dividerY + 2, 0x22FFFFFF);
	}

	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		int label = menu.style() == ContainerStyle.MEDICAL ? DLTheme.INK_SOFT : DLTheme.TEXT_DIM;
		graphics.drawString(font, title, titleLabelX, titleLabelY, titleColour(), menu.style() != ContainerStyle.MEDICAL);
		graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, label, menu.style() != ContainerStyle.MEDICAL);
	}
}
