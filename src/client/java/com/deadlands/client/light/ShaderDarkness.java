package com.deadlands.client.light;

import com.deadlands.Deadlands;
import com.deadlands.config.ConfigManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;

/**
 * Darkness and the flashlight beam that work with shader packs (Hard Rules 3 and 4).
 *
 * <p>DEADLANDS' own darkness and flashlight cone live in Minecraft's core shaders and lightmap. Shader
 * packs (Iris: Photon, Complementary, BSL...) replace those shaders, and Sodium replaces the terrain
 * shader, so neither the dark nights nor the cone survive. This draws them on the screen instead,
 * after the world (and any pack) has been drawn, so no pack can undo it: the screen darkens as far as
 * the light where the survivor stands is missing (night under the sky, or an unlit room), and a
 * shining flashlight opens a soft beam in the middle of the view. With Lamb's Dynamic Lights
 * installed the flashlight also really lights the blocks around (assets/deadlands/dynamiclights).
 *
 * <p>On by default only when a shader pack is in use or Sodium is loaded ({@code horror.shaderCompatibility});
 * without them the core-shader darkness and cone are used as before.
 */
public final class ShaderDarkness {
	private static final Identifier MASK = Deadlands.id("textures/gui/flashlight_mask.png");
	private static final int MASK_SIZE = 256;
	private static Boolean sodium;
	private static long checkedAt = -1000;
	private static boolean shaders;
	private static float shown;

	private ShaderDarkness() {
	}

	/** Whether the screen-space darkness is in charge (a shader pack in use, or Sodium loaded). */
	public static boolean active() {
		if (!ConfigManager.get().horror.shaderCompatibility) {
			return false;
		}
		if (sodium == null) {
			sodium = FabricLoader.getInstance().isModLoaded("sodium");
		}
		return sodium || shaderPackInUse();
	}

	/** Iris's public API, read by reflection (no dependency): is a shader pack in use? Checked once a second. */
	private static boolean shaderPackInUse() {
		long now = System.currentTimeMillis();
		if (now - checkedAt < 1000) {
			return shaders;
		}
		checkedAt = now;
		try {
			Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
			Object instance = api.getMethod("getInstance").invoke(null);
			shaders = (Boolean) api.getMethod("isShaderPackInUse").invoke(instance);
		} catch (ReflectiveOperationException | ClassCastException e) {
			shaders = false;
		}
		return shaders;
	}

	/** 0 (enough light here) to 1 (night under the sky, or an unlit room). */
	private static float darkness(Minecraft minecraft, LocalPlayer player) {
		BlockPos eye = BlockPos.containing(player.getEyePosition());
		float sky = minecraft.level.getBrightness(LightLayer.SKY, eye) / 15.0F;
		float block = minecraft.level.getBrightness(LightLayer.BLOCK, eye) / 15.0F;
		long time = Math.floorMod(minecraft.level.getDayTime(), 24000L);
		// Daylight: full from dawn to dusk, fading over about a minute and a half either side.
		float day;
		if (time < 12000L) {
			day = 1.0F;
		} else if (time < 13800L) {
			day = 1.0F - (time - 12000L) / 1800.0F;
		} else if (time < 22200L) {
			day = 0.0F;
		} else {
			day = (time - 22200L) / 1800.0F;
		}
		float light = Math.max(sky * day * (1.0F - 0.6F * minecraft.level.getRainLevel(1.0F) * 0.5F), block);
		return Mth.clamp(1.0F - light / 0.6F, 0.0F, 1.0F);
	}

	public static void render(GuiGraphics graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null || !active() || player.isSpectator() || player.isCreative()) {
			shown = 0.0F;
			return;
		}
		shown += (darkness(minecraft, player) - shown) * 0.05F;
		float dark = shown * 0.9F; // the faintest silhouettes stay (Hard Rule 3)
		if (dark < 0.01F) {
			return;
		}
		int w = graphics.guiWidth();
		int h = graphics.guiHeight();
		float beam = FlashlightClient.uniform()[0];
		if (beam <= 0.01F) {
			graphics.fill(0, 0, w, h, ARGB.color(Math.round(dark * 255.0F), 0, 0, 0));
			return;
		}
		// Inside the beam a little darkness stays (more as the battery fades); outside it, the full dark.
		float inside = dark * (1.0F - 0.9F * Mth.clamp(beam, 0.0F, 1.0F));
		float ring = 1.0F - (1.0F - dark) / Math.max(0.001F, 1.0F - inside);
		graphics.fill(0, 0, w, h, ARGB.color(Math.round(inside * 255.0F), 0, 0, 0));
		int size = Math.round(h * 1.7F);
		int x = (w - size) / 2;
		int y = (h - size) / 2;
		int ringColour = ARGB.color(Math.round(Mth.clamp(ring, 0.0F, 1.0F) * 255.0F), 0, 0, 0);
		graphics.blit(RenderPipelines.GUI_TEXTURED, MASK, x, y, 0.0F, 0.0F, size, size, MASK_SIZE, MASK_SIZE, MASK_SIZE, MASK_SIZE, ringColour);
		// Outside the mask's square, the same darkness.
		graphics.fill(0, 0, w, Math.max(0, y), ringColour);
		graphics.fill(0, y + size, w, h, ringColour);
		graphics.fill(0, Math.max(0, y), Math.max(0, x), Math.min(h, y + size), ringColour);
		graphics.fill(x + size, Math.max(0, y), w, Math.min(h, y + size), ringColour);
	}
}
