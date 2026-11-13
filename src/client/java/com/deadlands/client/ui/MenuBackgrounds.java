package com.deadlands.client.ui;

import com.deadlands.Deadlands;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;

/**
 * The main menu's backgrounds: the owner's shader screenshots ({@code textures/gui/title/backgrounds/bg_*.png},
 * imported by {@code tools/assetgen/import_menu_backgrounds.py}) and the rat.
 *
 * <ul>
 *   <li>A new picture every {@link #HOLD_MS} (10 seconds), cross-faded over {@link #FADE_MS}.</li>
 *   <li>Each time the menu opens, the first picture is a random screenshot and the second is always the rat.
 *       After that the rat comes back often, about one change in four, but never twice in a row.</li>
 *   <li>A screenshot is never followed by itself.</li>
 *   <li>Each picture covers the screen at its own aspect ratio (cropped, never stretched) and drifts in very
 *       slowly, so the menu feels alive. Only the two pictures on screen are kept in video memory.</li>
 * </ul>
 */
public final class MenuBackgrounds {
	private static final String FOLDER = "textures/gui/title/backgrounds";
	public static final Identifier RAT = Deadlands.id(FOLDER + "/rat.png");
	private static final long HOLD_MS = 10_000L;
	private static final long FADE_MS = 1_400L;
	/** After this long without being drawn, the menu counts as opened anew. */
	private static final long REOPEN_MS = 2_000L;
	private static final float RAT_CHANCE = 0.26F;
	private static final int ART_W = 1920;
	private static final int ART_H = 1080;

	private static final Random RANDOM = new Random();
	private static @Nullable List<Identifier> shots;
	private static boolean ratPresent;
	private static @Nullable Identifier current;
	private static @Nullable Identifier previous;
	private static long shownAt;
	private static long lastDrawn;
	private static int shownThisVisit;

	private MenuBackgrounds() {
	}

	private static List<Identifier> shots() {
		if (shots == null) {
			List<Identifier> found = new ArrayList<>();
			boolean rat = false;
			for (Identifier id : Minecraft.getInstance().getResourceManager().listResources(FOLDER, path -> path.getPath().endsWith(".png")).keySet()) {
				if (id.equals(RAT)) {
					rat = true;
				} else if (id.getNamespace().equals(Deadlands.MOD_ID)) {
					found.add(id);
				}
			}
			found.sort(null);
			shots = List.copyOf(found);
			ratPresent = rat;
		}
		return shots;
	}

	/** Whether there is anything to show (the pictures are in the resources). */
	public static boolean available() {
		return !shots().isEmpty();
	}

	private static Identifier pick() {
		List<Identifier> pool = shots();
		if (ratPresent && shownThisVisit == 1) {
			return RAT; // the second picture is always the rat
		}
		if (ratPresent && shownThisVisit >= 2 && !RAT.equals(current) && RANDOM.nextFloat() < RAT_CHANCE) {
			return RAT;
		}
		if (pool.size() == 1) {
			return pool.getFirst();
		}
		Identifier next;
		do {
			next = pool.get(RANDOM.nextInt(pool.size()));
		} while (next.equals(current));
		return next;
	}

	private static void advance(long now) {
		TextureManager textures = Minecraft.getInstance().getTextureManager();
		if (previous != null && !previous.equals(current)) {
			textures.release(previous); // only the two on screen stay in video memory
		}
		previous = current;
		current = pick();
		shownAt = now;
		shownThisVisit++;
	}

	/** Draw the backgrounds over the whole screen. */
	public static void render(GuiGraphics graphics, int width, int height) {
		long now = Util.getMillis();
		if (now - lastDrawn > REOPEN_MS) {
			shownThisVisit = 0; // the menu was opened again: a screenshot first, then the rat
			previous = null;
			current = null;
		}
		lastDrawn = now;
		if (current == null || now - shownAt >= HOLD_MS) {
			advance(now);
		}
		graphics.fill(0, 0, width, height, 0xFF000000);
		float fade = previous == null ? 1.0F : Math.min(1.0F, (now - shownAt) / (float) FADE_MS);
		if (previous != null && fade < 1.0F) {
			draw(graphics, previous, width, height, now - shownAt + HOLD_MS, 1.0F);
		}
		draw(graphics, current, width, height, now - shownAt, fade * fade * (3.0F - 2.0F * fade));
	}

	private static void draw(GuiGraphics graphics, Identifier texture, int width, int height, long age, float alpha) {
		// Cover the screen without stretching, drifting in by 4% over the picture's time.
		float drift = 1.0F + 0.04F * Math.min(1.0F, age / (float) (HOLD_MS + FADE_MS));
		float scale = Math.max(width / (float) ART_W, height / (float) ART_H) * drift;
		float x = (width - ART_W * scale) / 2.0F;
		float y = (height - ART_H * scale) / 2.0F;
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(scale, scale);
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0, 0.0F, 0.0F, ART_W, ART_H, ART_W, ART_H, ARGB.white(alpha));
		pose.popMatrix();
	}

	/** Free the pictures (a world was joined). */
	public static void release() {
		TextureManager textures = Minecraft.getInstance().getTextureManager();
		if (current != null) {
			textures.release(current);
		}
		if (previous != null) {
			textures.release(previous);
		}
		current = null;
		previous = null;
	}
}
