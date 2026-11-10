package com.deadlands.client.hud;

import com.deadlands.client.mixin.BossHealthOverlayAccessor;
import com.deadlands.client.ui.DLTheme;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;

/**
 * Boss bars in the DEADLANDS style, in place of Minecraft's: a dark strip with the name in the accent
 * colour, a blood-red (or, for Patient Zero, bruise-purple) fill with notches, and a pale band where
 * health was just lost. Every boss bar is drawn this way, DEADLANDS or not.
 */
public final class BossBarHud {
	private static final int W = 182;
	private static final int H = 5;

	private BossBarHud() {
	}

	public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.options.hideGui) {
			return;
		}
		var events = ((BossHealthOverlayAccessor) minecraft.gui.getBossOverlay()).deadlands$events();
		if (events.isEmpty()) {
			return;
		}
		int x = graphics.guiWidth() / 2 - W / 2;
		int y = 12;
		for (LerpingBossEvent event : events.values()) {
			float progress = Mth.clamp(event.getProgress(), 0.0F, 1.0F);
			int fill = event.getColor() == BossEvent.BossBarColor.PURPLE ? 0xFF6A2A6A : event.getColor() == BossEvent.BossBarColor.RED ? 0xFF8A1E16
				: 0xFF6A6A5A;
			graphics.drawCenteredString(minecraft.font, event.getName(), graphics.guiWidth() / 2, y - 9, DLTheme.ACCENT);
			graphics.fill(x - 2, y - 1, x + W + 2, y + H + 1, 0xCC0E0C0A);
			graphics.fill(x, y, x + W, y + H, 0xFF2A2420);
			int filled = Math.round(W * progress);
			graphics.fill(x, y, x + filled, y + H, fill);
			graphics.fill(x, y, x + filled, y + 1, DLTheme.withAlpha(0xFFFFFFFF, 0.18F));
			if (event.getOverlay() != BossEvent.BossBarOverlay.PROGRESS) {
				int notches = switch (event.getOverlay()) {
					case NOTCHED_6 -> 6;
					case NOTCHED_10 -> 10;
					case NOTCHED_12 -> 12;
					case NOTCHED_20 -> 20;
					default -> 1;
				};
				for (int i = 1; i < notches; i++) {
					int nx = x + i * W / notches;
					graphics.fill(nx, y, nx + 1, y + H, 0x99000000);
				}
			}
			y += 19;
			if (y >= graphics.guiHeight() / 3) {
				break;
			}
		}
	}
}
