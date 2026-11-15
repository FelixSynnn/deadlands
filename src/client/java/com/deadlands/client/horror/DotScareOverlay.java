package com.deadlands.client.horror;

import com.deadlands.Deadlands;
import com.deadlands.config.ConfigManager;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/**
 * What D.O.T.'s jumpscares put on the screen (the scare steps {@code dot_frame}, {@code dot_tear}, {@code dot_404}).
 *
 * <ul>
 *   <li><b>Wrong Frame</b>: his torn, hooded face fills the screen for a few frames.</li>
 *   <li><b>Road Ghost</b>: the picture tears: bright scan lines and sideways-shifted bands, for a second and a half.</li>
 *   <li><b>404 Death</b>: over the death screen, black, static, and "ERROR 404: SUBJECT NOT FOUND", for two seconds.</li>
 * </ul>
 * With Photosensitivity on ({@code horror.photosensitivity}, the reduce-flashes option), nothing flashes: the face fades
 * in and out at half strength, the tear becomes a slow dim wash, and the 404 fades instead of flickering.
 */
public final class DotScareOverlay {
	private static final Identifier FRAME = Deadlands.id("textures/gui/dot_wrong_frame.png");
	private static String kind = "";
	private static long until;
	private static long started;

	private DotScareOverlay() {
	}

	public static void init() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, Deadlands.id("dot_scare"), DotScareOverlay::render);
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof DeathScreen) {
				ScreenEvents.afterRender(screen).register((s, graphics, mouseX, mouseY, delta) -> draw(graphics, graphics.guiWidth(), graphics.guiHeight()));
			}
		});
	}

	/** A scare step arrived: show it for {@code ticks}. */
	public static void start(String type, int ticks) {
		kind = type;
		started = Util.getMillis();
		until = started + Math.max(1, ticks) * 50L;
	}

	private static void render(GuiGraphics graphics, DeltaTracker delta) {
		if (!(Minecraft.getInstance().screen instanceof DeathScreen)) {
			draw(graphics, graphics.guiWidth(), graphics.guiHeight());
		}
	}

	private static void draw(GuiGraphics g, int w, int h) {
		long now = Util.getMillis();
		if (now >= until || kind.isEmpty()) {
			return;
		}
		boolean gentle = ConfigManager.get().horror.photosensitivity;
		float life = Mth.clamp((now - started) / (float) Math.max(1L, until - started), 0.0F, 1.0F);
		float fade = gentle ? Mth.sin(life * Mth.PI) : 1.0F;
		switch (kind) {
			case "dot_frame" -> {
				float alpha = gentle ? 0.5F * fade : 1.0F;
				float scale = Math.max(w / 512.0F, h / 288.0F);
				int dw = Math.round(512 * scale);
				int dh = Math.round(288 * scale);
				g.blit(RenderPipelines.GUI_TEXTURED, FRAME, (w - dw) / 2, (h - dh) / 2, 0.0F, 0.0F, dw, dh, 512, 288, 512, 288, ARGB.white(alpha));
			}
			case "dot_tear" -> {
				if (gentle) {
					g.fill(0, 0, w, h, ARGB.color(Math.round(70 * fade), 10, 40, 20));
					return;
				}
				long frame = now / 40L;
				for (int i = 0; i < 9; i++) {
					long hsh = (frame * 31L + i * 17L) * 0x9E3779B97F4A7C15L;
					int y = (int) Math.floorMod(hsh >>> 20, Math.max(1, h));
					int band = 2 + (int) Math.floorMod(hsh >>> 40, 9L);
					int shift = (int) Math.floorMod(hsh >>> 8, 61L) - 30;
					g.fill(Math.min(0, shift), y, w + Math.max(0, shift), y + band, ARGB.color(110, 61, 255, 110));
					g.fill(0, y + band, w, y + band + 1, ARGB.color(160, 236, 255, 244));
				}
			}
			case "dot_404" -> {
				float alpha = gentle ? fade : (Math.floorMod(now / 60L, 7L) == 0 ? 0.75F : 0.94F);
				g.fill(0, 0, w, h, ARGB.color(Math.round(255 * alpha), 2, 4, 6));
				if (!gentle) {
					long frame = now / 50L;
					for (int i = 0; i < 40; i++) {
						long hsh = (frame * 131L + i * 7L) * 0x9E3779B97F4A7C15L;
						int y = (int) Math.floorMod(hsh >>> 16, Math.max(1, h));
						g.fill(0, y, w, y + 1, ARGB.color(50, 200, 220, 210));
					}
				}
				Minecraft mc = Minecraft.getInstance();
				Component line = Component.translatable("scare.deadlands.dot_404");
				Component sub = Component.translatable("scare.deadlands.dot_404.sub");
				int colour = ARGB.color(Math.round(255 * Math.max(0.3F, alpha)), 61, 255, 110);
				g.drawCenteredString(mc.font, line, w / 2, h / 2 - 10, colour);
				g.drawCenteredString(mc.font, sub, w / 2, h / 2 + 4, ARGB.color(Math.round(200 * Math.max(0.3F, alpha)), 236, 255, 244));
			}
			default -> {
			}
		}
	}
}
