package com.deadlands.client.render;

import com.deadlands.Deadlands;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;

/**
 * The story people and survivors on Minecraft's player model, moving with clips keyed in Blender
 * ({@code tools/assetgen/blender/npc_anims.py}, {@code models/entity/npc.anim}): breathing and shifting their weight
 * while they stand (now and then a look over the shoulder), a relaxed walk blending into a run. Vanilla still does the
 * rest: where they look (added on top), swinging a weapon, holding and using items, crouching, riding and swimming.
 */
public class NpcModel extends HumanoidModel<NpcRenderer.State> {
	private final ClipRig rig;

	public NpcModel(ModelPart root) {
		super(root);
		rig = new ClipRig(root.createPartLookup(), "body", Deadlands.id("models/entity/npc.anim"));
	}

	@Override
	public void setupAnim(NpcRenderer.State state) {
		super.setupAnim(state);
		if (rig.anim() == null || state.isCrouching || state.isPassenger || state.swimAmount > 0.0F || state.isFallFlying || state.isUsingItem) {
			return;
		}
		float age = state.ageInTicks + state.seed * 31;
		if (!rig.sample("idle", age, rig.pose)) {
			return;
		}
		// Now and then, a look over the shoulder (two seconds in every thirty, on its own schedule).
		float lookAt = age % 600.0F;
		if (lookAt < rig.length("look") && (state.seed & 1) == 0 && rig.sample("look", lookAt, rig.layer)) {
			ClipRig.mix(rig.pose, rig.layer, ClipRig.smooth(lookAt / 4.0F) * ClipRig.smooth((rig.length("look") - lookAt) / 4.0F));
		}
		float move = ClipRig.smooth(Math.min(1.0F, state.walkAnimationSpeed * 1.4F));
		if (move > 0.001F && rig.sampleStride("walk", state.walkAnimationPos, 9.43F, rig.layer)
			&& rig.sampleStride("run", state.walkAnimationPos, 9.43F, rig.layer2)) {
			ClipRig.mix(rig.layer, rig.layer2, ClipRig.smooth((state.walkAnimationSpeed - 0.55F) / 0.35F));
			ClipRig.mix(rig.pose, rig.layer, move);
		}
		// Keep vanilla's look (added to the clip's head), and its arms while it fights or holds something up.
		float lookX = head.xRot;
		float lookY = head.yRot;
		boolean vanillaArms = state.attackTime > 0.0F || state.rightArmPose != ArmPose.EMPTY && state.rightArmPose != ArmPose.ITEM
			|| state.leftArmPose != ArmPose.EMPTY && state.leftArmPose != ArmPose.ITEM;
		float rx = rightArm.xRot;
		float ry = rightArm.yRot;
		float rz = rightArm.zRot;
		float lx = leftArm.xRot;
		float ly = leftArm.yRot;
		float lz = leftArm.zRot;
		float offX = rig.pose[rig.pose.length - 3];
		float offY = rig.pose[rig.pose.length - 2];
		rig.pose[rig.pose.length - 3] = 0.0F; // the whole figure moves by the offset, not just its body (below)
		rig.pose[rig.pose.length - 2] = 0.0F;
		rig.pose[rig.pose.length - 1] = 0.0F;
		rig.apply();
		head.xRot += lookX;
		head.yRot += lookY;
		if (vanillaArms) {
			rightArm.setRotation(rx, ry, rz);
			leftArm.setRotation(lx, ly, lz);
		}
		for (ModelPart part : new ModelPart[] {head, body, rightArm, leftArm, rightLeg, leftLeg}) {
			part.x += offX;
			part.y += offY;
		}
	}
}
