package com.deadlands.client.ui.widget;

import com.deadlands.client.ui.DLTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

/** A DEADLANDS-styled slider: recessed track, riveted handle. Subclasses map 0..1 to real values. */
public abstract class DLSlider extends AbstractSliderButton {
	protected DLSlider(int x, int y, int width, int height, Component message, double value) {
		super(x, y, width, height, message, value);
	}

	@Override
	public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		boolean hot = active && (isHovered() || isFocused());
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, hot ? DLTheme.SLIDER_HIGHLIGHTED : DLTheme.SLIDER,
			getX(), getY(), getWidth(), getHeight(), ARGB.white(alpha));
		int handleX = getX() + (int) (value * (getWidth() - 8));
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, hot ? DLTheme.SLIDER_HANDLE_HIGHLIGHTED : DLTheme.SLIDER_HANDLE,
			handleX, getY(), 8, getHeight(), ARGB.white(alpha));
		int color = !active ? DLTheme.TEXT_FAINT : hot ? DLTheme.ACCENT_LIGHT : DLTheme.TEXT;
		graphics.drawCenteredString(Minecraft.getInstance().font, getMessage(), getX() + getWidth() / 2,
			getY() + (getHeight() - 8) / 2, DLTheme.withAlpha(color, alpha));
	}
}
