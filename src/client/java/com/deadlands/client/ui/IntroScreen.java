package com.deadlands.client.ui;

import com.deadlands.network.IntroDonePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The opening: black, then the outbreak in a few typed lines (day -90 to day 0), then the screen
 * fades up on Harley's camp and she speaks. Any key or click skips. Nothing else interrupts like
 * this; it plays once per survivor, on their first arrival.
 */
public class IntroScreen extends Screen {
	private static final int LINES = 7;
	private static final int TICKS_PER_LINE = 50;
	private static final int FADE_TICKS = 40;

	private final boolean harleyNear;
	private final net.minecraft.world.phys.Vec3 camp;
	private int ticks;
	private boolean finished;

	public IntroScreen(boolean harleyNear, net.minecraft.world.phys.Vec3 camp) {
		super(Component.translatable("intro.deadlands.title"));
		this.harleyNear = harleyNear;
		this.camp = camp;
	}

	@Override
	public void tick() {
		ticks++;
		if (ticks >= LINES * TICKS_PER_LINE + FADE_TICKS) {
			finish(false);
		}
	}

	private void finish(boolean skipped) {
		if (finished) {
			return;
		}
		finished = true;
		onClose();
		Runnable done = () -> {
			if (ClientPlayNetworking.canSend(IntroDonePayload.TYPE)) {
				ClientPlayNetworking.send(new IntroDonePayload(skipped));
			}
		};
		// Skipping the words skips the camera too; otherwise the camera circles Harley's camp before she speaks.
		if (harleyNear && !skipped) {
			com.deadlands.client.cutscene.CutscenePlayer.start("opening", camp, 0.0F, done);
		} else {
			done.run();
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		finish(true);
		return true;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		finish(true);
		return true;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		float t = ticks + partialTick;
		int fadeStart = LINES * TICKS_PER_LINE;
		float alpha = t < fadeStart ? 1.0F : 1.0F - Mth.clamp((t - fadeStart) / FADE_TICKS, 0.0F, 1.0F);
		graphics.fill(0, 0, width, height, DLTheme.withAlpha(0xFF000000, alpha));
		int y = height / 2 - LINES * 7;
		for (int i = 0; i < LINES; i++) {
			float start = i * TICKS_PER_LINE;
			if (t < start) {
				break;
			}
			String text = Component.translatable("intro.deadlands.line." + i + (i == LINES - 1 && !harleyNear ? ".alone" : "")).getString();
			int shown = Mth.clamp((int) ((t - start) * 1.6F), 0, text.length());
			int colour = i == 0 || i == LINES - 1 ? DLTheme.ACCENT : DLTheme.TEXT;
			graphics.drawCenteredString(font, text.substring(0, shown), width / 2, y, DLTheme.withAlpha(colour, alpha));
			y += 14;
		}
		Component skip = Component.translatable("intro.deadlands.skip");
		graphics.drawString(font, skip, width - font.width(skip) - 8, height - 14, DLTheme.withAlpha(DLTheme.TEXT_FAINT, alpha));
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
