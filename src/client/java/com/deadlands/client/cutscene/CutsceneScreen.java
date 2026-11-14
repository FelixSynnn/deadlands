package com.deadlands.client.cutscene;

import com.deadlands.client.ui.DLTheme;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** What is drawn over a cutscene: letterbox bars, the subtitle, the fade, and a hint that any key skips. */
public class CutsceneScreen extends Screen {
	public CutsceneScreen() {
		super(Component.translatable("cutscene.deadlands.title"));
	}

	@Override
	public void tick() {
		CutscenePlayer.tick();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		CutscenePlayer.stop();
		return true;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		CutscenePlayer.stop();
		return true;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		int bar = height / 8;
		graphics.fill(0, 0, width, bar, 0xFF000000);
		graphics.fill(0, height - bar, width, height, 0xFF000000);
		Component line = CutscenePlayer.subtitle();
		if (line != null) {
			List<FormattedCharSequence> lines = font.split(line, Math.min(420, width - 40));
			int y = height - bar - 10 - lines.size() * 11;
			for (FormattedCharSequence part : lines) {
				graphics.drawCenteredString(font, part, width / 2, y, DLTheme.TEXT);
				y += 11;
			}
		}
		Component skip = Component.translatable("intro.deadlands.skip");
		graphics.drawString(font, skip, width - font.width(skip) - 8, height - bar / 2 - 4, DLTheme.TEXT_FAINT);
		float fade = CutscenePlayer.fade(partialTick);
		if (fade > 0.0F) {
			graphics.fill(0, 0, width, height, DLTheme.withAlpha(0xFF000000, fade));
		}
		float[] alpha = new float[1];
		Component title = CutscenePlayer.title(alpha);
		if (title != null && alpha[0] > 0.02F) {
			graphics.pose().pushMatrix();
			graphics.pose().translate(width / 2.0F, height / 2.0F - 12.0F);
			graphics.pose().scale(3.0F, 3.0F);
			graphics.drawCenteredString(font, title, 0, 0, DLTheme.withAlpha(DLTheme.TEXT, alpha[0]));
			graphics.pose().popMatrix();
		}
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public void removed() {
		// Closed some other way (a death screen, disconnecting): end the cutscene too.
		CutscenePlayer.abandon();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
