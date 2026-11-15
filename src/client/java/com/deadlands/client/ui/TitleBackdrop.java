package com.deadlands.client.ui;

import com.deadlands.Deadlands;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;

/**
 * The DEADLANDS menu backdrop: a dead city street at sunset (tools/assetgen/gen_menu.py), drawn in
 * layers that shift with the mouse for depth: the sky with the low sun, the far skyline and a broken
 * overpass, the street's buildings, light shafts, the road with its wrecks, and foreground weeds.
 *
 * <p>It moves a little, never much: two fog banks drift across the street and a third lies on the
 * road, the light shafts slowly breathe, the weeds sway in the wind, and now and then a few crows
 * cross the sky. Vignette and film grain sit on top.
 *
 * <p>Used by the title screen and, through {@code ScreenMixin}, behind every pre-game menu that
 * would otherwise show the vanilla panorama.
 */
public final class TitleBackdrop {
	public static final Identifier SKY = Deadlands.id("textures/gui/title/sky.png");
	public static final Identifier FAR = Deadlands.id("textures/gui/title/skyline_far.png");
	public static final Identifier MID = Deadlands.id("textures/gui/title/skyline_mid.png");
	public static final Identifier NEAR = Deadlands.id("textures/gui/title/skyline_near.png");
	public static final Identifier PLANTS = Deadlands.id("textures/gui/title/plants.png");
	public static final Identifier RAYS = Deadlands.id("textures/gui/title/rays.png");
	public static final Identifier FOG = Deadlands.id("textures/gui/title/fog.png");
	public static final Identifier CROW = Deadlands.id("textures/gui/title/crow.png");
	public static final Identifier VIGNETTE = Deadlands.id("textures/gui/title/vignette.png");
	public static final Identifier GRAIN = Deadlands.id("textures/gui/title/grain.png");
	public static final Identifier LOGO = Deadlands.id("textures/gui/title/logo.png");
	public static final int LOGO_WIDTH = 1123;
	public static final int LOGO_HEIGHT = 250;
	private static final List<Identifier> ALL = List.of(SKY, FAR, MID, NEAR, PLANTS, RAYS, FOG, CROW, VIGNETTE, GRAIN, LOGO);

	private static final int ART_W = 1920;
	private static final int ART_H = 1080;
	private static final int FOG_W = 1024;
	private static final int FOG_H = 512;
	private static final int GRAIN_TILE = 128;
	private static final int CROW_SIZE = 32;
	/** Seconds between flights of crows across the sky, and how long one takes. */
	private static final float CROW_PERIOD = 38.0F;
	private static final float CROW_FLIGHT = 14.0F;

	private TitleBackdrop() {
	}

	/**
	 * @param mouseNX mouse x relative to screen centre, -1..1 (0 for no parallax)
	 * @param mouseNY mouse y relative to screen centre, -1..1
	 */
	public static void render(GuiGraphics graphics, int width, int height, float mouseNX, float mouseNY, boolean grain) {
		// The painted street is retired: the menu shows the shader screenshots (MenuBackgrounds).
		MenuBackgrounds.render(graphics, width, height);
		// Keep the menu readable over any picture: darker toward the edges and the bottom.
		graphics.fillGradient(0, height / 3, width, height, 0x00000000, 0xA0000000);
		graphics.fill(0, 0, width, height, 0x30000000);
		graphics.blit(RenderPipelines.GUI_TEXTURED, VIGNETTE, 0, 0, 0.0F, 0.0F, width, height, 512, 288, 512, 288);
		if (grain) {
			grain(graphics, width, height);
		}
	}

	/** The retired painted street (kept for reference; not drawn). */
	@SuppressWarnings("unused")
	private static void renderStreet(GuiGraphics graphics, int width, int height, float mouseNX, float mouseNY, boolean grain) {
		float t = (Util.getMillis() % 3_600_000L) / 1000.0F;
		float cover = Math.max(width / (float) ART_W, height / (float) ART_H) * 1.05F;
		float artW = ART_W * cover;
		float artH = ART_H * cover;
		float baseX = (width - artW) / 2.0F;
		float baseY = (height - artH) / 2.0F;

		graphics.fill(0, 0, width, height, 0xFF0B0C0E);
		layer(graphics, SKY, baseX - mouseNX * 2.0F + (float) Math.sin(t * 0.03F) * 3.0F, baseY - mouseNY * 1.0F, cover, -1);
		crows(graphics, t, baseX - mouseNX * 3.0F, baseY - mouseNY * 1.5F, cover);
		layer(graphics, FAR, baseX - mouseNX * 4.0F, baseY - mouseNY * 2.0F, cover, -1);
		fog(graphics, width, t * 4.0F, height * 0.36F, height * 0.40F, 0.55F);
		layer(graphics, MID, baseX - mouseNX * 7.0F, baseY - mouseNY * 3.0F, cover, -1);
		// The light shafts breathe slowly, as if clouds were crossing the sun.
		float breathe = 0.26F + 0.08F * (float) Math.sin(t * 0.35F) + 0.04F * (float) Math.sin(t * 0.93F + 1.7F);
		layer(graphics, RAYS, baseX - mouseNX * 5.0F, baseY - mouseNY * 2.5F, cover, ARGB.white(breathe));
		fog(graphics, width, t * 9.0F + 380.0F, height * 0.44F, height * 0.36F, 0.42F);
		layer(graphics, NEAR, baseX - mouseNX * 11.0F, baseY - mouseNY * 4.0F, cover, -1);
		plants(graphics, t, baseX - mouseNX * 15.0F, baseY - mouseNY * 5.0F, cover);
		fog(graphics, width, t * 15.0F + 820.0F, height * 0.66F, height * 0.30F, 0.22F);
		graphics.blit(RenderPipelines.GUI_TEXTURED, VIGNETTE, 0, 0, 0.0F, 0.0F, width, height, 512, 288, 512, 288);
		if (grain) {
			grain(graphics, width, height);
		}
	}

	private static void layer(GuiGraphics graphics, Identifier texture, float x, float y, float scale, int colour) {
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(scale, scale);
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0, 0.0F, 0.0F, ART_W, ART_H, ART_W, ART_H, colour);
		pose.popMatrix();
	}

	/** The weeds lean with the wind: a shear that grows toward their tips, in two slow gusts. */
	private static void plants(GuiGraphics graphics, float t, float x, float y, float scale) {
		float sway = 0.018F * (float) Math.sin(t * 0.8F) + 0.009F * (float) Math.sin(t * 2.1F + 0.6F) + 0.012F;
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(scale, scale);
		// x' = x + sway * (y - bottom): the bottom stays put, the tips move.
		pose.mul(new Matrix3x2f(1.0F, 0.0F, sway, 1.0F, -sway * ART_H, 0.0F));
		graphics.blit(RenderPipelines.GUI_TEXTURED, PLANTS, 0, 0, 0.0F, 0.0F, ART_W, ART_H, ART_W, ART_H, -1);
		pose.popMatrix();
	}

	/** Now and then a few crows cross the sky from right to left, wings beating out of step. */
	private static void crows(GuiGraphics graphics, float t, float x, float y, float scale) {
		float cycle = t % CROW_PERIOD;
		if (cycle > CROW_FLIGHT) {
			return;
		}
		long flight = (long) (t / CROW_PERIOD);
		float progress = cycle / CROW_FLIGHT;
		Matrix3x2fStack pose = graphics.pose();
		for (int i = 0; i < 4; i++) {
			long h = (flight * 31L + i * 17L) * 0x9E3779B97F4A7C15L;
			float lane = 120.0F + ((h >>> 40) & 0xFF) * 1.2F + i * 26.0F;
			float lag = i * 0.045F + ((h >>> 20) & 0xF) / 160.0F;
			float px = ART_W * (1.08F - (progress - lag) * 1.25F);
			float py = lane + (float) Math.sin((progress + i) * 6.0F) * 6.0F;
			if (px < -60 || px > ART_W + 60) {
				continue;
			}
			int frame = (int) ((t * 7.0F + i * 1.3F) % 4.0F);
			float size = 0.55F + 0.12F * (i % 3);
			pose.pushMatrix();
			pose.translate(x + px * scale, y + py * scale);
			pose.scale(scale * size, scale * size);
			// the sheet's crow faces right; these fly left
			pose.scale(-1.0F, 1.0F);
			graphics.blit(RenderPipelines.GUI_TEXTURED, CROW, -CROW_SIZE / 2, -CROW_SIZE / 2, frame * CROW_SIZE, 0.0F, CROW_SIZE, CROW_SIZE,
				CROW_SIZE * 4, CROW_SIZE, ARGB.white(0.85F));
			pose.popMatrix();
		}
	}

	private static void fog(GuiGraphics graphics, int width, float scroll, float top, float bandHeight, float alpha) {
		float scale = bandHeight / FOG_H;
		float tileW = FOG_W * scale;
		float offset = scroll % tileW;
		Matrix3x2fStack pose = graphics.pose();
		for (float x = -offset; x < width; x += tileW) {
			pose.pushMatrix();
			pose.translate(x, top);
			pose.scale(scale, scale);
			graphics.blit(RenderPipelines.GUI_TEXTURED, FOG, 0, 0, 0.0F, 0.0F, FOG_W, FOG_H, FOG_W, FOG_H, ARGB.white(alpha));
			pose.popMatrix();
		}
	}

	private static void grain(GuiGraphics graphics, int width, int height) {
		// Re-roll the grain 24 times a second, like film, independent of frame rate.
		long frame = Util.getMillis() / 42L;
		long mix = frame * 0x9E3779B97F4A7C15L;
		int ox = (int) Math.floorMod(mix >>> 17, GRAIN_TILE);
		int oy = (int) Math.floorMod(mix >>> 37, GRAIN_TILE);
		int tint = ARGB.white(0.075F);
		for (int y = -oy; y < height; y += GRAIN_TILE) {
			for (int x = -ox; x < width; x += GRAIN_TILE) {
				graphics.blit(RenderPipelines.GUI_TEXTURED, GRAIN, x, y, 0.0F, 0.0F, GRAIN_TILE, GRAIN_TILE, 256, 256, 256, 256, tint);
			}
		}
	}

	/** Free the large backdrop textures (about 50 MB of video memory) once a world is joined. */
	public static void releaseTextures() {
		MenuBackgrounds.release();
		TextureManager textures = Minecraft.getInstance().getTextureManager();
		for (Identifier id : ALL) {
			textures.release(id);
		}
	}
}
