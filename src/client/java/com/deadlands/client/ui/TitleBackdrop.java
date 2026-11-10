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
import org.joml.Matrix3x2fStack;

/**
 * The layered DEADLANDS menu backdrop: overcast sky, far skyline, drifting fog, near ruins,
 * a second fog bank, vignette and film grain. Layers shift with the mouse for parallax.
 *
 * <p>Used by the title screen and, through {@code ScreenMixin}, behind every pre-game menu that
 * would otherwise show the vanilla panorama.
 */
public final class TitleBackdrop {
	public static final Identifier SKY = Deadlands.id("textures/gui/title/sky.png");
	public static final Identifier FAR = Deadlands.id("textures/gui/title/skyline_far.png");
	public static final Identifier NEAR = Deadlands.id("textures/gui/title/skyline_near.png");
	public static final Identifier FOG = Deadlands.id("textures/gui/title/fog.png");
	public static final Identifier VIGNETTE = Deadlands.id("textures/gui/title/vignette.png");
	public static final Identifier GRAIN = Deadlands.id("textures/gui/title/grain.png");
	public static final Identifier LOGO = Deadlands.id("textures/gui/title/logo.png");
	public static final int LOGO_WIDTH = 1123;
	public static final int LOGO_HEIGHT = 250;
	private static final List<Identifier> ALL = List.of(SKY, FAR, NEAR, FOG, VIGNETTE, GRAIN, LOGO);

	private static final int ART_W = 1920;
	private static final int ART_H = 1080;
	private static final int FOG_W = 1024;
	private static final int FOG_H = 512;
	private static final int GRAIN_TILE = 128;

	private TitleBackdrop() {
	}

	/**
	 * @param mouseNX mouse x relative to screen centre, -1..1 (0 for no parallax)
	 * @param mouseNY mouse y relative to screen centre, -1..1
	 */
	public static void render(GuiGraphics graphics, int width, int height, float mouseNX, float mouseNY, boolean grain) {
		float t = (Util.getMillis() % 3_600_000L) / 1000.0F;
		float cover = Math.max(width / (float) ART_W, height / (float) ART_H) * 1.05F;
		float artW = ART_W * cover;
		float artH = ART_H * cover;
		float baseX = (width - artW) / 2.0F;
		float baseY = (height - artH) / 2.0F;

		graphics.fill(0, 0, width, height, 0xFF0B0E10);
		layer(graphics, SKY, baseX - mouseNX * 2.0F + (float) Math.sin(t * 0.03F) * 3.0F, baseY - mouseNY * 1.0F, cover);
		layer(graphics, FAR, baseX - mouseNX * 5.0F, baseY - mouseNY * 2.0F, cover);
		fog(graphics, width, t * 5.0F, height * 0.40F, height * 0.62F, 0.80F);
		layer(graphics, NEAR, baseX - mouseNX * 11.0F, baseY - mouseNY * 4.0F, cover);
		fog(graphics, width, t * 13.0F + 400.0F, height * 0.58F, height * 0.52F, 0.42F);
		graphics.blit(RenderPipelines.GUI_TEXTURED, VIGNETTE, 0, 0, 0.0F, 0.0F, width, height, 512, 288, 512, 288);
		if (grain) {
			grain(graphics, width, height);
		}
	}

	private static void layer(GuiGraphics graphics, Identifier texture, float x, float y, float scale) {
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(scale, scale);
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0, 0.0F, 0.0F, ART_W, ART_H, ART_W, ART_H, -1);
		pose.popMatrix();
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

	/** Free the large backdrop textures (about 30 MB of video memory) once a world is joined. */
	public static void releaseTextures() {
		TextureManager textures = Minecraft.getInstance().getTextureManager();
		for (Identifier id : ALL) {
			textures.release(id);
		}
	}
}
