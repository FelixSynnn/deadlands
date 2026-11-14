package com.deadlands.client.render;

import com.deadlands.Deadlands;
import com.deadlands.entity.DotEntity;
import java.util.Locale;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * D.O.T.'s animations, keyframed in Blender ({@code tools/assetgen/blender/dot_anims.py}, exported to
 * {@code models/entity/dot.anim}) and layered here:
 *
 * <ol>
 *   <li><b>Base</b>: the idle (breathing, a bird's head snapping from point to point, a claw flexing), blending into
 *       walking as he moves: the low prowl, and the hunched sprint while he hunts. Prowl and sprint run on one walk
 *       phase from the entity, so they blend stride for stride; their pelvis heights were baked in Blender to keep the
 *       boots on the floor.</li>
 *   <li><b>Climbing</b>, blended in on a wall.</li>
 *   <li><b>The action</b> (scream, yell, claw strikes, pounce, his abilities, twitches) eased in over the base and out
 *       again. Upper-body actions leave the legs to the gait while he moves, so he can strike at a run.</li>
 *   <li><b>Where he looks</b>, added to the head, less of it while an action holds the head.</li>
 * </ol>
 */
public class DotModel extends EntityModel<DotRenderer.State> {
	private static final Identifier ANIM = Deadlands.id("models/entity/dot.anim");
	/** Bones the gait keeps while he moves, even under an upper-body action. */
	private static final String[] LOWER = {"pelvis", "skirt", "leg_r", "foot_r", "leg_l", "foot_l", "cloth"};

	private final ModelPart[] parts;
	private final ModelPart pelvis;
	private final ModelPart head;
	private RiggedAnimation bound;
	private int[] partToBone = new int[0];
	private boolean[] lower = new boolean[0];
	private float[] base = new float[0];
	private float[] layer = new float[0];
	private float[] layer2 = new float[0];

	public DotModel(ModelPart root) {
		super(root);
		ModelPart base = root.getChild("root");
		parts = new ModelPart[DotGeometry.BONES.length];
		java.util.Map<String, ModelPart> byName = new java.util.HashMap<>();
		byName.put("root", base);
		for (int i = 0; i < DotGeometry.BONES.length; i++) {
			parts[i] = byName.get(DotGeometry.PARENTS[i]).getChild(DotGeometry.BONES[i]);
			byName.put(DotGeometry.BONES[i], parts[i]);
		}
		pelvis = byName.get("pelvis");
		head = byName.get("head");
	}

	@Override
	public void setupAnim(DotRenderer.State state) {
		super.setupAnim(state);
		RiggedAnimation anim = RiggedAnimation.get(ANIM);
		if (anim == null) {
			return;
		}
		bind(anim);
		float move = smooth(Math.min(1.0F, state.walkAnimationSpeed * 1.6F));

		// 1. The base: idle into prowl or sprint.
		RiggedAnimation.Clip idle = anim.clip("idle");
		if (idle != null) {
			anim.sample(idle, state.ageInTicks + state.seed * 37, base); // his own breathing, out of step with another's
		}
		RiggedAnimation.Clip prowl = anim.clip("prowl");
		RiggedAnimation.Clip sprint = anim.clip("sprint");
		if (move > 0.001F && prowl != null && sprint != null) {
			anim.sample(prowl, state.walkPhase * prowl.frames(), layer);
			anim.sample(sprint, state.walkPhase * sprint.frames(), layer2);
			mix(layer, layer2, state.aggro);
			mix(base, layer, move);
		}

		// 2. On a wall.
		RiggedAnimation.Clip climb = anim.clip("climb");
		if (state.climb > 0.001F && climb != null) {
			anim.sample(climb, state.ageInTicks, layer);
			mix(base, layer, smooth(state.climb));
		}

		// 3. The action, eased in and out.
		float held = 0.0F;
		if (state.action != DotEntity.Action.NONE) {
			RiggedAnimation.Clip clip = anim.clip(state.action.name().toLowerCase(Locale.ROOT));
			if (clip != null) {
				float age = state.actionAge;
				float w = smooth(age / 2.5F) * smooth((clip.length() + 3.0F - age) / 3.0F);
				if (w > 0.001F) {
					anim.sample(clip, age, layer);
					float legs = clip.fullBody() ? w : w * (1.0F - move);
					for (int i = 0; i < parts.length; i++) {
						int b = partToBone[i];
						if (b >= 0) {
							float k = lower[i] ? legs : w;
							for (int c = 0; c < 3; c++) {
								base[b * 3 + c] += (layer[b * 3 + c] - base[b * 3 + c]) * k;
							}
						}
					}
					int o = anim.bones.length * 3;
					for (int c = 0; c < 3; c++) {
						base[o + c] += (layer[o + c] - base[o + c]) * legs;
					}
					held = w;
				}
			}
		}

		// Onto the rig.
		for (int i = 0; i < parts.length; i++) {
			int b = partToBone[i];
			if (b >= 0) {
				parts[i].xRot = base[b * 3];
				parts[i].yRot = base[b * 3 + 1];
				parts[i].zRot = base[b * 3 + 2];
			}
		}
		int o = anim.bones.length * 3;
		pelvis.x += base[o];
		pelvis.y += base[o + 1];
		pelvis.z += base[o + 2];

		// 4. Where he looks.
		float look = 1.0F - 0.75F * held;
		head.yRot += state.yRot * Mth.DEG_TO_RAD * 0.8F * look;
		head.xRot += state.xRot * Mth.DEG_TO_RAD * 0.55F * look;
	}

	/** Matches the rig's parts to the file's bones (once per loaded file). */
	private void bind(RiggedAnimation anim) {
		if (bound == anim) {
			return;
		}
		bound = anim;
		partToBone = new int[parts.length];
		lower = new boolean[parts.length];
		for (int i = 0; i < parts.length; i++) {
			partToBone[i] = anim.bone(DotGeometry.BONES[i]);
			for (String name : LOWER) {
				lower[i] |= name.equals(DotGeometry.BONES[i]);
			}
		}
		base = new float[anim.stride];
		layer = new float[anim.stride];
		layer2 = new float[anim.stride];
	}

	private static void mix(float[] into, float[] other, float k) {
		for (int i = 0; i < into.length; i++) {
			into[i] += (other[i] - into[i]) * k;
		}
	}

	private static float smooth(float k) {
		k = Mth.clamp(k, 0.0F, 1.0F);
		return k * k * (3.0F - 2.0F * k);
	}
}
