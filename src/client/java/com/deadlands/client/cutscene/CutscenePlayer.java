package com.deadlands.client.cutscene;

import com.deadlands.config.ConfigManager;
import com.deadlands.network.CutscenePayload;
import com.deadlands.registry.DLSounds;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Plays the story's cutscenes: a scripted camera (shots that ease from one point to another while
 * looking at a target, all relative to an anchor the server gives), letterbox bars, subtitles, a
 * sound cue, a fade in and out. While one plays, {@link CutsceneScreen} is open (so the survivor
 * stands still and any key skips) and {@code CameraMixin} takes the camera.
 */
public final class CutscenePlayer {
	/** One camera move: from and to (relative to the anchor, before turning by its yaw), looking at a point that may rise. */
	record Shot(int ticks, Vec3 from, Vec3 to, Vec3 look, double lookRise) {
	}

	/** A subtitle line shown between two ticks. */
	record Line(int from, int to, String key) {
	}

	record Script(List<Shot> shots, List<Line> lines, @Nullable SoundEvent cue) {
		int length() {
			int total = 0;
			for (Shot shot : shots) {
				total += shot.ticks();
			}
			return total;
		}
	}

	private static @Nullable Script script;
	private static Vec3 anchor = Vec3.ZERO;
	private static float yaw;
	private static int ticks;
	private static @Nullable Runnable onEnd;

	private CutscenePlayer() {
	}

	private static Script script(String id, float bearing) {
		Vec3 ahead = new Vec3(Math.cos(bearing) * 30.0, 2.0, Math.sin(bearing) * 30.0);
		return switch (id) {
			case "opening" -> new Script(List.of(
				new Shot(110, new Vec3(14, 9, 12), new Vec3(-12, 6, 12), new Vec3(0, 1, 0), 0.0),
				new Shot(90, new Vec3(7, 2.5, 6), new Vec3(2.6, 1.9, 2.4), new Vec3(0, 1.5, 0), 0.0)),
				List.of(new Line(10, 95, "cutscene.deadlands.opening.0"), new Line(105, 195, "cutscene.deadlands.opening.1")), DLSounds.EVENT_DAY_BEGIN.value());
			case "boss_reveal" -> new Script(List.of(
				new Shot(70, new Vec3(0, 6, -18), new Vec3(0, 3, -9), new Vec3(0, 2, 0), 0.0),
				new Shot(60, new Vec3(4, 1.5, -6), new Vec3(-4, 1.5, -6), new Vec3(0, 2.4, 0), 0.0)),
				List.of(new Line(20, 125, "cutscene.deadlands.boss_reveal.0")), DLSounds.EVENT_BOSS.value());
			case "research", "cure" -> new Script(List.of(
				new Shot(120, new Vec3(4, 2.5, 0), new Vec3(0, 2.0, 4), new Vec3(0, 1.4, 0), 0.0)),
				List.of(new Line(10, 115, "cutscene.deadlands." + id + ".0")), DLSounds.EVENT_TOWN_ENTER.value());
			case "horde" -> new Script(List.of(
				new Shot(60, new Vec3(0, 3, 0), new Vec3(0, 7, 0), ahead.scale(0.5), 0.0),
				new Shot(80, new Vec3(0, 7, 0), ahead.scale(0.3).add(0, 5, 0), ahead, 0.0)),
				List.of(new Line(15, 135, "cutscene.deadlands.horde.0")), DLSounds.EVENT_HORDE_START.value());
			case "extraction" -> new Script(List.of(
				new Shot(120, new Vec3(14, 3, 10), new Vec3(-14, 6, 10), new Vec3(0, 2, 0), 10.0),
				new Shot(100, new Vec3(-14, 6, 10), new Vec3(-6, 22, -10), new Vec3(0, 12, 0), 16.0)),
				List.of(new Line(20, 110, "cutscene.deadlands.extraction.0"), new Line(130, 215, "cutscene.deadlands.extraction.1")),
				DLSounds.HELICOPTER_ROTOR.value());
			default -> new Script(List.of(new Shot(40, new Vec3(3, 2, 3), new Vec3(3, 2, 3), Vec3.ZERO, 0.0)), List.of(), null);
		};
	}

	public static void start(CutscenePayload payload) {
		start(payload.id(), new Vec3(payload.x(), payload.y(), payload.z()), payload.yaw(), null);
	}

	/** Play a cutscene; {@code then} runs when it ends or is skipped (straight away if cutscenes are switched off). */
	public static void start(String id, Vec3 at, float facing, @Nullable Runnable then) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!ConfigManager.get().ui.cutscenes || minecraft.player == null) {
			if (then != null) {
				then.run();
			}
			return;
		}
		script = script(id, facing);
		anchor = at;
		yaw = id.equals("horde") ? 0.0F : facing;
		ticks = 0;
		onEnd = then;
		minecraft.setScreen(new CutsceneScreen());
		if (script.cue() != null) {
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(script.cue(), 1.0F, 0.9F));
		}
	}

	public static boolean active() {
		return script != null;
	}

	public static void tick() {
		if (script == null) {
			return;
		}
		ticks++;
		if (ticks >= script.length()) {
			stop();
		}
	}

	/** End now (finished or skipped): close the overlay, then carry on. */
	public static void stop() {
		if (script == null) {
			return;
		}
		script = null;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.screen instanceof CutsceneScreen) {
			minecraft.setScreen(null);
		}
		runEnd();
	}

	/** The overlay was closed by something else (death, disconnecting): end without touching screens. */
	static void abandon() {
		if (script != null) {
			script = null;
			runEnd();
		}
	}

	private static void runEnd() {
		Runnable then = onEnd;
		onEnd = null;
		if (then != null) {
			then.run();
		}
	}

	private static Vec3 turn(Vec3 offset) {
		return offset.yRot(-yaw * Mth.DEG_TO_RAD);
	}

	/** The current shot and how far through it, 0 to 1 (eased). */
	private static Object[] current(float partialTick) {
		float t = ticks + partialTick;
		for (Shot shot : script.shots()) {
			if (t < shot.ticks()) {
				float p = Mth.clamp(t / shot.ticks(), 0.0F, 1.0F);
				return new Object[] {shot, p * p * (3.0F - 2.0F * p)};
			}
			t -= shot.ticks();
		}
		return new Object[] {script.shots().getLast(), 1.0F};
	}

	public static Vec3 position(float partialTick) {
		Object[] now = current(partialTick);
		Shot shot = (Shot) now[0];
		float p = (Float) now[1];
		return anchor.add(turn(shot.from().lerp(shot.to(), p)));
	}

	/** Yaw and pitch, degrees, to look from the camera at the shot's target. */
	public static float[] rotation(float partialTick) {
		Object[] now = current(partialTick);
		Shot shot = (Shot) now[0];
		float p = (Float) now[1];
		Vec3 eye = position(partialTick);
		Vec3 look = anchor.add(turn(shot.look())).add(0.0, shot.lookRise() * p, 0.0);
		Vec3 d = look.subtract(eye);
		double flat = Math.sqrt(d.x * d.x + d.z * d.z);
		float lookYaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
		float pitch = (float) -(Mth.atan2(d.y, flat) * Mth.RAD_TO_DEG);
		return new float[] {lookYaw, pitch};
	}

	/** 0 to 1: black over the picture, at the start and end. */
	public static float fade(float partialTick) {
		if (script == null) {
			return 0.0F;
		}
		float t = ticks + partialTick;
		float end = script.length() - t;
		return Mth.clamp(Math.max(1.0F - t / 12.0F, 1.0F - end / 12.0F), 0.0F, 1.0F);
	}

	public static @Nullable Component subtitle() {
		if (script == null) {
			return null;
		}
		for (Line line : script.lines()) {
			if (ticks >= line.from() && ticks < line.to()) {
				return Component.translatable(line.key());
			}
		}
		return null;
	}
}
