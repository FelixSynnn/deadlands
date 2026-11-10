package com.deadlands.client.ui.widget;

import com.deadlands.client.ui.DLTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

/** A DEADLANDS-styled push button (riveted gunmetal plate, amber edge when highlighted). */
public class DLButton extends AbstractButton {
	@FunctionalInterface
	public interface OnPress {
		void onPress(DLButton button);
	}

	private final OnPress onPress;

	public DLButton(int x, int y, int width, int height, Component message, OnPress onPress) {
		super(x, y, width, height, message);
		this.onPress = onPress;
	}

	@Override
	public void onPress(InputWithModifiers input) {
		onPress.onPress(this);
	}

	@Override
	protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		boolean hot = active && isHoveredOrFocused();
		Identifier sprite = !active ? DLTheme.BUTTON_DISABLED : hot ? DLTheme.BUTTON_HIGHLIGHTED : DLTheme.BUTTON;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, getX(), getY(), getWidth(), getHeight(), ARGB.white(alpha));
		renderLabel(graphics, hot);
	}

	protected void renderLabel(GuiGraphics graphics, boolean hot) {
		Font font = Minecraft.getInstance().font;
		int color = !active ? DLTheme.TEXT_FAINT : hot ? DLTheme.ACCENT_LIGHT : DLTheme.TEXT;
		net.minecraft.network.chat.Component label = getMessage();
		int room = getWidth() - 8;
		if (font.width(label) > room) {
			// Too long for the button: cut it rather than draw past the edges.
			label = net.minecraft.network.chat.Component.literal(font.plainSubstrByWidth(label.getString(), room - font.width("...")) + "...");
		}
		graphics.drawCenteredString(font, label, getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, DLTheme.withAlpha(color, alpha));
	}

	@Override
	public void playDownSound(SoundManager soundManager) {
		DLTheme.click();
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
