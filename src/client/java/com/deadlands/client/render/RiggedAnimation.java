package com.deadlands.client.render;

import com.deadlands.Deadlands;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Keyframed animations made in Blender for a boxless rig (the file {@code tools/assetgen/blender/dot_export.py}
 * writes): per clip, one pose per game tick, each pose every bone's xRot/yRot/zRot followed by the pelvis offset in
 * pixels. {@link #sample} interpolates between ticks so motion stays smooth at any frame rate.
 */
public final class RiggedAnimation {
	public record Clip(String name, boolean loop, boolean fullBody, boolean stepped, int frames, float[] data) {
		/** How long the clip plays, in ticks. */
		public float length() {
			return loop ? frames : frames - 1;
		}
	}

	private static final Map<Identifier, RiggedAnimation> LOADED = new HashMap<>();
	private static final Map<Identifier, Long> FAILED = new HashMap<>();

	public final String[] bones;
	/** Floats per pose: three per bone, then the pelvis offset. */
	public final int stride;
	private final Map<String, Clip> clips;

	private RiggedAnimation(String[] bones, Map<String, Clip> clips) {
		this.bones = bones;
		this.stride = bones.length * 3 + 3;
		this.clips = clips;
	}

	/** The animations in {@code file}; null if they cannot be read (logged; tried again five seconds later). */
	public static @Nullable RiggedAnimation get(Identifier file) {
		RiggedAnimation anim = LOADED.get(file);
		if (anim == null && System.currentTimeMillis() >= FAILED.getOrDefault(file, 0L)) {
			try {
				anim = read(file);
				LOADED.put(file, anim);
			} catch (IOException | RuntimeException failure) {
				FAILED.put(file, System.currentTimeMillis() + 5000L);
				Deadlands.LOGGER.error("DEADLANDS: could not read the animations {}", file, failure);
			}
		}
		return anim;
	}

	public @Nullable Clip clip(String name) {
		return clips.get(name);
	}

	/** The bone's index in each pose, or -1. */
	public int bone(String name) {
		for (int i = 0; i < bones.length; i++) {
			if (bones[i].equals(name)) {
				return i;
			}
		}
		return -1;
	}

	/** Writes the clip's pose at {@code time} ticks into {@code out}: wrapped if it loops, held at the ends if not. */
	public void sample(Clip clip, float time, float[] out) {
		int n = clip.frames();
		float t;
		int a;
		int b;
		if (clip.loop()) {
			t = time % n;
			if (t < 0.0F) {
				t += n;
			}
			a = Math.min(n - 1, (int) t);
			b = (a + 1) % n;
		} else {
			t = Math.max(0.0F, Math.min(time, n - 1));
			a = Math.min(n - 1, (int) t);
			b = Math.min(n - 1, a + 1);
		}
		float k = clip.stepped() ? 0.0F : t - a;
		float[] d = clip.data();
		int oa = a * stride;
		int ob = b * stride;
		for (int i = 0; i < stride; i++) {
			out[i] = d[oa + i] + (d[ob + i] - d[oa + i]) * k;
		}
	}

	private static RiggedAnimation read(Identifier file) throws IOException {
		try (InputStream raw = Minecraft.getInstance().getResourceManager().getResourceOrThrow(file).open();
			DataInputStream in = new DataInputStream(new BufferedInputStream(raw))) {
			byte[] magic = new byte[8];
			in.readFully(magic);
			if (!new String(magic, 0, 7, StandardCharsets.US_ASCII).equals("DLANIM1")) {
				throw new IOException("not DEADLANDS animations");
			}
			String[] bones = new String[in.readInt()];
			for (int i = 0; i < bones.length; i++) {
				bones[i] = string(in);
			}
			int stride = bones.length * 3 + 3;
			int count = in.readInt();
			Map<String, Clip> clips = new HashMap<>();
			for (int c = 0; c < count; c++) {
				String name = string(in);
				int flags = in.readByte();
				int frames = in.readInt();
				if (frames <= 0) {
					throw new IOException("the clip " + name + " has no frames");
				}
				float[] data = new float[frames * stride];
				for (int i = 0; i < data.length; i++) {
					data[i] = in.readFloat();
				}
				clips.put(name, new Clip(name, (flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0, frames, data));
			}
			return new RiggedAnimation(bones, Map.copyOf(clips));
		}
	}

	private static String string(DataInputStream in) throws IOException {
		byte[] bytes = new byte[in.readShort()];
		in.readFully(bytes);
		return new String(bytes, StandardCharsets.UTF_8);
	}
}
