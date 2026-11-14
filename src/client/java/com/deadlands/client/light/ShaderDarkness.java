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
 * <p>The whole night is drawn here when a shader pack is in use or Sodium is loaded ({@code horror.shaderCompatibility}).
 * With the flashlight on in the dark it is drawn in every case, so outside the beam is black
 * ({@code horror.flashlightDarkness}) with or without shaders, the cutout following the beam's real cone and the field
 * of view, its edge as soft as {@code horror.flashlightConeSoftness}. The flashlight's own light blocks don't count as
 * light when deciding how dark it is; daylight lifts it all.
 */
public final class ShaderDarkness {
	/** Soft-edged cone cutouts, hardest (0) to softest (4): transparent inside, black outside the beam. */
	private static final Identifier[] CONES = {Deadlands.id("textures/gui/flashlight_cone_0.png"), Deadlands.id("textures/gui/flashlight_cone_1.png"),
		Deadlands.id("textures/gui/flashlight_cone_2.png"), Deadlands.id("textures/gui/flashlight_cone_3.png"), Deadlands.id("textures/gui/flashlight_cone_4.png")};
	private static final int MASK_SIZE = 256;
	/** The beam's outer half-angle (FlashlightClient's cone): wide, so the light fills most of the screen. */
	private static final double CONE_DEGREES = 42.0;
	private static long ownLightAt = Long.MIN_VALUE;
	private static int ownLight;
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
		// The flashlight's own invisible light blocks (FlashlightBeam: the beam's spot and the light that goes with the
		// holder) must not count, or switching it on would read as "lit" and lift the dark everywhere.
		int blockLevel = minecraft.level.getBrightness(LightLayer.BLOCK, eye);
		if (blockLevel <= ownLight(minecraft, eye)) {
			blockLevel = 0;
		}
		float block = blockLevel / 15.0F;
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

	/** The strongest light our flashlight's light blocks give at {@code eye} (checked once a tick). */
	private static int ownLight(Minecraft minecraft, BlockPos eye) {
		long now = minecraft.level.getGameTime();
		if (now == ownLightAt) {
			return ownLight;
		}
		ownLightAt = now;
		int best = 0;
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int dx = -8; dx <= 8; dx++) {
			for (int dy = -8; dy <= 8; dy++) {
				for (int dz = -8; dz <= 8; dz++) {
					int far = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
					if (far >= 15) {
						continue;
					}
					at.set(eye.getX() + dx, eye.getY() + dy, eye.getZ() + dz);
					net.minecraft.world.level.block.state.BlockState state = minecraft.level.getBlockState(at);
					if (state.is(net.minecraft.world.level.block.Blocks.LIGHT)) {
						best = Math.max(best, state.getLightEmission() - far);
					}
				}
			}
		}
		ownLight = best;
		return best;
	}

	public static void render(GuiGraphics graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		float beam = player == null ? 0.0F : FlashlightClient.uniform()[0];
		// With a shader pack or Sodium the whole night is drawn here; and with the flashlight on in the dark it always
		// is, so only its beam shows, with or without shaders.
		if (player == null || minecraft.level == null || !active() && beam <= 0.01F || player.isSpectator() || player.isCreative()) {
			shown = 0.0F;
			return;
		}
		shown += (darkness(minecraft, player) - shown) * 0.05F;
		float dark = shown * (beam > 0.01F ? (float) ConfigManager.get().horror.flashlightDarkness : 0.9F); // without the beam the faintest silhouettes stay (Hard Rule 3)
		if (dark < 0.01F) {
			return;
		}
		int w = graphics.guiWidth();
		int h = graphics.guiHeight();
		if (beam <= 0.01F) {
			graphics.fill(0, 0, w, h, ARGB.color(Math.round(dark * 255.0F), 0, 0, 0));
			return;
		}
		// Inside the beam a little darkness stays (more as the battery fades); outside it, the full dark.
		float inside = dark * (1.0F - 0.9F * Mth.clamp(beam, 0.0F, 1.0F));
		float ring = 1.0F - (1.0F - dark) / Math.max(0.001F, 1.0F - inside);
		graphics.fill(0, 0, w, h, ARGB.color(Math.round(inside * 255.0F), 0, 0, 0));
		// The cutout matches the beam: its 23-degree edge projected through the field of view, the fade starting inside it.
		float softness = (float) Mth.clamp(ConfigManager.get().horror.flashlightConeSoftness, 0.0, 1.0);
		double halfFov = Math.toRadians(minecraft.options.fov().get() * 0.5);
		float radius = (float) (Math.tan(Math.toRadians(CONE_DEGREES)) / Math.tan(halfFov) * h * 0.5) * (1.0F + 0.25F * softness);
		int size = Math.max(8, Math.round(radius * 2.0F));
		int x = (w - size) / 2;
		int y = (h - size) / 2;
		int ringColour = ARGB.color(Math.round(Mth.clamp(ring, 0.0F, 1.0F) * 255.0F), 0, 0, 0);
		Identifier cone = CONES[Math.round(softness * (CONES.length - 1))];
		graphics.blit(RenderPipelines.GUI_TEXTURED, cone, x, y, 0.0F, 0.0F, size, size, MASK_SIZE, MASK_SIZE, MASK_SIZE, MASK_SIZE, ringColour);
		// Outside the mask's square, the same darkness.
		graphics.fill(0, 0, w, Math.max(0, y), ringColour);
		graphics.fill(0, y + size, w, h, ringColour);
		graphics.fill(0, Math.max(0, y), Math.max(0, x), Math.min(h, y + size), ringColour);
		graphics.fill(x + size, Math.max(0, y), w, Math.min(h, y + size), ringColour);
	}
}
