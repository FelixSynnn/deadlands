package com.deadlands.client.ui.widget;

import com.deadlands.client.ui.DLTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/**
 * Title-menu entry: bare text over the backdrop; on hover a rust brush-stroke slides in behind it
 * and an amber marker appears, the label easing a few pixels to the right.
 */
public class DLMenuButton extends DLButton {
	private float hover;
	private long lastFrame = Util.getMillis();

	public DLMenuButton(int x, int y, int width, int height, Component message, OnPress onPress) {
		super(x, y, width, height, message, onPress);
	}

	@Override
	protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		long now = Util.getMillis();
		float dt = Math.min(0.1F, (now - lastFrame) / 1000.0F);
		lastFrame = now;
		boolean hot = active && isHoveredOrFocused();
		hover = Mth.clamp(hover + (hot ? dt * 7.0F : -dt * 5.0F), 0.0F, 1.0F);
		float ease = hover * hover * (3.0F - 2.0F * hover);

		if (ease > 0.01F) {
			int swatchWidth = (int) (getWidth() * (0.55F + 0.45F * ease));
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, DLTheme.MENU_HIGHLIGHT, getX() - 8, getY(), swatchWidth, getHeight(), ARGB.white(ease * alpha));
			graphics.fill(getX() - 4, getY() + 3, getX() - 2, getY() + getHeight() - 3, DLTheme.withAlpha(DLTheme.ACCENT, ease * alpha));
		}
		Font font = Minecraft.getInstance().font;
		int color = !active ? DLTheme.TEXT_FAINT : hot ? DLTheme.ACCENT_LIGHT : DLTheme.TEXT;
		int textX = getX() + 2 + Math.round(ease * 5.0F);
		graphics.drawString(font, getMessage(), textX, getY() + (getHeight() - 8) / 2, DLTheme.withAlpha(color, alpha), true);
	}
}
