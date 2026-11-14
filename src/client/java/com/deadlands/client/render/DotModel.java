package com.deadlands.client.render;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * D.O.T.'s animations: deliberate, heavy, wrong.
 *
 * <p>Everything is driven by the render state's frame-interpolated time ({@code ageInTicks}, {@code walkAnimationPos}
 * and the eased blends from the entity), so it moves at the full frame rate, not in 20 steps a second, and every
 * change of state blends rather than snaps: walking into the provoked walk, onto a wall and off it, into and out of
 * each action (each keyed with smoothstep easing in and out).
 *
 * <ul>
 *   <li><b>Idle</b>: almost still. A slow breath in the chest, the cloak settling, the head turning a little too
 *       smoothly to follow you, and now and then a twitch: the head snaps to one side for three frames and back, a hand
 *       jerks.</li>
 *   <li><b>Walk</b>: slow, long, heavy steps; the arms hang and barely swing; the head stays perfectly level while the
 *       body moves under it; the cloak drags behind with the step, lagging.</li>
 *   <li><b>Provoked walk</b>: leaning in, head low and forward, the blade raised and held out, the burning hand
 *       reaching, longer strides.</li>
 *   <li><b>Slash</b>: the blade drawn high and back, held, then brought across and down, following through.</li>
 *   <li><b>Error Grasp</b>: the wind-up is a hand rising, slowly, palm out, the body leaning back, shivering with static;
 *       the grasp thrusts it forward and holds.</li>
 *   <li><b>Static Step</b>: he arrives twisted, the torso turned wrong and the head tilted, and unwinds.</li>
 *   <li><b>Restore Point</b>: arms flung out, then pulled back in reverse, like a tape rewinding.</li>
 *   <li><b>Corruption Field</b>: arms lifting out to the sides, head back.</li>
 *   <li><b>Climbing</b>: hand over hand up the wall, slow, legs pushing.</li>
 *   <li><b>Loom</b> (Behind the Light): right in front of you, head tilted, one hand reaching for the light.</li>
 * </ul>
 * Minecraft models are rigid parts on pivots, so nothing bends inside a part: the cloak sways as two halves, the hands
 * and fingers can't close (the hand is the end of the arm), and the face can't change.
 */
public class DotModel extends EntityModel<DotRenderer.State> {
	private final ModelPart pelvis;
	private final ModelPart torso;
	private final ModelPart head;
	private final ModelPart armR;
	private final ModelPart armL;
	private final ModelPart blade;
	private final ModelPart legR;
	private final ModelPart legL;
	private final ModelPart cloakR;
	private final ModelPart cloakL;

	public DotModel(ModelPart root) {
		super(root);
		ModelPart base = root.getChild("root");
		pelvis = base.getChild("pelvis");
		torso = pelvis.getChild("torso");
		head = torso.getChild("head");
		armR = torso.getChild("arm_r");
		armL = torso.getChild("arm_l");
		blade = armL.getChild("blade");
		legR = pelvis.getChild("leg_r");
		legL = pelvis.getChild("leg_l");
		cloakR = torso.getChild("cloak_r");
		cloakL = torso.getChild("cloak_l");
	}

	@Override
	public void setupAnim(DotRenderer.State state) {
		super.setupAnim(state);
		float t = state.ageInTicks;
		float a = state.aggro;
		float speed = Math.min(1.0F, state.walkAnimationSpeed * 1.6F);
		float pos = state.walkAnimationPos;
		idle(state, t, a);
		walk(pos, speed, a, t);
		cloak(t, speed, a);
		if (state.climb > 0.01F) {
			climb(t, state.climb);
		}
		action(state, state.actionAge, t);
		twitch(state, t);
	}

	/** Breathing, head following, the provoked lean, blended by {@code a}. */
	private void idle(DotRenderer.State state, float t, float a) {
		float breath = Mth.sin(t * 0.07F);
		torso.xRot = 0.03F * breath + Mth.lerp(a, 0.0F, 0.28F);
		torso.yRot = 0.0F;
		torso.zRot = 0.0F;
		pelvis.y += 0.15F * breath;
		// The head follows a little too smoothly, then holds.
		head.yRot = state.yRot * Mth.DEG_TO_RAD * 0.8F;
		head.xRot = state.xRot * Mth.DEG_TO_RAD * 0.6F + Mth.lerp(a, 0.0F, 0.18F) - torso.xRot * 0.6F;
		head.zRot = 0.0F;
		armR.xRot = Mth.lerp(a, 0.05F, -0.6F) + 0.03F * Mth.sin(t * 0.05F + 1.0F);
		armR.yRot = 0.0F;
		armR.zRot = Mth.lerp(a, 0.06F, 0.15F);
		armL.xRot = Mth.lerp(a, 0.05F, -1.05F) + 0.03F * Mth.sin(t * 0.05F);
		armL.yRot = Mth.lerp(a, 0.0F, -0.25F);
		armL.zRot = Mth.lerp(a, -0.06F, -0.1F);
		blade.xRot = 0.0F;
		blade.yRot = 0.0F;
		blade.zRot = 0.0F;
		legR.xRot = 0.0F;
		legL.xRot = 0.0F;
		legR.zRot = 0.02F;
		legL.zRot = -0.02F;
	}

	/** Long, slow, heavy steps; the head stays level while the body moves under it. */
	private void walk(float p, float w, float a, float t) {
		if (w <= 0.01F) {
			return;
		}
		float stride = Mth.lerp(a, 0.42F, 0.62F);
		float phase = p * 0.42F;
		float s = Mth.sin(phase);
		legR.xRot += -s * stride * w;
		legL.xRot += s * stride * w;
		// Weight onto each foot: the hips drop and roll a little as it lands.
		pelvis.y += Math.abs(Mth.cos(phase)) * 0.7F * w;
		pelvis.zRot = s * 0.04F * w;
		torso.yRot = -s * 0.06F * w;            // the spine turns against the step
		torso.zRot = -pelvis.zRot * 0.6F;
		head.yRot -= torso.yRot;                 // and the head doesn't: it stays on you
		head.zRot = -(pelvis.zRot + torso.zRot);
		// The arms barely swing; provoked, they don't at all.
		armR.xRot += s * 0.12F * w * (1.0F - a);
		armL.xRot += -s * 0.08F * w * (1.0F - a);
	}

	/** The cloak's two halves drag behind the step and settle, lagging the body. */
	private void cloak(float t, float w, float a) {
		float lag = Mth.sin(t * 0.09F - 0.8F);
		float drag = 0.18F + 0.25F * w + 0.1F * a;
		cloakR.xRot = drag + 0.05F * lag;
		cloakL.xRot = drag + 0.05F * Mth.sin(t * 0.09F - 1.3F);
		cloakR.yRot = 0.06F * lag;
		cloakL.yRot = -0.06F * Mth.sin(t * 0.09F - 1.1F);
		cloakR.zRot = 0.0F;
		cloakL.zRot = 0.0F;
	}

	/** Hand over hand up the wall, slowly, legs pushing. */
	private void climb(float t, float c) {
		float p = t * 0.22F;
		torso.xRot = Mth.lerp(c, torso.xRot, -0.15F);
		armR.xRot = Mth.lerp(c, armR.xRot, -2.6F + 0.35F * Mth.sin(p));
		armL.xRot = Mth.lerp(c, armL.xRot, -2.6F - 0.35F * Mth.sin(p));
		armR.zRot = Mth.lerp(c, armR.zRot, 0.25F);
		armL.zRot = Mth.lerp(c, armL.zRot, -0.25F);
		legR.xRot = Mth.lerp(c, legR.xRot, -0.5F - 0.4F * Mth.sin(p));
		legL.xRot = Mth.lerp(c, legL.xRot, -0.5F + 0.4F * Mth.sin(p));
		head.xRot = Mth.lerp(c, head.xRot, -0.45F);
	}

	private void action(DotRenderer.State state, float a, float t) {
		switch (state.action) {
			case SWING -> {
				// Drawn high and back, held, then across and down, following through.
				armL.xRot = key(a, 0, armL.xRot, 7, -2.8F, 9, -2.85F, 12, -0.3F, 18, armL.xRot);
				armL.zRot = key(a, 0, armL.zRot, 7, -0.55F, 12, 0.35F, 18, armL.zRot);
				armL.yRot = key(a, 0, armL.yRot, 7, 0.3F, 12, -0.7F, 18, armL.yRot);
				torso.yRot += key(a, 0, 0.0F, 7, 0.35F, 12, -0.45F, 18, 0.0F);
				blade.xRot = key(a, 0, 0.0F, 7, -0.4F, 12, 0.3F, 18, 0.0F);
			}
			case GRASP_WINDUP -> {
				// A hand rising, slowly, palm out; leaning back; shivering with static.
				float rise = key(a, 0, 0.0F, 20, 1.0F);
				float shiver = Mth.sin(t * 3.1F) * 0.03F * rise;
				armR.xRot = Mth.lerp(rise, armR.xRot, -1.55F) + shiver;
				armR.zRot = Mth.lerp(rise, armR.zRot, -0.1F);
				torso.xRot = Mth.lerp(rise, torso.xRot, -0.12F) + shiver;
				head.zRot += 0.25F * rise + shiver * 2.0F;
			}
			case GRASP -> {
				float thrust = key(a, 0, 0.0F, 3, 1.0F, 26, 1.0F, 30, 0.0F);
				armR.xRot = Mth.lerp(thrust, armR.xRot, -1.7F);
				armR.zRot = Mth.lerp(thrust, armR.zRot, -0.05F);
				torso.xRot = Mth.lerp(thrust, torso.xRot, 0.35F);
				head.xRot = Mth.lerp(thrust, head.xRot, 0.2F);
			}
			case STATIC_STEP -> {
				// Arrives twisted, and unwinds.
				float wrong = key(a, 0, 1.0F, 10, 0.0F);
				torso.yRot += 0.7F * wrong;
				head.zRot += -0.6F * wrong;
				head.yRot += 0.5F * wrong;
				armR.zRot += 0.5F * wrong;
			}
			case RESTORE -> {
				// Flung out, then pulled back in reverse.
				float out = key(a, 0, 0.0F, 4, 1.0F, 16, 0.0F);
				armR.zRot = Mth.lerp(out, armR.zRot, 1.3F);
				armL.zRot = Mth.lerp(out, armL.zRot, -1.3F);
				head.xRot = Mth.lerp(out, head.xRot, -0.5F);
				torso.xRot = Mth.lerp(out, torso.xRot, -0.15F);
			}
			case FIELD -> {
				float lift = key(a, 0, 0.0F, 10, 1.0F, 24, 0.4F);
				armR.zRot = Mth.lerp(lift, armR.zRot, 1.45F);
				armL.zRot = Mth.lerp(lift, armL.zRot, -1.45F);
				armR.xRot = Mth.lerp(lift, armR.xRot, -0.3F);
				armL.xRot = Mth.lerp(lift, armL.xRot, -0.3F);
				head.xRot = Mth.lerp(lift, head.xRot, -0.6F);
			}
			case TWITCH -> {
				// Something in him skips: the head snaps aside for three frames, a hand jerks.
				float snap = a < 3.0F ? 1.0F : key(a, 3, 1.0F, 6, 0.0F);
				head.zRot += 0.55F * snap * (state.seed % 2 == 0 ? 1.0F : -1.0F);
				head.yRot += 0.3F * snap;
				armR.xRot += -0.35F * key(a, 0, 0.0F, 2, 1.0F, 5, 0.0F);
			}
			case LOOM -> {
				// Right there when the light comes back: head tilted, one hand reaching for it.
				float in = key(a, 0, 1.0F, 30, 1.0F, 40, 0.0F);
				head.zRot += 0.55F * in;
				head.xRot = Mth.lerp(in, head.xRot, 0.15F);
				armR.xRot = Mth.lerp(in, armR.xRot, -1.35F);
				armR.yRot = Mth.lerp(in, armR.yRot, 0.25F);
				torso.xRot = Mth.lerp(in, torso.xRot, 0.2F);
			}
			case NONE -> {
			}
		}
	}

	/**
	 * Unprompted twitches: every so often (when his own pattern says so) the head jerks for a few frames, or the blade
	 * hand shivers. Rare enough to be noticed.
	 */
	private void twitch(DotRenderer.State state, float t) {
		int beat = Mth.floor(t / 23.0F);
		int roll = Math.floorMod(beat * 92821 + state.seed * 68917, 100);
		float into = t - beat * 23.0F;
		if (roll < 6 && into < 3.0F) {
			head.zRot += 0.35F * (roll % 2 == 0 ? 1.0F : -1.0F);
			head.xRot += 0.12F;
		} else if (roll < 10 && into < 4.0F) {
			armL.zRot += 0.12F * Mth.sin(into * 9.0F);
		}
	}

	/** A keyframed value at {@code a}: (tick, value) pairs, eased between (smoothstep), held outside. */
	private static float key(float a, float... f) {
		if (a <= f[0]) {
			return f[1];
		}
		for (int i = 2; i < f.length; i += 2) {
			if (a <= f[i]) {
				float p = Mth.clamp((a - f[i - 2]) / Math.max(1.0E-4F, f[i] - f[i - 2]), 0.0F, 1.0F);
				p = p * p * (3.0F - 2.0F * p);
				return Mth.lerp(p, f[i - 1], f[i + 1]);
			}
		}
		return f[f.length - 1];
	}
}
