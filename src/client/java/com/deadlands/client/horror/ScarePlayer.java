package com.deadlands.client.horror;

import com.deadlands.client.cutscene.CutscenePlayer;
import com.deadlands.client.cutscene.CutsceneScreen;
import com.deadlands.client.ui.DialogueScreen;
import com.deadlands.client.ui.EndingScreen;
import com.deadlands.client.ui.IntroScreen;
import com.deadlands.config.ConfigManager;
import com.deadlands.network.HorrorBusyPayload;
import com.deadlands.network.HorrorScarePayload;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Performs the Horror Director's scares on this client (the server chooses them, see
 * {@code HorrorDirector}). Steps run at their tick:
 *
 * <ul>
 *   <li>{@code sound}: at the ear (a hand's width from one side of the head), behind, far off in a
 *       random direction, ahead, or everywhere at once;</li>
 *   <li>{@code silence}: ambience stops and music dips (the held breath before a payoff);</li>
 *   <li>{@code flicker} and {@code beam_off}: the flashlight stutters or dies for a while;</li>
 *   <li>{@code jolt}: the view flinches, scaled by {@code horror.cameraShake}.</li>
 * </ul>
 * With {@code horror.photosensitivity} on, flickers become a slow, gentle fade.
 *
 * <p>A scare that arrives while a screen or cutscene is open is dropped. This class also tells the
 * server what the survivor is looking at ({@link HorrorBusyPayload}) so it does not send one.
 */
public final class ScarePlayer {
	private static final RandomSource RANDOM = RandomSource.create();

	private record Due(int at, HorrorScarePayload.Step step) {
	}

	private static final List<Due> QUEUE = new ArrayList<>();
	private static int now;
	private static int silenceUntil;
	private static int flickerUntil;
	private static int beamOffUntil;
	private static int joltLeft;
	private static float joltStrength;
	private static int busy = -1;

	private ScarePlayer() {
	}

	public static void onScare(HorrorScarePayload payload) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.screen != null || CutscenePlayer.active() || minecraft.player == null) {
			return;
		}
		for (HorrorScarePayload.Step step : payload.steps()) {
			QUEUE.add(new Due(now + Math.max(0, step.at()), step));
		}
	}

	public static void tick(Minecraft minecraft) {
		reportBusy(minecraft);
		if (minecraft.player == null || minecraft.isPaused()) {
			return;
		}
		now++;
		Iterator<Due> it = QUEUE.iterator();
		List<HorrorScarePayload.Step> run = new ArrayList<>();
		while (it.hasNext()) {
			Due due = it.next();
			if (due.at() <= now) {
				run.add(due.step());
				it.remove();
			}
		}
		for (HorrorScarePayload.Step step : run) {
			perform(minecraft, step);
		}
		if (joltLeft > 0) {
			joltLeft--;
			LocalPlayer player = minecraft.player;
			float fall = joltLeft / 6.0F;
			player.setXRot(Mth.clamp(player.getXRot() + (RANDOM.nextFloat() - 0.7F) * 4.0F * joltStrength * fall, -90.0F, 90.0F));
			player.setYRot(player.getYRot() + (RANDOM.nextFloat() - 0.5F) * 5.0F * joltStrength * fall);
		}
	}

	private static void perform(Minecraft minecraft, HorrorScarePayload.Step step) {
		switch (step.type()) {
			case "sound" -> sound(minecraft, step);
			case "silence" -> silenceUntil = Math.max(silenceUntil, now + Math.round(step.amount()));
			case "flicker" -> flickerUntil = Math.max(flickerUntil, now + Math.round(step.amount()));
			case "beam_off" -> beamOffUntil = Math.max(beamOffUntil, now + Math.round(step.amount()));
			case "jolt" -> {
				float shake = (float) ConfigManager.get().horror.cameraShake;
				if (shake > 0.0F) {
					joltLeft = 6;
					joltStrength = Mth.clamp(step.amount(), 0.0F, 1.0F) * shake;
				}
			}
			default -> {
			}
		}
	}

	private static void sound(Minecraft minecraft, HorrorScarePayload.Step step) {
		Identifier id = Identifier.tryParse(step.sound());
		if (id == null || minecraft.player == null) {
			return;
		}
		if (step.where().equals("everywhere")) {
			minecraft.getSoundManager().play(new SimpleSoundInstance(id, SoundSource.AMBIENT, step.volume(), step.pitch(),
				SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true));
			return;
		}
		Vec3 eye = minecraft.gameRenderer.getMainCamera().position();
		Vec3 look = minecraft.player.getViewVector(1.0F);
		Vec3 flat = new Vec3(look.x, 0.0, look.z);
		flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
		Vec3 right = new Vec3(-flat.z, 0.0, flat.x);
		double distance = Math.max(0.5, step.distance());
		Vec3 at = switch (step.where()) {
			case "ear" -> eye.add(right.scale(RANDOM.nextBoolean() ? 0.7 : -0.7)).add(0.0, 0.1, 0.0);
			case "behind" -> eye.subtract(flat.scale(distance)).add(right.scale((RANDOM.nextFloat() - 0.5F) * 1.5));
			case "ahead" -> eye.add(look.scale(distance));
			default -> {
				double angle = RANDOM.nextDouble() * Math.PI * 2.0;
				yield eye.add(Math.cos(angle) * distance, (RANDOM.nextDouble() - 0.4) * 3.0, Math.sin(angle) * distance);
			}
		};
		minecraft.getSoundManager().play(new SimpleSoundInstance(id, SoundSource.AMBIENT, step.volume(), step.pitch(),
			SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.LINEAR, at.x, at.y, at.z, false));
	}

	/** Ambience holds its breath. */
	public static boolean silenced() {
		return now < silenceUntil;
	}

	/** How much music stays during a held breath. */
	public static float musicDuck() {
		return silenced() ? 0.15F : 1.0F;
	}

	/** The flashlight's strength multiplier: 0 while dead, stuttering (or slowly fading) while it flickers. */
	public static float flashlightFactor() {
		if (now < beamOffUntil) {
			return 0.0F;
		}
		if (now < flickerUntil) {
			if (ConfigManager.get().horror.photosensitivity) {
				return 0.55F + 0.3F * Mth.sin(now * 0.15F);
			}
			long hash = (now / 2) * 0x9E3779B97F4A7C15L;
			hash ^= hash >>> 31;
			return Math.floorMod(hash, 3) == 0 ? 0.08F : 1.0F;
		}
		return 1.0F;
	}

	private static void reportBusy(Minecraft minecraft) {
		if (minecraft.player == null) {
			busy = -1;
			return;
		}
		int state = CutscenePlayer.active() || minecraft.screen instanceof DialogueScreen || minecraft.screen instanceof CutsceneScreen
			|| minecraft.screen instanceof IntroScreen || minecraft.screen instanceof EndingScreen ? HorrorBusyPayload.STORY
			: minecraft.screen != null ? HorrorBusyPayload.SCREEN : HorrorBusyPayload.FREE;
		if (state != busy && ClientPlayNetworking.canSend(HorrorBusyPayload.TYPE)) {
			busy = state;
			ClientPlayNetworking.send(new HorrorBusyPayload(state));
		}
	}

	public static void reset() {
		QUEUE.clear();
		silenceUntil = 0;
		flickerUntil = 0;
		beamOffUntil = 0;
		joltLeft = 0;
		busy = -1;
	}
}
