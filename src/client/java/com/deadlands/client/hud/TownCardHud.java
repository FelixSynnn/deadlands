package com.deadlands.client.hud;

import com.deadlands.client.ui.DLTheme;
import com.deadlands.config.ConfigManager;
import com.deadlands.network.TownCardPayload;
import com.deadlands.registry.DLSounds;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;

/**
 * The town card: when a survivor walks into a town, its name fades in over a painted stroke with
 * what it was (size, condition, how many lived there), what happened to it, who held it last, its
 * landmark, how much is left to scavenge, and one story. It waits for the DAY card to finish.
 */
public final class TownCardHud {
	private static final float FADE_IN = 1.0F;
	private static final float HOLD_UNTIL = 8.5F;
	private static final float FADE_OUT = 1.6F;
	private static final long STALE_AFTER_MS = 60_000L;
	private static final int WRAP = 300;

	private static @Nullable TownCardPayload card;
	private static boolean pending;
	private static long receivedAt;
	private static long startedAt;

	private TownCardHud() {
	}

	public static void show(TownCardPayload payload) {
		if (!ConfigManager.get().ui.townCards) {
			return;
		}
		card = payload;
		pending = true;
		receivedAt = Util.getMillis();
	}

	public static void reset() {
		card = null;
		pending = false;
	}

	public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
		TownCardPayload town = card;
		if (town == null) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (pending) {
			if (Util.getMillis() - receivedAt > STALE_AFTER_MS) {
				reset();
				return;
			}
			if (minecraft.screen != null || DayCardHud.isShowing()) {
				return;
			}
			pending = false;
			startedAt = Util.getMillis();
			DLTheme.playUi(DLSounds.EVENT_TOWN_ENTER, 1.0F, 0.85F);
		}
		float t = (Util.getMillis() - startedAt) / 1000.0F;
		if (t > HOLD_UNTIL + FADE_OUT) {
			card = null;
			return;
		}
		float alpha = t < FADE_IN ? t / FADE_IN : t > HOLD_UNTIL ? 1.0F - (t - HOLD_UNTIL) / FADE_OUT : 1.0F;
		alpha = Mth.clamp(alpha, 0.0F, 1.0F);
		alpha = alpha * alpha * (3.0F - 2.0F * alpha);
		if (alpha <= 0.02F) {
			return;
		}

		Font font = minecraft.font;
		int cx = graphics.guiWidth() / 2;
		int y = (int) (graphics.guiHeight() * 0.16F);
		int text = DLTheme.withAlpha(DLTheme.TEXT, alpha);
		int dim = DLTheme.withAlpha(DLTheme.TEXT, alpha * 0.72F);

		Component overline = Component.translatable(town.firstDiscovery() ? "hud.deadlands.town.discovered" : "hud.deadlands.town.entering");
		graphics.drawCenteredString(font, overline, cx, y, dim);
		y += 12;

		int strokeW = 260;
		float reveal = Mth.clamp(t / 0.8F, 0.0F, 1.0F);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, DLTheme.DAY_STROKE, cx - strokeW / 2, y - 6,
			Math.max(1, (int) (strokeW * reveal)), 38, ARGB.white(alpha * 0.9F));
		Component name = Component.literal(town.name());
		float scale = 2.4F;
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(cx - font.width(name) * scale / 2.0F, y);
		pose.scale(scale, scale);
		graphics.drawString(font, name, 0, 0, text, true);
		pose.popMatrix();
		y += 30;

		Component summary = Component.translatable("hud.deadlands.town.summary",
			Component.translatable("town.deadlands.size." + town.size()),
			Component.translatable("town.deadlands.condition." + town.condition()),
			String.format(Locale.ROOT, "%,d", town.population()));
		graphics.drawCenteredString(font, summary, cx, y, text);
		y += 14;

		y = wrapped(graphics, font, Component.translatable(town.historyKey(), town.name()), cx, y, text);
		y += 4;
		List<Component> facts = new ArrayList<>();
		facts.add(Component.translatable("hud.deadlands.town.held_by", Component.translatable(town.factionKey(), town.factionArg())));
		if (!town.landmarkKey().isEmpty()) {
			facts.add(Component.translatable("hud.deadlands.town.landmark", Component.translatable(town.landmarkKey())));
		}
		facts.add(Component.translatable("hud.deadlands.town.supplies", Component.translatable(town.lootKey())));
		if (town.powered()) {
			facts.add(Component.translatable("hud.deadlands.town.powered"));
		}
		for (Component fact : facts) {
			graphics.drawCenteredString(font, fact, cx, y, dim);
			y += 11;
		}
		y += 3;
		wrapped(graphics, font, Component.translatable(town.storyKey()).withStyle(style -> style.withItalic(true)), cx, y, dim);
	}

	private static int wrapped(GuiGraphics graphics, Font font, Component line, int cx, int y, int colour) {
		for (FormattedCharSequence part : font.split(line, WRAP)) {
			graphics.drawCenteredString(font, part, cx, y, colour);
			y += 10;
		}
		return y;
	}
}
