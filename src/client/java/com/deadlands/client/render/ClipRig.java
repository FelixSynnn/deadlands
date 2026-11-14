package com.deadlands.client.render;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * Plays Blender-made clips ({@link RiggedAnimation}) on a boxless rig: sample clips into poses, blend poses, and put
 * the result on the parts. Poses are float arrays in the file's layout (three angles a bone, then the root bone's
 * offset in pixels); {@link #apply} matches them to the parts by name.
 */
public final class ClipRig {
	private final Identifier file;
	private final ModelPart[] parts;
	private final String[] names;
	private final ModelPart root;
	private @Nullable RiggedAnimation bound;
	private int[] partToBone = new int[0];
	public float[] pose = new float[0];
	public float[] layer = new float[0];
	public float[] layer2 = new float[0];

	/** @param bones the rig's bone names, parents first (a Geometry's BONES and PARENTS); the first is the root bone. */
	public ClipRig(ModelPart modelRoot, String[] bones, String[] parents, Identifier file) {
		this.file = file;
		this.names = bones;
		Map<String, ModelPart> byName = new HashMap<>();
		byName.put("root", modelRoot.getChild("root"));
		parts = new ModelPart[bones.length];
		for (int i = 0; i < bones.length; i++) {
			parts[i] = byName.get(parents[i]).getChild(bones[i]);
			byName.put(bones[i], parts[i]);
		}
		root = parts[0];
	}

	/** The animations, bound to this rig; null if the file can't be read. */
	public @Nullable RiggedAnimation anim() {
		RiggedAnimation anim = RiggedAnimation.get(file);
		if (anim != null && anim != bound) {
			bound = anim;
			partToBone = new int[parts.length];
			for (int i = 0; i < parts.length; i++) {
				partToBone[i] = anim.bone(names[i]);
			}
			pose = new float[anim.stride];
			layer = new float[anim.stride];
			layer2 = new float[anim.stride];
		}
		return anim;
	}

	public ModelPart part(String name) {
		for (int i = 0; i < names.length; i++) {
			if (names[i].equals(name)) {
				return parts[i];
			}
		}
		throw new IllegalArgumentException(name);
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

	public float length(String clip) {
		RiggedAnimation.Clip c = bound == null ? null : bound.clip(clip);
		return c == null ? 0.0F : c.length();
	}

	public static void mix(float[] into, float[] other, float k) {
		if (k <= 0.0F) {
			return;
		}
		for (int i = 0; i < into.length; i++) {
			into[i] += (other[i] - into[i]) * k;
		}
	}

	/** Puts {@link #pose} on the parts (rotations, and the root bone's offset). */
	public void apply() {
		if (bound == null) {
			return;
		}
		for (int i = 0; i < parts.length; i++) {
			int b = partToBone[i];
			if (b >= 0) {
				parts[i].xRot = pose[b * 3];
				parts[i].yRot = pose[b * 3 + 1];
				parts[i].zRot = pose[b * 3 + 2];
			}
		}
		int o = bound.bones.length * 3;
		root.x += pose[o];
		root.y += pose[o + 1];
		root.z += pose[o + 2];
	}

	public static float smooth(float k) {
		k = Mth.clamp(k, 0.0F, 1.0F);
		return k * k * (3.0F - 2.0F * k);
	}
}
