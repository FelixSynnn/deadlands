package com.deadlands.client.cutscene;

import com.deadlands.config.ConfigManager;
import com.deadlands.network.CutscenePayload;
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

	/** True when the current cutscene's points were already turned to the bearing (no further yaw). */
	private static boolean bearingFrame;

	/**
	 * The scene's script, from its data file ({@link CutsceneLibrary}). In the bearing frame the points
	 * are turned now so that +X points along the bearing (given in radians).
	 */
	private static Script script(String id, float facing) {
		CutsceneLibrary.Def def = CutsceneLibrary.get(id).orElse(null);
		if (def == null) {
			bearingFrame = false;
			return new Script(List.of(new Shot(40, new Vec3(3, 2, 3), new Vec3(3, 2, 3), Vec3.ZERO, 0.0)), List.of(), null);
		}
		bearingFrame = def.frame().equals("bearing");
		double cos = Math.cos(facing);
		double sin = Math.sin(facing);
		List<Shot> shots = new java.util.ArrayList<>();
		for (CutsceneLibrary.ShotDef shot : def.shots()) {
			shots.add(bearingFrame
				? new Shot(shot.ticks(), alongBearing(shot.from(), cos, sin), alongBearing(shot.to(), cos, sin), alongBearing(shot.look(), cos, sin), shot.lookRise())
				: new Shot(shot.ticks(), shot.from(), shot.to(), shot.look(), shot.lookRise()));
		}
		List<Line> lines = new java.util.ArrayList<>();
		for (CutsceneLibrary.LineDef line : def.lines()) {
			lines.add(new Line(line.from(), line.to(), line.key()));
		}
		SoundEvent cue = def.cue().map(net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT::getValue).orElse(null);
		return new Script(shots, lines, cue);
	}

	private static Vec3 alongBearing(Vec3 point, double cos, double sin) {
		return new Vec3(point.x * cos - point.z * sin, point.y, point.x * sin + point.z * cos);
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
		yaw = bearingFrame ? 0.0F : facing;
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
