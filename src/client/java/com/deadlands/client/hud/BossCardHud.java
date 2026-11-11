package com.deadlands.client.hud;

import com.deadlands.client.ui.DLTheme;
import com.deadlands.network.BossEventPayload;
import com.deadlands.registry.DLSounds;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2fStack;

/**
 * A boss's title card: its name large across the upper screen with what it is beneath ("What came out
 * of Ward C"), fading in and out over six seconds, with the boss sting. Also shown, smaller, when one
 * is defeated or retreats at dawn.
 */
public final class BossCardHud {
	private static final int SHOW_TICKS = 120;
	private static Component title = Component.empty();
	private static Component subtitle = Component.empty();
	private static int colour = DLTheme.DANGER;
	private static int ticks = SHOW_TICKS;

	private BossCardHud() {
	}

	public static void handle(BossEventPayload payload) {
		Component name = Component.translatable("boss.deadlands." + payload.kind());
		switch (payload.event()) {
			case BossEventPayload.START -> {
				title = name;
				subtitle = payload.tier() > 0 ? Component.translatable("hud.deadlands.boss.tier", Component.translatable("boss.deadlands." + payload.kind() + ".subtitle"),
					payload.tier() + 1) : Component.translatable("boss.deadlands." + payload.kind() + ".subtitle");
				colour = DLTheme.DANGER;
				com.deadlands.client.ui.DLTheme.playSting(DLSounds.EVENT_BOSS, 1.0F, 1.0F);
			}
			case BossEventPayload.DEFEATED -> {
				title = Component.translatable("hud.deadlands.boss.defeated", name);
				subtitle = Component.translatable("hud.deadlands.boss.defeated.sub");
				colour = DLTheme.ACCENT;
			}
			default -> {
				title = Component.translatable("hud.deadlands.boss.retreated", name);
				subtitle = Component.translatable("hud.deadlands.boss.retreated.sub");
				colour = DLTheme.TEXT_DIM;
			}
		}
		ticks = 0;
	}

	/** A world event's card: what it is and where. */
	public static void event(com.deadlands.network.WorldEventPayload payload) {
		title = Component.translatable("hud.deadlands.event." + payload.kind());
		subtitle = Component.translatable("event.deadlands.where", payload.x(), payload.z());
		colour = DLTheme.ACCENT_LIGHT;
		ticks = 0;
	}

	public static void tick() {
		if (ticks < SHOW_TICKS) {
			ticks++;
		}
	}

	public static void reset() {
		ticks = SHOW_TICKS;
	}

	public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
		if (ticks >= SHOW_TICKS || Minecraft.getInstance().options.hideGui) {
			return;
		}
		float t = ticks + deltaTracker.getGameTimeDeltaPartialTick(false);
		float alpha = Mth.clamp(Math.min(t / 20.0F, (SHOW_TICKS - t) / 25.0F), 0.0F, 1.0F);
		if (alpha <= 0.02F) {
			return;
		}
		var font = Minecraft.getInstance().font;
		int cx = graphics.guiWidth() / 2;
		int y = graphics.guiHeight() / 4;
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(cx, y);
		pose.scale(3.0F, 3.0F);
		graphics.drawCenteredString(font, title, 0, 0, DLTheme.withAlpha(colour, alpha));
		pose.popMatrix();
		graphics.drawCenteredString(font, subtitle, cx, y + 32, DLTheme.withAlpha(DLTheme.TEXT, alpha));
	}
}
