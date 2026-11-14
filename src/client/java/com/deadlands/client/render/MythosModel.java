package com.deadlands.client.render;

import com.deadlands.Deadlands;
import com.deadlands.entity.MythosEntity;
import java.util.Locale;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Mythos' animations, keyed in Blender at 60 fps ({@code tools/assetgen/blender/mythos_anims.py},
 * {@code models/entity/mythos.anim}), layered here: the idle blends into the walk as he moves and the walk into the
 * chase while he fights (both on one stride phase from the entity, so the feet stay in step); the action plays over
 * that, eased in and out (the slash and the tether leave the legs to the gait while he moves); his death plays out over
 * its 2.5 seconds; where he looks is added to the head. Glitches: a few frames at a time his head and claws jerk out of
 * place (more often enraged).
 */
public class MythosModel extends EntityModel<MythosRenderer.State> {
	private final ClipRig rig;
	private final ModelPart head;
	private final ModelPart neck;
	private final ModelPart clawsR;
	private final ModelPart clawsL;

	public MythosModel(ModelPart root) {
		super(root);
		rig = new ClipRig(root, MythosGeometry.BONES, MythosGeometry.PARENTS, Deadlands.id("models/entity/mythos.anim"));
		head = rig.part("head");
		neck = rig.part("neck");
		clawsR = rig.part("claws_r");
		clawsL = rig.part("claws_l");
	}

	@Override
	public void setupAnim(MythosRenderer.State state) {
		super.setupAnim(state);
		if (rig.anim() == null) {
			return;
		}
		float move = ClipRig.smooth(Math.min(1.0F, state.walkAnimationSpeed * 1.6F));
		// Base: idle, walk, chase.
		rig.sample("idle", state.ageInTicks + state.seed * 41, rig.pose);
		if (move > 0.001F) {
			boolean walk = rig.sample("walk", state.walkPhase * rig.length("walk"), rig.layer);
			boolean chase = rig.sample("chase", state.walkPhase * rig.length("chase"), rig.layer2);
			if (walk && chase) {
				ClipRig.mix(rig.layer, rig.layer2, state.chase);
				ClipRig.mix(rig.pose, rig.layer, move);
			}
		}
		// The action.
		float held = 0.0F;
		if (state.deathTime > 0.0F) {
			if (rig.sample("death", state.deathTime + 0.0F, rig.layer)) {
				ClipRig.mix(rig.pose, rig.layer, ClipRig.smooth(state.deathTime / 3.0F));
				held = 1.0F;
			}
		} else if (state.action != MythosEntity.Action.NONE) {
			String clip = state.action.name().toLowerCase(Locale.ROOT);
			float length = rig.length(clip);
			if (length > 0.0F && rig.sample(clip, state.actionAge, rig.layer)) {
				float w = ClipRig.smooth(state.actionAge / 2.5F) * ClipRig.smooth((length + 3.0F - state.actionAge) / 3.0F);
				if (state.action == MythosEntity.Action.ENTRANCE || state.action == MythosEntity.Action.REJUVENATE) {
					w = ClipRig.smooth((length + 3.0F - state.actionAge) / 3.0F); // no blend in: he starts in it
					if (state.action == MythosEntity.Action.REJUVENATE) {
						w = 1.0F;
					}
				}
				boolean upper = state.action == MythosEntity.Action.SLASH || state.action == MythosEntity.Action.ENTANGLE;
				mixAction(w, upper ? w * (1.0F - move) : w);
				held = w;
			}
		}
		rig.apply();
		// Where he looks.
		float look = 1.0F - 0.8F * held;
		head.yRot += state.yRot * Mth.DEG_TO_RAD * 0.7F * look;
		head.xRot += state.xRot * Mth.DEG_TO_RAD * 0.5F * look;
		// Glitch jerks.
		int beat = Mth.floor(state.ageInTicks / 7.0F);
		int roll = Math.floorMod(beat * 92821 + state.seed * 68917, 100);
		float into = state.ageInTicks - beat * 7.0F;
		if (state.deathTime <= 0.0F && roll < (state.phase == 3 ? 22 : 10) && into < 1.5F) {
			float s = (roll % 2 == 0 ? 1.0F : -1.0F);
			head.zRot += 0.5F * s;
			neck.yRot += 0.3F * s;
			clawsR.zRot -= 0.4F;
			clawsL.zRot += 0.4F;
		}
	}

	/** Blends the action (in rig.layer) over the base: upper body by {@code w}, the legs, tail and hips by {@code legs}. */
	private void mixAction(float w, float legs) {
		float[] pose = rig.pose;
		float[] layer = rig.layer;
		for (int i = 0; i < MythosGeometry.BONES.length; i++) {
			String b = MythosGeometry.BONES[i];
			boolean lower = b.startsWith("leg") || b.startsWith("shin") || b.startsWith("foot") || b.startsWith("tail") || b.equals("pelvis");
			float k = lower ? legs : w;
			for (int c = 0; c < 3; c++) {
				pose[i * 3 + c] += (layer[i * 3 + c] - pose[i * 3 + c]) * k;
			}
		}
		int o = pose.length - 3;
		for (int c = 0; c < 3; c++) {
			pose[o + c] += (layer[o + c] - pose[o + c]) * legs;
		}
	}
}
