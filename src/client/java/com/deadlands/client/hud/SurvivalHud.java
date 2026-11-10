package com.deadlands.client.hud;

import com.deadlands.Deadlands;
import com.deadlands.client.ui.DLTheme;
import com.deadlands.config.ConfigManager;
import com.deadlands.network.SurvivalPayload;
import com.deadlands.survival.Condition;
import com.deadlands.survival.LocalSurvival;
import com.deadlands.survival.SurvivalData;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/**
 * The survivor's body on screen, bottom left: five small meters (thirst, stamina, temperature,
 * fatigue, load) and, above them, whatever is wrong right now (bleeding, infected, fracture...), each
 * with its icon. A condition that has just appeared also says what to do about it for a few
 * seconds. A meter is drawn dim while it is fine and lights up when it needs attention, so a
 * healthy survivor's HUD stays quiet. Setting: {@code ui.survivalHud}.
 */
public final class SurvivalHud {
	private static final int BAR_W = 46;
	private static final int ROW_H = 11;
	private static final long HINT_MS = 8000L;
	private static final Identifier ICON_THIRST = Deadlands.id("survival/thirst");
	private static final Identifier ICON_STAMINA = Deadlands.id("survival/stamina");
	private static final Identifier ICON_TEMPERATURE = Deadlands.id("survival/temperature");
	private static final Identifier ICON_FATIGUE = Deadlands.id("survival/fatigue");
	private static final Identifier ICON_LOAD = Deadlands.id("survival/load");
	private static final int WATER = 0xFF5A9CC8;
	private static final int COLD = 0xFF7FB4E0;

	private static final Map<Condition, Long> SEEN = new EnumMap<>(Condition.class);

	private SurvivalHud() {
	}

	public static void reset() {
		SEEN.clear();
	}

	public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
		SurvivalPayload body = LocalSurvival.get();
		Minecraft minecraft = Minecraft.getInstance();
		if (!body.active() || !ConfigManager.get().ui.survivalHud || minecraft.options.hideGui || minecraft.player == null) {
			return;
		}
		Font font = minecraft.font;
		int x = 6;
		int y = graphics.guiHeight() - 6 - ROW_H * 5;
		var survival = ConfigManager.get().survival;
		if (survival.thirst) {
			meter(graphics, x, y, ICON_THIRST, body.thirst() / SurvivalData.MAX, 1.0F, body.thirst() < 25.0F ? DLTheme.DANGER : WATER, body.thirst() < 50.0F);
			y += ROW_H;
		}
		if (survival.stamina) {
			boolean winded = (body.conditions() & Condition.WINDED.bit()) != 0;
			meter(graphics, x, y, ICON_STAMINA, body.stamina() / SurvivalData.MAX, body.maxStamina() / SurvivalData.MAX,
				winded ? DLTheme.DANGER : DLTheme.ACCENT, body.stamina() < body.maxStamina() - 1.0F);
			y += ROW_H;
		}
		if (survival.temperature) {
			temperature(graphics, x, y, body.temperature());
			y += ROW_H;
		}
		if (survival.sleep) {
			meter(graphics, x, y, ICON_FATIGUE, 1.0F - body.fatigue() / SurvivalData.MAX, 1.0F, body.fatigue() >= 65.0F ? DLTheme.DANGER : DLTheme.TEXT_DIM,
				body.fatigue() >= 50.0F);
			y += ROW_H;
		}
		if (survival.weight) {
			float share = body.load() / Math.max(1.0F, body.capacity());
			meter(graphics, x, y, ICON_LOAD, Math.min(1.0F, share), 1.0F, share > 1.0F ? DLTheme.DANGER : DLTheme.TEXT_DIM, share >= 0.8F);
		}
		conditions(graphics, font, x, graphics.guiHeight() - 10 - ROW_H * 5, body.conditions());
	}

	/** Icon and bar. {@code cap} below 1 shades the part of the bar that is out of reach (fatigue caps stamina). */
	private static void meter(GuiGraphics graphics, int x, int y, Identifier icon, float value, float cap, int colour, boolean lit) {
		float alpha = lit ? 1.0F : 0.45F;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, icon, x, y, 9, 9, DLTheme.withAlpha(0xFFFFFFFF, alpha));
		int bx = x + 12;
		graphics.fill(bx - 1, y + 2, bx + BAR_W + 1, y + 7, DLTheme.withAlpha(0xFF000000, 0.55F * alpha + 0.2F));
		int filled = Math.round(BAR_W * Mth.clamp(value, 0.0F, 1.0F));
		graphics.fill(bx, y + 3, bx + filled, y + 6, DLTheme.withAlpha(colour, alpha));
		if (cap < 0.995F) {
			graphics.fill(bx + Math.round(BAR_W * Mth.clamp(cap, 0.0F, 1.0F)), y + 3, bx + BAR_W, y + 6, DLTheme.withAlpha(DLTheme.INK_RED, 0.8F));
		}
	}

	/** A centred scale: the marker sits in the middle when comfortable, left when cold, right when hot. */
	private static void temperature(GuiGraphics graphics, int x, int y, float temperature) {
		boolean lit = Math.abs(temperature) >= 30.0F;
		float alpha = lit ? 1.0F : 0.45F;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ICON_TEMPERATURE, x, y, 9, 9, DLTheme.withAlpha(0xFFFFFFFF, alpha));
		int bx = x + 12;
		graphics.fill(bx - 1, y + 2, bx + BAR_W + 1, y + 7, DLTheme.withAlpha(0xFF000000, 0.55F * alpha + 0.2F));
		int mid = bx + BAR_W / 2;
		int marker = mid + Math.round(BAR_W / 2.0F * Mth.clamp(temperature / SurvivalData.MAX, -1.0F, 1.0F));
		int colour = temperature <= -30.0F ? COLD : temperature >= 30.0F ? DLTheme.DANGER : DLTheme.TEXT_DIM;
		graphics.fill(Math.min(mid, marker), y + 3, Math.max(mid, marker) + 1, y + 6, DLTheme.withAlpha(colour, alpha));
		graphics.fill(mid, y + 2, mid + 1, y + 7, DLTheme.withAlpha(DLTheme.TEXT, 0.6F * alpha));
	}

	private static void conditions(GuiGraphics graphics, Font font, int x, int bottom, int flags) {
		long now = Util.getMillis();
		int y = bottom - 10;
		for (Condition condition : Condition.values()) {
			if ((flags & condition.bit()) == 0) {
				SEEN.remove(condition);
				continue;
			}
			long since = now - SEEN.computeIfAbsent(condition, c -> now);
			int colour = condition.serious() ? DLTheme.DANGER : DLTheme.TEXT;
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Deadlands.id("survival/" + condition.id()), x, y, 9, 9);
			Component name = Component.translatable(condition.translationKey());
			graphics.drawString(font, name, x + 12, y + 1, colour, true);
			if (since < HINT_MS) {
				float alpha = Mth.clamp((HINT_MS - since) / 1500.0F, 0.0F, 1.0F);
				if (alpha > 0.05F) {
					graphics.drawString(font, Component.translatable(condition.translationKey() + ".hint"), x + 16 + font.width(name), y + 1,
						DLTheme.withAlpha(DLTheme.TEXT_DIM, alpha), true);
				}
			}
			y -= 10;
		}
	}
}
