package com.deadlands.client.light;

import com.deadlands.item.FlashlightItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * The flashlight's beam, as the shaders see it. Once a frame the values below are appended to the
 * shared {@code Globals} uniform block ({@code GlobalSettingsUniformMixin}) as
 * {@code DeadlandsFlashlight}: strength, the cosines of the inner and outer half-angles of the cone,
 * and its range in blocks. The terrain and entity shaders add light inside that cone, centred on
 * the view and fading with distance: a true cone, not a glow around the player.
 *
 * <p>Below a tenth of its charge the beam weakens and stutters, a warning before it dies.
 */
public final class FlashlightClient {
	private static final float COS_INNER = (float) Math.cos(Math.toRadians(10.0));
	private static final float COS_OUTER = (float) Math.cos(Math.toRadians(23.0));
	private static final float RANGE = 26.0F;
	private static final float[] VALUES = new float[4];

	private FlashlightClient() {
	}

	/** Strength, cos inner, cos outer, range; strength 0 when no flashlight is shining. */
	public static float[] uniform() {
		VALUES[0] = strength();
		VALUES[1] = COS_INNER;
		VALUES[2] = COS_OUTER;
		VALUES[3] = RANGE;
		return VALUES;
	}

	private static float strength() {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null || player.isSpectator()) {
			return 0.0F;
		}
		ItemStack stack = FlashlightItem.shining(player.getMainHandItem()) ? player.getMainHandItem()
			: FlashlightItem.shining(player.getOffhandItem()) ? player.getOffhandItem() : ItemStack.EMPTY;
		if (stack.isEmpty()) {
			return 0.0F;
		}
		float charge = FlashlightItem.charge(stack) / (float) FlashlightItem.MAX_CHARGE;
		float scare = com.deadlands.client.horror.ScarePlayer.flashlightFactor(); // a scare can make it flicker or die
		if (charge >= 0.1F) {
			return scare;
		}
		// Running down: dimmer, with short stutters that come more often as it empties.
		long tick = minecraft.level.getGameTime();
		long hash = (tick / 2) * 0x9E3779B97F4A7C15L;
		hash ^= hash >>> 29;
		int odds = 6 + Math.round(charge * 200.0F);
		boolean stutter = Math.floorMod(hash, odds) == 0;
		return (stutter ? 0.12F : 0.5F + charge * 5.0F) * scare;
	}
}
