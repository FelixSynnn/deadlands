package com.deadlands.client.render;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * Plays Blender-made clips ({@link RiggedAnimation}) on a model: sample clips into poses, blend poses (whole or by a
 * mask), mirror them, and put the result on the parts. Poses are float arrays in the file's layout (three angles a bone,
 * then the root bone's offset in pixels); {@link #apply} matches them to the model's parts by name. Bones the model
 * doesn't have are skipped, parts the file doesn't move are left alone.
 */
public final class ClipRig {
	private final Identifier file;
	private final Function<String, @Nullable ModelPart> lookup;
	private final String rootName;
	private @Nullable RiggedAnimation bound;
	private ModelPart[] parts = new ModelPart[0];
	private int[] mirrorOf = new int[0];
	private @Nullable ModelPart root;
	public float[] pose = new float[0];
	public float[] layer = new float[0];
	public float[] layer2 = new float[0];

	/** A rig whose parts are found by name ({@code ModelPart.createPartLookup()}); {@code rootName} takes the offset. */
	public ClipRig(Function<String, @Nullable ModelPart> lookup, String rootName, Identifier file) {
		this.lookup = lookup;
		this.rootName = rootName;
		this.file = file;
	}

	/** A boxless rig from a generated Geometry's BONES and PARENTS (under the model's "root" part); the first bone takes the offset. */
	public ClipRig(ModelPart modelRoot, String[] bones, String[] parents, Identifier file) {
		Map<String, ModelPart> byName = new HashMap<>();
		byName.put("root", modelRoot.getChild("root"));
		for (int i = 0; i < bones.length; i++) {
			byName.put(bones[i], byName.get(parents[i]).getChild(bones[i]));
		}
		this.lookup = byName::get;
		this.rootName = bones[0];
		this.file = file;
	}

	/** The animations, bound to this model; null if the file can't be read. */
	public @Nullable RiggedAnimation anim() {
		RiggedAnimation anim = RiggedAnimation.get(file);
		if (anim != null && anim != bound) {
			bound = anim;
			int n = anim.bones.length;
			parts = new ModelPart[n];
			mirrorOf = new int[n];
			for (int i = 0; i < n; i++) {
				parts[i] = lookup.apply(anim.bones[i]);
				mirrorOf[i] = anim.bone(mirrorName(anim.bones[i]));
				if (mirrorOf[i] < 0) {
					mirrorOf[i] = i;
				}
			}
			root = lookup.apply(rootName);
			pose = new float[anim.stride];
			layer = new float[anim.stride];
			layer2 = new float[anim.stride];
		}
		return anim;
	}

	private static String mirrorName(String name) {
		if (name.startsWith("right_")) {
			return "left_" + name.substring(6);
		}
		if (name.startsWith("left_")) {
			return "right_" + name.substring(5);
		}
		if (name.endsWith("_r")) {
			return name.substring(0, name.length() - 2) + "_l";
		}
		if (name.endsWith("_l")) {
			return name.substring(0, name.length() - 2) + "_r";
		}
		if (name.contains("_r_")) {
			return name.replace("_r_", "_l_");
		}
		if (name.contains("_l_")) {
			return name.replace("_l_", "_r_");
		}
		return name;
	}

	public boolean has(String clip) {
		return bound != null && bound.clip(clip) != null;
	}

	/** Samples a clip into {@code out}; false (and {@code out} untouched) if the clip is missing. */
	public boolean sample(String clip, float ticks, float[] out) {
		RiggedAnimation anim = bound;
		RiggedAnimation.Clip c = anim == null ? null : anim.clip(clip);
		if (c == null) {
			return false;
		}
		anim.sample(c, ticks, out);
		return true;
	}

	/** Samples a looping clip by a stride position: {@code pos} units of walk animation make one cycle every {@code perCycle}. */
	public boolean sampleStride(String clip, float pos, float perCycle, float[] out) {
		return sample(clip, pos / perCycle * length(clip), out);
	}

	public float length(String clip) {
		RiggedAnimation.Clip c = bound == null ? null : bound.clip(clip);
		return c == null ? 0.0F : c.length();
	}

	/** Left for right: swaps the sides' bones, turns yaw and roll the other way, and the sideways offset. */
	public void mirror(float[] p) {
		if (bound == null) {
			return;
		}
		float[] copy = p.clone();
		for (int i = 0; i < mirrorOf.length; i++) {
			int j = mirrorOf[i];
			p[i * 3] = copy[j * 3];
			p[i * 3 + 1] = -copy[j * 3 + 1];
			p[i * 3 + 2] = -copy[j * 3 + 2];
		}
		int o = mirrorOf.length * 3;
		p[o] = -copy[o];
	}

	public static void mix(float[] into, float[] other, float k) {
		if (k <= 0.0F) {
			return;
		}
		for (int i = 0; i < into.length; i++) {
			into[i] += (other[i] - into[i]) * k;
		}
	}

	/** Blends {@code other} over {@code into}: bones {@code lower} accepts by {@code kLower}, the rest by {@code k} (the offset counts as lower). */
	public void mixMasked(float[] into, float[] other, float k, float kLower, Predicate<String> lower) {
		if (bound == null || k <= 0.0F && kLower <= 0.0F) {
			return;
		}
		String[] bones = bound.bones;
		for (int i = 0; i < bones.length; i++) {
			float w = lower.test(bones[i]) ? kLower : k;
			for (int c = 0; c < 3; c++) {
				into[i * 3 + c] += (other[i * 3 + c] - into[i * 3 + c]) * w;
			}
		}
		int o = bones.length * 3;
		for (int c = 0; c < 3; c++) {
			into[o + c] += (other[o + c] - into[o + c]) * kLower;
		}
	}

	/** Puts {@link #pose} on the parts (rotations, and the root bone's offset). */
	public void apply() {
		if (bound == null) {
			return;
		}
		for (int i = 0; i < parts.length; i++) {
			ModelPart part = parts[i];
			if (part != null) {
				part.xRot = pose[i * 3];
				part.yRot = pose[i * 3 + 1];
				part.zRot = pose[i * 3 + 2];
			}
		}
		if (root != null) {
			int o = parts.length * 3;
			root.x += pose[o];
			root.y += pose[o + 1];
			root.z += pose[o + 2];
		}
	}

	public ModelPart part(String name) {
		ModelPart part = lookup.apply(name);
		if (part == null) {
			throw new IllegalArgumentException(name);
		}
		return part;
	}

	public static float smooth(float k) {
		k = Mth.clamp(k, 0.0F, 1.0F);
		return k * k * (3.0F - 2.0F * k);
	}
}
