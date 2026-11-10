package com.deadlands.client.hud;

import com.deadlands.client.ui.DLTheme;
import com.deadlands.config.ConfigManager;
import com.deadlands.registry.DLSounds;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.joml.Matrix3x2fStack;

/**
 * The DAY card: when a new day begins, "DAY 12" fades in over a painted stroke with a short line
 * underneath, holds, and fades out. The first day a survivor spends in a world uses the arrival line.
 */
public final class DayCardHud {
	private static final float FADE_IN = 1.0F;
	private static final float HOLD_UNTIL = 4.6F;
	private static final float FADE_OUT = 1.6F;
	private static final int SUBTITLE_COUNT = 12;

	private static final long STALE_AFTER_MS = 60_000L;

	private static long day = -1;
	private static boolean firstArrival;
	private static boolean pending;
	private static long receivedAt;
	private static long startedAt;

	private DayCardHud() {
	}

	/** Queue the card; it starts once no screen (loading screen, inventory...) covers the HUD. */
	public static void show(long newDay, boolean arrival) {
		if (!ConfigManager.get().ui.dayCard) {
			return;
		}
		day = newDay;
		firstArrival = arrival;
		pending = true;
		receivedAt = Util.getMillis();
	}

	public static boolean isShowing() {
		return day >= 0 && !pending && (Util.getMillis() - startedAt) / 1000.0F < HOLD_UNTIL + FADE_OUT;
	}

	public static void reset() {
		day = -1;
		pending = false;
	}

	public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
		if (day < 0) {
			return;
		}
		if (pending) {
			if (Util.getMillis() - receivedAt > STALE_AFTER_MS) {
				reset();
				return;
			}
			if (Minecraft.getInstance().screen != null) {
				return;
			}
			pending = false;
			startedAt = Util.getMillis();
			DLTheme.playUi(DLSounds.EVENT_DAY_BEGIN, 1.0F, 0.9F);
		}
		float t = (Util.getMillis() - startedAt) / 1000.0F;
		if (t > HOLD_UNTIL + FADE_OUT) {
			day = -1;
			return;
		}
		float alpha = t < FADE_IN ? t / FADE_IN : t > HOLD_UNTIL ? 1.0F - (t - HOLD_UNTIL) / FADE_OUT : 1.0F;
		alpha = Mth.clamp(alpha, 0.0F, 1.0F);
		alpha = alpha * alpha * (3.0F - 2.0F * alpha);
		if (alpha <= 0.02F) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		Font font = minecraft.font;
		int cx = graphics.guiWidth() / 2;
		int cy = (int) (graphics.guiHeight() * 0.27F);

		int strokeW = 230;
		int strokeH = 46;
		float reveal = Mth.clamp(t / 0.8F, 0.0F, 1.0F);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, DLTheme.DAY_STROKE, cx - strokeW / 2, cy - 8,
			Math.max(1, (int) (strokeW * reveal)), strokeH, ARGB.white(alpha));

		Component title = Component.translatable("hud.deadlands.day", day);
		float scale = 3.0F;
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(cx - font.width(title) * scale / 2.0F, cy);
		pose.scale(scale, scale);
		graphics.drawString(font, title, 0, 0, DLTheme.withAlpha(DLTheme.TEXT, alpha), true);
		pose.popMatrix();

		Component subtitle = firstArrival
			? Component.translatable("hud.deadlands.day.arrival")
			: Component.translatable("hud.deadlands.day.subtitle." + Math.floorMod(day * 7L + 3L, SUBTITLE_COUNT));
		graphics.drawCenteredString(font, subtitle, cx, cy + 32, DLTheme.withAlpha(DLTheme.TEXT, alpha * 0.92F));
	}
}
