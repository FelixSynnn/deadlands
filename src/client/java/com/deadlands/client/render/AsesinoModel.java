package com.deadlands.client.render;

import com.deadlands.entity.AsesinoEntity;
import java.util.Locale;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * The Asesino's animations on his own rig ({@link AsesinoGeometry}).
 *
 * <p><b>Always:</b> breathing (the chest rises, the shoulders lift), the head tracking what he looks at
 * (shared between neck and head), a walk cycle with real knees (the shin folds on the back swing, the
 * pelvis dips and sways and the torso counter-turns), elbows that lag behind the shoulders, and coat
 * tails that trail the legs and stream back when he runs. Stance changes blend smoothly: how hunched
 * he is and how much he is sprinting are eased on the client ({@code AsesinoEntity.hunch}/{@code sprint}).
 *
 * <p><b>Gaits:</b> roaming is a slow, heavy walk, the machete hanging forward, an idle fidget of the blade
 * and now and then a slow tilt of the head; stalking is hunched and low, knees bent, head held level;
 * chasing is a deep sprinting lean with big pumping arms and the blade cocked back; searching sweeps
 * the head from side to side.
 *
 * <p><b>Actions</b> (synced from the server, keyframed here in ticks): two slashes that alternate, the
 * overhead wind-up and diagonal slash with follow-through and the backhand draw and flat sweep; a
 * crouched, listening lurk; the pounce crouch and the leaping cut, a slumping stagger when
 * stunned, the shriek (thrown back, arms flung wide, shuddering), the two-handed breach smash, the
 * frenzy's thrashing, the scent's raised and turning head, and the frozen, head-tilted stare.
 *
 * <p>All of it he uses is keyed in Blender at 60 fps ({@code tools/assetgen/blender/aberrant_moves.py} and
 * {@code aberrant_anims.py}, {@code models/entity/aberrant.anim}) and blended in {@link #clipAnim}; the procedural
 * version below is kept for when that file can't be read, and for the old ability poses he no longer uses.
 */
public class AsesinoModel extends EntityModel<AsesinoRenderer.State> {
	private final ModelPart pelvis;
	private final ModelPart torso;
	private final ModelPart neck;
	private final ModelPart head;
	private final ModelPart armR;
	private final ModelPart forearmR;
	private final ModelPart handR;
	private final ModelPart armL;
	private final ModelPart forearmL;
	private final ModelPart handL;
	private final ModelPart coatFrontR;
	private final ModelPart coatFrontL;
	private final ModelPart coatBack;
	private final ModelPart legR;
	private final ModelPart shinR;
	private final ModelPart legL;
	private final ModelPart shinL;

	public AsesinoModel(ModelPart root) {
		super(root);
		ModelPart base = root.getChild("root");
		pelvis = base.getChild("pelvis");
		torso = pelvis.getChild("torso");
		neck = torso.getChild("neck");
		head = neck.getChild("head");
		armR = torso.getChild("arm_r");
		forearmR = armR.getChild("forearm_r");
		handR = forearmR.getChild("hand_r");
		armL = torso.getChild("arm_l");
		forearmL = armL.getChild("forearm_l");
		handL = forearmL.getChild("hand_l");
		coatFrontR = pelvis.getChild("coat_front_r");
		coatFrontL = pelvis.getChild("coat_front_l");
		coatBack = pelvis.getChild("coat_back");
		legR = pelvis.getChild("leg_r");
		shinR = legR.getChild("shin_r");
		legL = pelvis.getChild("leg_l");
		shinL = legL.getChild("shin_l");
		clips = new ClipRig(root.createPartLookup(), "pelvis", ANIM);
	}

	private final ClipRig clips;
	private static final float STRIDE = Mth.TWO_PI / 0.55F;

	/** The Blender clips: stance (hunched, lurking, searching), the walks blended by speed, climbing, then the action; false without them. */
	private boolean clipAnim(AsesinoRenderer.State state) {
		ClipRig rig = clips;
		AsesinoEntity.Action action = state.action;
		boolean old = action == AsesinoEntity.Action.SHRIEK || action == AsesinoEntity.Action.BREACH || action == AsesinoEntity.Action.STUN
			|| action == AsesinoEntity.Action.FRENZY;
		if (old || rig.anim() == null || !rig.sample("idle", state.ageInTicks, rig.pose)) {
			return false;
		}
		float t = state.ageInTicks;
		float hunch = state.hunch;
		float sprint = state.sprint;
		boolean frozen = action == AsesinoEntity.Action.STARE || action == AsesinoEntity.Action.CROUCH;
		float move = ClipRig.smooth(Math.min(1.0F, state.walkAnimationSpeed * 1.2F)) * (frozen ? 0.15F : 1.0F);
		if (hunch > 0.001F && rig.sample("stalk_idle", t, rig.layer)) {
			ClipRig.mix(rig.pose, rig.layer, hunch);
		}
		if (state.mode == AsesinoEntity.Mode.LURK && rig.sample("lurk", t, rig.layer)) {
			ClipRig.mix(rig.pose, rig.layer, 1.0F - move);
		}
		if (move > 0.001F && rig.sampleStride("walk", state.walkAnimationPos, STRIDE, rig.layer)) {
			if (hunch > 0.001F && rig.sampleStride("stalk", state.walkAnimationPos, STRIDE, rig.layer2)) {
				ClipRig.mix(rig.layer, rig.layer2, hunch * (1.0F - sprint));
			}
			if (sprint > 0.001F && rig.sampleStride("run", state.walkAnimationPos, STRIDE, rig.layer2)) {
				ClipRig.mix(rig.layer, rig.layer2, sprint);
			}
			ClipRig.mix(rig.pose, rig.layer, move);
		}
		if (state.mode == AsesinoEntity.Mode.SEARCH && rig.sample("search", t, rig.layer)) {
			rig.mixMasked(rig.pose, rig.layer, 1.0F - 0.6F * move, 1.0F - move, AsesinoModel::legs);
		}
		if (state.climbing && rig.sample("climb", t, rig.layer)) {
			ClipRig.mix(rig.pose, rig.layer, 1.0F);
		}
		if (action != AsesinoEntity.Action.NONE) {
			String name = action.name().toLowerCase(Locale.ROOT);
			float a = state.actionAge;
			if (rig.sample(name, a, rig.layer)) {
				boolean chained = action == AsesinoEntity.Action.SWING || action == AsesinoEntity.Action.SWING2 || action == AsesinoEntity.Action.LEAP;
				float w = chained ? 1.0F : ClipRig.smooth(a / (action == AsesinoEntity.Action.DIG ? 5.0F : 2.0F));
				if (chained) {
					w *= ClipRig.smooth((rig.length(name) - a) / 2.0F); // a chain's last clip hands back to the walk over its final two ticks
				}
				boolean arms = action == AsesinoEntity.Action.WINDUP || action == AsesinoEntity.Action.SWING
					|| action == AsesinoEntity.Action.WINDUP2 || action == AsesinoEntity.Action.SWING2;
				rig.mixMasked(rig.pose, rig.layer, w, arms ? w * (1.0F - 0.75F * move) : w, AsesinoModel::legs);
			}
		}
		rig.apply();
		float yaw = state.yRot * Mth.DEG_TO_RAD;
		float pitch = state.xRot * Mth.DEG_TO_RAD;
		neck.yRot += yaw * 0.3F;
		head.yRot += yaw * 0.7F;
		head.xRot += pitch * 0.8F;
		neck.xRot += pitch * 0.2F;
		return true;
	}

	private static boolean legs(String bone) {
		return bone.equals("pelvis") || bone.startsWith("leg_") || bone.startsWith("shin_") || bone.startsWith("coat_");
	}

	@Override
	public void setupAnim(AsesinoRenderer.State state) {
		super.setupAnim(state); // back to the rest pose
		if (clipAnim(state)) {
			return;
		}
		float t = state.ageInTicks;
		float speed = Math.min(1.0F, state.walkAnimationSpeed * 1.2F);
		float phase = state.walkAnimationPos * 0.55F;
		float hunch = state.hunch;
		float sprint = state.sprint;
		float a = state.actionAge;
		AsesinoEntity.Action action = state.action;
		boolean frozen = action == AsesinoEntity.Action.STARE || action == AsesinoEntity.Action.SHRIEK || action == AsesinoEntity.Action.BREACH
			|| action == AsesinoEntity.Action.STUN || action == AsesinoEntity.Action.CROUCH;
		if (frozen) {
			speed *= 0.15F;
		}

		// ---------------------------------------------------------------- breathing and the head
		float breath = Mth.sin(t * 0.075F);
		torso.xRot = 0.10F + breath * 0.02F;
		armR.y += breath * 0.15F;
		armL.y += breath * 0.15F;
		float yaw = state.yRot * Mth.DEG_TO_RAD;
		float pitch = state.xRot * Mth.DEG_TO_RAD;
		neck.yRot = yaw * 0.3F;
		head.yRot = yaw * 0.7F;
		head.xRot = pitch * 0.8F;
		neck.xRot = pitch * 0.2F;

		// ---------------------------------------------------------------- walk cycle (amplitudes grow with the sprint)
		float stride = 0.65F + 0.45F * sprint;
		float swingR = Mth.sin(phase);
		float swingL = Mth.sin(phase + Mth.PI);
		legR.xRot = -swingR * stride * speed;
		legL.xRot = -swingL * stride * speed;
		shinR.xRot = Math.max(0.0F, Mth.sin(phase - 0.9F)) * (0.9F + 0.6F * sprint) * speed;
		shinL.xRot = Math.max(0.0F, Mth.sin(phase + Mth.PI - 0.9F)) * (0.9F + 0.6F * sprint) * speed;
		pelvis.y += -Math.abs(Mth.cos(phase)) * (0.7F + 0.8F * sprint) * speed;
		pelvis.yRot = swingR * 0.09F * speed;
		pelvis.zRot = Mth.cos(phase) * 0.04F * speed;
		torso.yRot = -swingR * (0.12F + 0.1F * sprint) * speed;
		float armSwing = 0.45F + 0.9F * sprint;
		armR.xRot = swingR * armSwing * speed;
		armL.xRot = swingL * armSwing * speed;
		forearmR.xRot = -0.35F - Math.max(0.0F, Mth.sin(phase - 0.5F)) * (0.3F + 0.7F * sprint) * speed; // the elbow lags
		forearmL.xRot = -0.25F - Math.max(0.0F, Mth.sin(phase + Mth.PI - 0.5F)) * (0.3F + 0.7F * sprint) * speed;
		armR.zRot = 0.08F;
		armL.zRot = -0.08F;

		// ---------------------------------------------------------------- stance: hunch (stalk/chase) and sprint lean
		pelvis.y += hunch * 2.2F;
		legR.xRot -= hunch * 0.55F;
		legL.xRot -= hunch * 0.55F;
		shinR.xRot += hunch * 0.9F;
		shinL.xRot += hunch * 0.9F;
		torso.xRot += hunch * 0.5F + sprint * 0.25F;
		neck.xRot -= hunch * 0.35F + sprint * 0.15F;
		head.xRot -= hunch * 0.3F + sprint * 0.1F;
		armR.xRot -= hunch * 0.25F;
		armL.xRot -= hunch * 0.25F;
		armR.xRot += sprint * 0.35F; // the machete cocked back while running

		// ---------------------------------------------------------------- gait flavour
		switch (state.mode) {
			case ROAM -> {
				if (speed < 0.15F) {
					handR.xRot = Mth.sin(t * 0.11F) * 0.12F; // fidgeting with the blade
					float tilt = Mth.sin(t * 0.013F);
					head.zRot = tilt * tilt * tilt * tilt * tilt * tilt * tilt * 0.45F; // now and then, a slow tilt
				}
			}
			case STALK -> {
				armR.xRot -= 0.15F;
				forearmR.xRot -= 0.35F;
			}
			case SEARCH -> head.yRot += Mth.sin(t * 0.09F) * 0.75F;
			case CHASE -> {
			}
			case LURK -> {
				// Crouched in hiding, blade held close, head slowly turning, listening.
				armR.xRot = -0.35F;
				forearmR.xRot = -1.0F;
				armL.xRot = -0.2F;
				forearmL.xRot = -0.9F;
				head.yRot += Mth.sin(t * 0.03F) * 0.4F;
				head.xRot += 0.1F;
				coatBack.xRot = 0.5F;
			}
		}

		// ---------------------------------------------------------------- coat tails trail the legs
		coatFrontR.xRot = Math.min(0.0F, legR.xRot) * 0.85F;
		coatFrontL.xRot = Math.min(0.0F, legL.xRot) * 0.85F;
		coatBack.xRot = Math.max(Math.max(legR.xRot, legL.xRot), 0.0F) * 0.5F + speed * (0.15F + 0.45F * sprint) + Mth.sin(t * 0.11F) * 0.03F;

		// ---------------------------------------------------------------- actions
		switch (action) {
			case WINDUP -> {
				armR.xRot = key(a, 0, armR.xRot, 10, -2.9F);
				armR.zRot = key(a, 0, 0.08F, 10, -0.45F);
				forearmR.xRot = key(a, 0, forearmR.xRot, 10, -1.25F);
				torso.yRot = key(a, 0, torso.yRot, 10, -0.45F);
				torso.xRot = key(a, 0, torso.xRot, 10, torso.xRot - 0.15F);
				armL.xRot = key(a, 0, armL.xRot, 8, -0.7F);
				forearmL.xRot = key(a, 0, forearmL.xRot, 8, -0.8F);
				pelvis.z += key(a, 0, 0, 10, 0.8F);
				head.yRot += key(a, 0, 0, 10, 0.3F);
			}
			case SWING -> {
				armR.xRot = key(a, 0, -2.9F, 2.5F, 0.4F, 6, 0.75F);
				armR.yRot = key(a, 0, 0.0F, 3, -0.5F, 6, -0.35F);
				armR.zRot = key(a, 0, -0.45F, 3, 0.35F, 6, 0.2F);
				forearmR.xRot = key(a, 0, -1.25F, 2.5F, -0.05F, 6, -0.3F);
				torso.yRot = key(a, 0, -0.45F, 3, 0.6F, 6, 0.45F);
				torso.xRot = key(a, 0, torso.xRot - 0.15F, 3, torso.xRot + 0.4F, 6, torso.xRot + 0.25F);
				armL.xRot = key(a, 0, -0.7F, 3, 0.5F, 6, 0.3F);
				pelvis.z += key(a, 0, 0.8F, 3, -1.0F, 6, -0.6F);
				legR.xRot = key(a, 0, legR.xRot, 3, -0.45F, 6, -0.3F);
				shinR.xRot = key(a, 0, shinR.xRot, 3, 0.35F, 6, 0.25F);
			}
			case CROUCH -> {
				pelvis.y += key(a, 0, 0, 12, 3.5F);
				legR.xRot = key(a, 0, legR.xRot, 12, -1.15F);
				legL.xRot = key(a, 0, legL.xRot, 12, -0.9F);
				shinR.xRot = key(a, 0, shinR.xRot, 12, 1.75F);
				shinL.xRot = key(a, 0, shinL.xRot, 12, 1.5F);
				torso.xRot = key(a, 0, torso.xRot, 12, 0.85F);
				neck.xRot = key(a, 0, neck.xRot, 12, -0.6F);
				head.xRot = key(a, 0, head.xRot, 12, -0.5F);
				armR.xRot = key(a, 0, armR.xRot, 12, 0.95F);
				armL.xRot = key(a, 0, armL.xRot, 12, 0.95F);
				armR.zRot = key(a, 0, armR.zRot, 12, 0.35F);
				armL.zRot = key(a, 0, armL.zRot, 12, -0.35F);
				coatBack.xRot = key(a, 0, coatBack.xRot, 12, 0.35F);
			}
			case LEAP -> {
				legR.xRot = key(a, 0, -1.0F, 6, -1.2F, 16, -0.5F);
				legL.xRot = key(a, 0, 0.6F, 6, 0.8F, 16, 0.2F);
				shinR.xRot = key(a, 0, 1.0F, 6, 0.4F, 16, 0.6F);
				shinL.xRot = key(a, 0, 0.3F, 6, 0.9F, 16, 0.4F);
				torso.xRot = key(a, 0, 0.5F, 8, 0.25F, 16, 0.55F);
				armR.xRot = key(a, 0, -2.4F, 8, -3.0F, 12, 0.5F, 16, 0.6F); // raised, then the cut on the way down
				forearmR.xRot = key(a, 0, -0.6F, 8, -1.1F, 12, -0.1F, 16, -0.3F);
				armL.xRot = key(a, 0, -1.6F, 8, -1.9F, 16, 0.2F);
				armL.zRot = -0.3F;
				coatBack.xRot = key(a, 0, 0.9F, 8, 1.2F, 16, 0.5F);
				coatFrontR.xRot = 0.4F;
				coatFrontL.xRot = 0.4F;
			}
			case STUN -> {
				float wobble = 1.0F - Mth.clamp(a / 30.0F, 0.0F, 1.0F);
				torso.xRot = key(a, 0, 0.2F, 5, 0.75F, 30, 0.35F);
				neck.xRot = key(a, 0, 0.0F, 5, 0.45F, 30, 0.1F);
				head.xRot = 0.35F;
				head.zRot = Mth.sin(a * 0.45F) * 0.28F * wobble;
				torso.zRot = Mth.sin(a * 0.3F) * 0.12F * wobble;
				pelvis.x += Mth.sin(a * 0.3F) * 0.8F * wobble;
				pelvis.y += key(a, 0, 0, 5, 1.8F, 30, 0.6F);
				legR.xRot = key(a, 0, 0, 5, -0.4F, 30, -0.1F);
				shinR.xRot = key(a, 0, 0, 5, 0.6F, 30, 0.2F);
				legL.xRot = 0.15F;
				armR.xRot = 0.25F;
				armL.xRot = 0.2F;
				armR.zRot = 0.3F + Mth.sin(a * 0.3F) * 0.1F * wobble;
				armL.zRot = -0.25F;
				forearmR.xRot = -0.1F;
				forearmL.xRot = -0.1F;
			}
			case SHRIEK -> {
				float shudder = a > 20.0F ? Mth.sin(a * 2.4F) : 0.0F;
				torso.xRot = key(a, 0, torso.xRot, 8, 0.35F, 20, -0.35F);
				neck.xRot = key(a, 0, neck.xRot, 20, -0.4F);
				head.xRot = key(a, 0, head.xRot, 20, -0.75F);
				head.yRot = shudder * 0.12F;
				torso.zRot = shudder * 0.04F;
				armR.xRot = key(a, 0, armR.xRot, 8, 0.5F, 20, -0.45F);
				armL.xRot = key(a, 0, armL.xRot, 8, 0.5F, 20, -0.45F);
				armR.zRot = key(a, 0, 0.08F, 8, 0.2F, 20, 1.45F) + shudder * 0.05F;
				armL.zRot = key(a, 0, -0.08F, 8, -0.2F, 20, -1.45F) - shudder * 0.05F;
				forearmR.xRot = key(a, 0, forearmR.xRot, 20, -0.2F);
				forearmL.xRot = key(a, 0, forearmL.xRot, 20, -0.2F);
				pelvis.y += key(a, 0, 0, 8, 1.5F, 20, 0.2F);
			}
			case BREACH -> {
				torso.xRot = key(a, 0, torso.xRot, 22, -0.3F, 25, 0.75F, 30, 0.6F);
				armR.xRot = key(a, 0, armR.xRot, 22, -3.05F, 25, 0.35F, 30, 0.4F);
				armL.xRot = key(a, 0, armL.xRot, 22, -3.05F, 25, 0.35F, 30, 0.4F);
				armR.zRot = key(a, 0, 0.08F, 22, -0.2F, 25, 0.05F);
				armL.zRot = key(a, 0, -0.08F, 22, 0.2F, 25, -0.05F);
				forearmR.xRot = key(a, 0, forearmR.xRot, 22, -0.6F, 25, -0.1F);
				forearmL.xRot = key(a, 0, forearmL.xRot, 22, -0.6F, 25, -0.1F);
				pelvis.y += key(a, 0, 0, 22, -0.6F, 25, 2.2F, 30, 1.6F);
				legR.xRot = key(a, 0, 0, 22, 0.1F, 25, -0.6F, 30, -0.5F);
				legL.xRot = key(a, 0, 0, 22, 0.1F, 25, -0.4F, 30, -0.3F);
				shinR.xRot = key(a, 0, 0, 25, 0.9F, 30, 0.7F);
				shinL.xRot = key(a, 0, 0, 25, 0.7F, 30, 0.5F);
			}
			case FRENZY -> {
				float thrash = Mth.sin(a * 1.6F);
				head.yRot = thrash * 0.45F;
				head.xRot = -0.45F;
				torso.zRot = Mth.sin(a * 1.1F) * 0.1F;
				torso.xRot = 0.35F;
				armR.zRot = 1.05F + Mth.sin(a * 1.3F) * 0.18F;
				armL.zRot = -1.05F - Mth.sin(a * 1.5F) * 0.18F;
				armR.xRot = -0.45F;
				armL.xRot = -0.45F;
				forearmR.xRot = -0.6F + Mth.sin(a * 1.7F) * 0.2F;
				forearmL.xRot = -0.6F + Mth.sin(a * 1.9F) * 0.2F;
			}
			case SCENT -> {
				neck.xRot = key(a, 0, neck.xRot, 8, -0.35F, 40, -0.3F);
				head.xRot = key(a, 0, head.xRot, 8, -0.45F, 40, -0.35F);
				head.yRot = Mth.sin(a * 0.22F) * 0.7F;
				torso.xRot = key(a, 0, torso.xRot, 8, 0.0F, 40, 0.05F);
			}
			case STARE -> {
				head.zRot = key(a, 0, 0, 18, 0.48F);
				head.xRot = key(a, 0, head.xRot, 18, -0.08F);
				head.yRot = yaw * 0.9F;
				neck.yRot = 0.0F;
				handR.xRot = Mth.sin(t * 0.08F) * 0.15F;
				legR.xRot = 0.0F;
				legL.xRot = 0.0F;
				shinR.xRot = hunch * 0.9F;
				shinL.xRot = hunch * 0.9F;
			}
			case WINDUP2 -> {
				// The backhand: blade drawn across the body to the far shoulder.
				armR.xRot = key(a, 0, armR.xRot, 10, -1.5F);
				armR.yRot = key(a, 0, 0.0F, 10, 0.9F);
				armR.zRot = key(a, 0, 0.08F, 10, 0.6F);
				forearmR.xRot = key(a, 0, forearmR.xRot, 10, -1.3F);
				torso.yRot = key(a, 0, torso.yRot, 10, 0.55F);
				armL.xRot = key(a, 0, armL.xRot, 8, -0.4F);
				pelvis.z += key(a, 0, 0, 10, 0.6F);
				head.yRot += key(a, 0, 0, 10, -0.25F);
			}
			case SWING2 -> {
				// A flat backhand sweep with the weight thrown through it.
				armR.xRot = key(a, 0, -1.5F, 3, -1.35F, 7, -0.6F);
				armR.yRot = key(a, 0, 0.9F, 3, -1.0F, 7, -0.7F);
				armR.zRot = key(a, 0, 0.6F, 3, -0.3F, 7, -0.1F);
				forearmR.xRot = key(a, 0, -1.3F, 3, -0.15F, 7, -0.4F);
				torso.yRot = key(a, 0, 0.55F, 3, -0.6F, 7, -0.4F);
				torso.xRot = key(a, 0, torso.xRot, 3, torso.xRot + 0.25F, 7, torso.xRot + 0.15F);
				armL.xRot = key(a, 0, -0.4F, 3, 0.45F, 7, 0.25F);
				pelvis.z += key(a, 0, 0.6F, 3, -0.9F, 7, -0.5F);
				legL.xRot = key(a, 0, legL.xRot, 3, -0.4F, 7, -0.25F);
				shinL.xRot = key(a, 0, shinL.xRot, 3, 0.3F, 7, 0.2F);
			}
			case NONE -> {
			}
		}
		if (state.climbing) {
			climb(t);
		}
		if (action == AsesinoEntity.Action.DIG) {
			dig(a);
		}
	}

	private static final net.minecraft.resources.Identifier ANIM = com.deadlands.Deadlands.id("models/entity/aberrant.anim");
	private ModelPart[] rigParts;
	private int[] rigBones;
	private RiggedAnimation rigBound;
	private float[] rigPose = new float[0];

	/**
	 * Digging through a block: the claw-and-chop loop keyed in Blender ({@code tools/assetgen/blender/aberrant_anims.py},
	 * exported to {@code models/entity/aberrant.anim}), eased in over a quarter second from whatever he was doing.
	 */
	private void dig(float age) {
		RiggedAnimation anim = RiggedAnimation.get(ANIM);
		RiggedAnimation.Clip clip = anim == null ? null : anim.clip("dig");
		if (clip == null) {
			return;
		}
		if (rigBound != anim) {
			rigBound = anim;
			rigParts = new ModelPart[] {pelvis, torso, neck, head, armR, forearmR, handR, armL, forearmL, handL, coatFrontR, coatFrontL,
				coatBack, legR, shinR, legL, shinL};
			rigBones = new int[rigParts.length];
			for (int i = 0; i < rigParts.length; i++) {
				rigBones[i] = anim.bone(AberrantGeometry.BONES[i]);
			}
			rigPose = new float[anim.stride];
		}
		anim.sample(clip, age, rigPose);
		float k = Mth.clamp(age / 5.0F, 0.0F, 1.0F);
		k = k * k * (3.0F - 2.0F * k);
		for (int i = 0; i < rigParts.length; i++) {
			int b = rigBones[i];
			if (b >= 0) {
				rigParts[i].xRot = Mth.lerp(k, rigParts[i].xRot, rigPose[b * 3]);
				rigParts[i].yRot = Mth.lerp(k, rigParts[i].yRot, rigPose[b * 3 + 1]);
				rigParts[i].zRot = Mth.lerp(k, rigParts[i].zRot, rigPose[b * 3 + 2]);
			}
		}
		int o = anim.bones.length * 3;
		pelvis.x += rigPose[o] * k;
		pelvis.y += rigPose[o + 1] * k;
		pelvis.z += rigPose[o + 2] * k;
	}

	/** Climbing: hand over hand up the wall, slowly, coat hanging, legs pushing; head tipped back to look up. */
	private void climb(float t) {
		float p = t * 0.2F;
		torso.xRot = -0.1F;
		armR.xRot = -2.7F + 0.35F * Mth.sin(p);
		armL.xRot = -2.7F - 0.35F * Mth.sin(p);
		armR.zRot = 0.2F;
		armL.zRot = -0.2F;
		forearmR.xRot = -0.3F - 0.3F * Math.max(0.0F, Mth.cos(p));
		forearmL.xRot = -0.3F - 0.3F * Math.max(0.0F, -Mth.cos(p));
		legR.xRot = -0.6F - 0.45F * Mth.sin(p);
		legL.xRot = -0.6F + 0.45F * Mth.sin(p);
		shinR.xRot = 0.8F + 0.3F * Mth.sin(p);
		shinL.xRot = 0.8F - 0.3F * Mth.sin(p);
		head.xRot = -0.5F;
		coatBack.xRot = 0.05F;
	}

	/**
	 * A keyframed value at time {@code a}: pairs of (tick, value), eased between them (smoothstep),
	 * held before the first and after the last.
	 */
	private static float key(float a, float... frames) {
		if (a <= frames[0]) {
			return frames[1];
		}
		for (int i = 2; i < frames.length; i += 2) {
			if (a <= frames[i]) {
				float t0 = frames[i - 2];
				float p = Mth.clamp((a - t0) / Math.max(1.0E-4F, frames[i] - t0), 0.0F, 1.0F);
				p = p * p * (3.0F - 2.0F * p);
				return Mth.lerp(p, frames[i - 1], frames[i + 1]);
			}
		}
		return frames[frames.length - 1];
	}
}
