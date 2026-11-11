package com.deadlands.client.hud;

import com.deadlands.client.ui.DLTheme;
import com.deadlands.config.ConfigManager;
import com.deadlands.network.HordeEventPayload;
import com.deadlands.registry.DLSounds;
import java.util.Locale;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;

/**
 * Horde night on screen and in the ears.
 *
 * <ul>
 *   <li>A card when the horde starts ("THE HORDE - they are coming, N of them"), when it is broken,
 *       and at dawn (how many of them were killed), each with its sting.</li>
 *   <li>While it lasts, a small counter at the top right: how many are left.</li>
 *   <li>Now and then, the horde heard far off in some direction: a reminder of what is still out there.</li>
 * </ul>
 * The card and counter follow {@code ui.hordeHud}; the stings and distant sounds always play.
 */
public final class HordeHud {
	private static final float FADE_IN = 0.8F;
	private static final float HOLD_UNTIL = 5.5F;
	private static final float FADE_OUT = 1.4F;
	private static final int DISTANT_MIN_TICKS = 400;
	private static final int DISTANT_MAX_TICKS = 1000;

	private static @Nullable HordeEventPayload card;
	private static long cardStartedAt;
	private static boolean active;
	private static int total;
	private static int remaining;
	private static int nextDistant = -1;
	private static final RandomSource RANDOM = RandomSource.create();

	private HordeHud() {
	}

	public static void handle(HordeEventPayload payload) {
		switch (payload.kind()) {
			case HordeEventPayload.STATUS -> {
				active = true;
				total = payload.total();
				remaining = payload.remaining();
			}
			case HordeEventPayload.START -> {
				active = true;
				total = payload.total();
				remaining = payload.remaining();
				showCard(payload);
				DLTheme.playSting(DLSounds.EVENT_HORDE_START, 1.0F, 1.0F);
				nextDistant = DISTANT_MIN_TICKS / 2;
			}
			case HordeEventPayload.BROKEN -> {
				active = false;
				showCard(payload);
				DLTheme.playSting(DLSounds.EVENT_HORDE_BROKEN, 1.0F, 1.0F);
			}
			case HordeEventPayload.DAWN -> {
				active = false;
				showCard(payload);
				DLTheme.playSting(DLSounds.EVENT_HORDE_DAWN, 1.0F, 0.9F);
			}
			case HordeEventPayload.CLEAR -> active = false;
			default -> {
			}
		}
	}

	private static void showCard(HordeEventPayload payload) {
		card = payload;
		cardStartedAt = Util.getMillis();
	}

	public static void reset() {
		card = null;
		active = false;
		total = 0;
		remaining = 0;
		nextDistant = -1;
	}

	/** Client tick: the horde heard far away, every 20 to 50 seconds while it lasts. */
	public static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (!active || player == null || minecraft.isPaused()) {
			return;
		}
		if (nextDistant < 0) {
			nextDistant = DISTANT_MIN_TICKS + RANDOM.nextInt(DISTANT_MAX_TICKS - DISTANT_MIN_TICKS);
		}
		if (--nextDistant > 0) {
			return;
		}
		nextDistant = DISTANT_MIN_TICKS + RANDOM.nextInt(DISTANT_MAX_TICKS - DISTANT_MIN_TICKS);
		float angle = RANDOM.nextFloat() * Mth.TWO_PI;
		double x = player.getX() + Mth.cos(angle) * 28.0;
		double z = player.getZ() + Mth.sin(angle) * 28.0;
		minecraft.getSoundManager().play(new SimpleSoundInstance(DLSounds.EVENT_HORDE_DISTANT.value(), SoundSource.HOSTILE, 2.5F,
			0.9F + RANDOM.nextFloat() * 0.2F, RANDOM, x, player.getEyeY(), z));
	}

	public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
		if (!ConfigManager.get().ui.hordeHud) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.options.hideGui) {
			return;
		}
		Font font = minecraft.font;
		if (active && minecraft.screen == null) {
			renderCounter(graphics, font);
		}
		HordeEventPayload shown = card;
		if (shown != null) {
			float t = (Util.getMillis() - cardStartedAt) / 1000.0F;
			if (t > HOLD_UNTIL + FADE_OUT) {
				card = null;
				return;
			}
			if (!DayCardHud.isShowing()) {
				renderCard(graphics, font, shown, t);
			}
		}
	}

	private static void renderCounter(GuiGraphics graphics, Font font) {
		Component text = Component.translatable("hud.deadlands.horde.counter", String.format(Locale.ROOT, "%,d", Math.max(0, remaining)));
		int width = font.width(text);
		int x = graphics.guiWidth() - width - 10;
		int y = 10;
		graphics.fill(x - 6, y - 4, x + width + 6, y + 14, DLTheme.withAlpha(0xFF000000, 0.45F));
		graphics.drawString(font, text, x, y, DLTheme.DANGER, true);
		if (total > 0) {
			float left = Mth.clamp(remaining / (float) total, 0.0F, 1.0F);
			int barW = width + 12;
			graphics.fill(x - 6, y + 12, x - 6 + barW, y + 14, DLTheme.withAlpha(DLTheme.INK_RED, 0.8F));
			graphics.fill(x - 6, y + 12, x - 6 + (int) (barW * left), y + 14, DLTheme.DANGER);
		}
	}

	private static void renderCard(GuiGraphics graphics, Font font, HordeEventPayload shown, float t) {
		float alpha = t < FADE_IN ? t / FADE_IN : t > HOLD_UNTIL ? 1.0F - (t - HOLD_UNTIL) / FADE_OUT : 1.0F;
		alpha = Mth.clamp(alpha, 0.0F, 1.0F);
		alpha = alpha * alpha * (3.0F - 2.0F * alpha);
		if (alpha <= 0.02F) {
			return;
		}
		int cx = graphics.guiWidth() / 2;
		int y = (int) (graphics.guiHeight() * 0.22F);
		Component title;
		Component sub;
		switch (shown.kind()) {
			case HordeEventPayload.START -> {
				title = Component.translatable("hud.deadlands.horde.title");
				sub = Component.translatable("hud.deadlands.horde.coming", String.format(Locale.ROOT, "%,d", shown.total()));
			}
			case HordeEventPayload.BROKEN -> {
				title = Component.translatable("hud.deadlands.horde.broken");
				sub = null;
			}
			default -> {
				title = Component.translatable("hud.deadlands.horde.dawn");
				int killed = Math.max(0, shown.total() - shown.remaining());
				sub = Component.translatable("hud.deadlands.horde.dawn_sub", String.format(Locale.ROOT, "%,d", killed),
					String.format(Locale.ROOT, "%,d", shown.total()));
			}
		}
		int colour = shown.kind() == HordeEventPayload.START ? DLTheme.withAlpha(DLTheme.DANGER, alpha) : DLTheme.withAlpha(DLTheme.TEXT, alpha);
		int strokeW = 280;
		float reveal = Mth.clamp(t / 0.7F, 0.0F, 1.0F);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, DLTheme.DAY_STROKE, cx - strokeW / 2, y - 7,
			Math.max(1, (int) (strokeW * reveal)), 40, ARGB.white(alpha * 0.9F));
		float scale = 2.6F;
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(cx - font.width(title) * scale / 2.0F, y);
		pose.scale(scale, scale);
		graphics.drawString(font, title, 0, 0, colour, true);
		pose.popMatrix();
		if (sub != null) {
			graphics.drawCenteredString(font, sub, cx, y + 34, DLTheme.withAlpha(DLTheme.TEXT, alpha * 0.85F));
		}
	}
}
