package com.deadlands.client.weather;

import com.deadlands.client.cutscene.CutscenePlayer;
import com.deadlands.config.ConfigManager;
import com.deadlands.network.DisasterPayload;
import com.deadlands.world.disaster.Disaster;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * The disaster as this survivor feels it (the server does the rest: {@code world.disaster.DisasterDirector}).
 * The tremor shakes the view in waves, scaled by {@code horror.cameraShake} (0 turns it off).
 */
public final class ClientDisaster {
	private static final RandomSource RANDOM = RandomSource.create();
	private static @Nullable Disaster current;
	private static boolean warning;
	private static float strength;
	private static int age;

	private ClientDisaster() {
	}

	public static void update(DisasterPayload payload) {
		Disaster next = Disaster.byIndex(payload.disaster());
		if (next != current) {
			age = 0;
		}
		current = next;
		warning = payload.warning();
		strength = payload.strength();
	}

	public static @Nullable Disaster current() {
		return warning ? null : current;
	}

	public static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null) {
			current = null;
			return;
		}
		age++;
		if (current != Disaster.TREMOR || warning || CutscenePlayer.active() || minecraft.isPaused()) {
			return;
		}
		float shake = (float) ConfigManager.get().horror.cameraShake;
		boolean wave = Mth.sin(age * 0.06F) > -0.2F;
		if (shake <= 0.0F || !wave || !player.onGround()) {
			return;
		}
		float amount = strength * shake;
		player.setXRot(Mth.clamp(player.getXRot() + (RANDOM.nextFloat() - 0.5F) * 1.6F * amount, -90.0F, 90.0F));
		player.setYRot(player.getYRot() + (RANDOM.nextFloat() - 0.5F) * 1.8F * amount);
	}
}
