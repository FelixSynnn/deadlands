package com.deadlands.client.light;

import net.minecraft.util.Mth;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Hard Rule 3: nights are near pitch black and nothing on the player's side lifts that. Applied to
 * the lightmap inputs ({@code LightTextureMixin}): the brightness slider and night vision are
 * ignored, and the sky's light fades to nothing at dusk (vanilla keeps about a quarter of it at
 * night, which is moonlight). By day the sky gives a little less light and less colour: overcast.
 * The lightmap shader's own floor of light is lowered too ({@code shaders/core/lightmap.fsh}), so an
 * unlit room is dark at any hour. Block light (lamps, fires, safehouses) is untouched.
 */
public final class Darkness {
	/** Sky light factor at or below which it counts as night (vanilla night is 0.24, day 1.0). */
	private static final float NIGHT = 0.30F;
	/** Full daylight is this much of vanilla's. */
	private static final float DAY = 0.85F;
	/** How much of the sky light's colour is kept by day. */
	private static final float SATURATION = 0.6F;

	private Darkness() {
	}

	public static float skyFactor(float vanilla) {
		float t = Mth.clamp((vanilla - NIGHT) / (1.0F - NIGHT - 0.05F), 0.0F, 1.0F);
		return t * t * (3.0F - 2.0F * t) * DAY;
	}

	public static Vector3fc overcast(Vector3fc color) {
		float grey = 0.299F * color.x() + 0.587F * color.y() + 0.114F * color.z();
		return new Vector3f(
			Mth.lerp(SATURATION, grey, color.x()),
			Mth.lerp(SATURATION, grey, color.y()),
			Mth.lerp(SATURATION, grey, color.z()));
	}
}
