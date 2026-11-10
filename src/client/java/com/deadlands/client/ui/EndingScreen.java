package com.deadlands.client.ui;

import com.deadlands.client.ui.widget.DLButton;
import com.deadlands.network.EndingChoicePayload;
import com.deadlands.network.EndingChosenPayload;
import com.deadlands.network.EndingPayload;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * An ending: black, the ending's name, what happened, who came with you, and the credits rising,
 * then a button to carry on (the world goes on). {@link Choice} is the question at the helicopter.
 */
public class EndingScreen extends Screen {
	private final EndingPayload ending;
	private int ticks;

	public EndingScreen(EndingPayload ending) {
		super(Component.translatable("ending.deadlands." + ending.ending() + ".title"));
		this.ending = ending;
	}

	@Override
	protected void init() {
		addRenderableWidget(new DLButton(width / 2 - 80, height - 30, 160, 20, Component.translatable("ending.deadlands.continue"), button -> onClose()));
	}

	@Override
	public void tick() {
		ticks++;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, 0xFF000000);
		float t = ticks + partialTick;
		float fade = Mth.clamp(t / 40.0F, 0.0F, 1.0F);
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(width / 2.0F, 30.0F);
		pose.scale(2.5F, 2.5F);
		graphics.drawCenteredString(font, title, 0, 0, DLTheme.withAlpha(DLTheme.ACCENT, fade));
		pose.popMatrix();
		int y = 64;
		List<FormattedCharSequence> body = font.split(Component.translatable("ending.deadlands." + ending.ending() + ".body"), Math.min(360, width - 40));
		for (FormattedCharSequence line : body) {
			graphics.drawCenteredString(font, line, width / 2, y, DLTheme.withAlpha(DLTheme.TEXT, fade));
			y += 11;
		}
		y += 10;
		if (!ending.companions().isEmpty()) {
			graphics.drawCenteredString(font, Component.translatable("ending.deadlands.with"), width / 2, y, DLTheme.withAlpha(DLTheme.TEXT_DIM, fade));
			y += 12;
			for (String id : ending.companions()) {
				graphics.drawCenteredString(font, Component.translatable("ending.deadlands.companion." + id), width / 2, y, DLTheme.withAlpha(DLTheme.TEXT, fade));
				y += 11;
			}
		}
		float credits = Mth.clamp((t - 200.0F) / 60.0F, 0.0F, 1.0F);
		graphics.drawCenteredString(font, Component.translatable("ending.deadlands.credits"), width / 2, height - 52, DLTheme.withAlpha(DLTheme.ACCENT_LIGHT, credits));
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** At the helicopter: leave together, stay behind with the cure, or not yet. */
	public static class Choice extends Screen {
		private static final int W = 300;
		private final EndingChoicePayload choice;

		public Choice(EndingChoicePayload choice) {
			super(Component.translatable("ending.deadlands.choice.title"));
			this.choice = choice;
		}

		@Override
		protected void init() {
			int y = height / 2 - 10;
			for (String option : choice.options()) {
				addRenderableWidget(new DLButton((width - W) / 2 + 20, y, W - 40, 20, Component.translatable("ending.deadlands.choice." + option), button -> pick(option)));
				y += 24;
			}
		}

		private void pick(String option) {
			if (!option.equals("not_yet") && ClientPlayNetworking.canSend(EndingChosenPayload.TYPE)) {
				ClientPlayNetworking.send(new EndingChosenPayload(choice.entityId(), option));
			}
			onClose();
		}

		@Override
		public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
			int x = (width - W) / 2;
			int y = height / 2 - 50;
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, DLTheme.PANEL, x - 6, y - 8, W + 12, 40 + choice.options().size() * 24 + 20);
			graphics.drawString(font, title, x + 8, y, DLTheme.ACCENT, true);
			List<FormattedCharSequence> lines = new ArrayList<>(font.split(Component.translatable(choice.cureDone() ? "ending.deadlands.choice.cure_done"
				: "ending.deadlands.choice.cure_missing"), W - 16));
			int ty = y + 14;
			for (FormattedCharSequence line : lines) {
				graphics.drawString(font, line, x + 8, ty, DLTheme.TEXT, true);
				ty += 10;
			}
			super.render(graphics, mouseX, mouseY, partialTick);
		}

		@Override
		public boolean isPauseScreen() {
			return false;
		}
	}
}
