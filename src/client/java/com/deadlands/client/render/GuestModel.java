package com.deadlands.client.render;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * The Guest moves like an animal: on all fours, its long arms as front legs.
 *
 * <p><b>Stance:</b> hips tipped forward and the back near level, head low and thrust forward, forearms
 * planted with the claws out in front, hind legs bunched under it, tail held out behind. It breathes
 * hard (the ribs heave), its head scans, its claws flex against the ground, its tail twitches.
 *
 * <p><b>Prowl:</b> a four-beat walk in diagonal pairs (right fore with left hind), the shoulders rolling,
 * the spine swaying, the head steady like a stalking cat's. <b>Charge:</b> a bounding gallop: both
 * forelegs reach and land together, then the hind legs drive, the spine coiling and stretching, the
 * whole body rising and dropping, tail streaming.
 *
 * <p><b>Actions</b> (keyframed in ticks): it rears up on its hind legs to scream (arms flung up and out,
 * jaw wide, shaking); swipes from a half-rear; for the grab it drops low, springs forward with both arms,
 * rears up holding the survivor, and slams them down; staggers after a miss; rears to knock and to rake a
 * wall; feeds with its face down at the ground, jaw working.
 *
 * <p>The rig: the arms hang straight down at rest (baked by the importer), so arms, legs, spine, neck,
 * jaw and tail all bend with X; Z spreads the limbs.
 */
public class GuestModel extends EntityModel<GuestRenderer.State> {
	// The stance (radians, pixels).
	private static final float PELVIS = 0.75F;
	private static final float TORSO = 0.85F;
	private static final float ARM = -1.75F;
	private static final float FOREARM = -1.1F;
	private static final float HAND = -0.4F;
	private static final float LEG = -1.0F;
	private static final float SHIN = 0.9F;
	private static final float LOW = 6.5F;

	private final ModelPart pelvis;
	private final ModelPart torso;
	private final ModelPart neck;
	private final ModelPart head;
	private final ModelPart jaw;
	private final ModelPart armR;
	private final ModelPart forearmR;
	private final ModelPart handR;
	private final ModelPart armL;
	private final ModelPart forearmL;
	private final ModelPart handL;
	private final ModelPart legR;
	private final ModelPart shinR;
	private final ModelPart legL;
	private final ModelPart shinL;
	private final ModelPart[] tail = new ModelPart[13];

	public GuestModel(ModelPart root) {
		super(root);
		ModelPart base = root.getChild("root");
		pelvis = base.getChild("pelvis");
		torso = pelvis.getChild("torso");
		neck = torso.getChild("neck");
		head = neck.getChild("head");
		jaw = head.getChild("jaw");
		armR = torso.getChild("arm_r");
		forearmR = armR.getChild("forearm_r");
		handR = forearmR.getChild("hand_r");
		armL = torso.getChild("arm_l");
		forearmL = armL.getChild("forearm_l");
		handL = forearmL.getChild("hand_l");
		legR = pelvis.getChild("leg_r");
		shinR = legR.getChild("shin_r");
		legL = pelvis.getChild("leg_l");
		shinL = legL.getChild("shin_l");
		ModelPart parent = pelvis;
		for (int i = 0; i < tail.length; i++) {
			tail[i] = parent.getChild("tail_" + i);
			parent = tail[i];
		}
	}

	@Override
	public void setupAnim(GuestRenderer.State state) {
		super.setupAnim(state);
		float t = state.ageInTicks;
		float speed = Math.min(1.0F, state.walkAnimationSpeed * 1.4F);
		float run = state.run;
		float walk = speed * (1.0F - run);
		float a = state.actionAge;

		stance(t, state);
		if (state.mode == com.deadlands.entity.GuestEntity.Mode.STALK) {
			// Stalking: belly almost to the ground, head low and level, jaw shut, tail still.
			pelvis.y += 2.5F;
			neck.xRot -= 0.2F;
			head.xRot += 0.25F;
			jaw.xRot = 0.04F;
		}
		prowl(state.walkAnimationPos * 0.62F, walk);
		if (run > 0.01F) {
			gallop(state.walkAnimationPos * 0.42F, run, t);
		}
		tail(t, speed, run);
		if (state.swimming) {
			swim(t);
		}
		action(state, a, t);
	}

	/** Swimming like a dog: body level in the water, head held up, forelegs paddling, hind legs kicking. */
	private void swim(float t) {
		float p = t * 0.45F;
		pelvis.xRot = 1.35F;
		torso.xRot = 0.15F;
		neck.xRot = -0.55F;
		head.xRot = -0.65F; // nose up, above the water
		jaw.xRot = 0.1F;
		armR.xRot = -1.9F + Mth.sin(p) * 0.6F;
		armL.xRot = -1.9F + Mth.sin(p + Mth.PI) * 0.6F;
		forearmR.xRot = -0.9F - Math.max(0.0F, Mth.cos(p)) * 0.8F;
		forearmL.xRot = -0.9F - Math.max(0.0F, Mth.cos(p + Mth.PI)) * 0.8F;
		legR.xRot = -0.3F + Mth.sin(p + 1.0F) * 0.5F;
		legL.xRot = -0.3F + Mth.sin(p + 1.0F + Mth.PI) * 0.5F;
		shinR.xRot = 0.6F;
		shinL.xRot = 0.6F;
		for (ModelPart part : tail) {
			part.xRot *= 0.3F; // trailing behind on the water
		}
	}

	/** Standing on all fours, breathing hard, looking, claws working. */
	private void stance(float t, GuestRenderer.State state) {
		float breath = Mth.sin(t * 0.16F);
		pelvis.xRot = PELVIS;
		pelvis.y += LOW + breath * 0.3F;
		torso.xRot = TORSO + breath * 0.04F;
		neck.xRot = -1.1F - breath * 0.03F;
		head.xRot = -0.2F + state.xRot * Mth.DEG_TO_RAD * 0.5F;
		head.yRot = state.yRot * Mth.DEG_TO_RAD * 0.75F + Mth.sin(t * 0.021F) * 0.25F;
		head.zRot = Mth.sin(t * 0.013F) * Mth.sin(t * 0.013F) * Mth.sin(t * 0.013F) * 0.3F; // now and then, a tilt
		jaw.xRot = 0.12F + Math.max(0.0F, Mth.sin(t * 0.07F)) * 0.18F;
		armR.xRot = ARM;
		armL.xRot = ARM;
		armR.zRot = 0.28F;
		armL.zRot = -0.28F;
		forearmR.xRot = FOREARM;
		forearmL.xRot = FOREARM;
		handR.xRot = HAND + Mth.sin(t * 0.19F) * 0.06F; // claws flexing against the ground
		handL.xRot = HAND + Mth.sin(t * 0.19F + 2.0F) * 0.06F;
		legR.xRot = LEG;
		legL.xRot = LEG;
		legR.zRot = 0.1F;
		legL.zRot = -0.1F;
		shinR.xRot = SHIN;
		shinL.xRot = SHIN;
	}

	/** A four-beat walk in diagonal pairs: right fore with left hind, left fore with right hind. */
	private void prowl(float p, float w) {
		if (w <= 0.001F) {
			return;
		}
		float fr = Mth.sin(p);
		float fl = Mth.sin(p + Mth.PI);
		armR.xRot += -fr * 0.4F * w;
		armL.xRot += -fl * 0.4F * w;
		forearmR.xRot += -Math.max(0.0F, Mth.cos(p)) * 0.7F * w; // the paw lifts and folds as it comes forward
		forearmL.xRot += -Math.max(0.0F, Mth.cos(p + Mth.PI)) * 0.7F * w;
		legL.xRot += -fr * 0.4F * w;
		legR.xRot += -fl * 0.4F * w;
		shinL.xRot += Math.max(0.0F, Mth.cos(p + 0.4F)) * 0.6F * w;
		shinR.xRot += Math.max(0.0F, Mth.cos(p + Mth.PI + 0.4F)) * 0.6F * w;
		// The shoulders roll and the spine sways; the head stays steady, like a stalking cat's.
		torso.zRot = Mth.sin(p) * 0.08F * w;
		torso.yRot = Mth.cos(p) * 0.06F * w;
		pelvis.zRot = -Mth.sin(p) * 0.05F * w;
		neck.zRot = -torso.zRot * 0.8F;
		head.yRot -= torso.yRot;
		pelvis.y -= Math.abs(Mth.sin(p * 2.0F)) * 0.6F * w;
	}

	/** A bounding gallop: forelegs reach and land together, hind legs drive, the spine coils and stretches. */
	private void gallop(float g, float r, float t) {
		float fore = Mth.sin(g);
		float hind = Mth.sin(g + 2.2F);
		float flex = Mth.cos(g + 1.1F);
		float lead = 0.12F;
		armR.xRot = Mth.lerp(r, armR.xRot, ARM - 0.15F - fore * 0.85F);
		armL.xRot = Mth.lerp(r, armL.xRot, ARM - 0.15F - Mth.sin(g + lead) * 0.85F);
		forearmR.xRot = Mth.lerp(r, forearmR.xRot, FOREARM + 0.15F - Math.max(0.0F, Mth.cos(g)) * 0.9F);
		forearmL.xRot = Mth.lerp(r, forearmL.xRot, FOREARM + 0.15F - Math.max(0.0F, Mth.cos(g + lead)) * 0.9F);
		legR.xRot = Mth.lerp(r, legR.xRot, LEG + 0.1F - hind * 0.9F);
		legL.xRot = Mth.lerp(r, legL.xRot, LEG + 0.1F - Mth.sin(g + 2.2F + lead) * 0.9F);
		shinR.xRot = Mth.lerp(r, shinR.xRot, SHIN + Math.max(0.0F, Mth.cos(g + 2.2F)) * 0.8F);
		shinL.xRot = Mth.lerp(r, shinL.xRot, SHIN + Math.max(0.0F, Mth.cos(g + 2.2F + lead)) * 0.8F);
		torso.xRot = Mth.lerp(r, torso.xRot, TORSO + 0.1F + flex * 0.18F); // the spine coils and stretches
		pelvis.xRot = Mth.lerp(r, pelvis.xRot, PELVIS + 0.05F - flex * 0.12F);
		neck.xRot = Mth.lerp(r, neck.xRot, -1.2F - flex * 0.12F);
		pelvis.y += r * (-Math.abs(Mth.sin(g)) * 1.6F + 0.3F); // rising and dropping with each bound
		torso.zRot *= 1.0F - r;
		jaw.xRot = Mth.lerp(r, jaw.xRot, 0.45F + Mth.sin(t * 0.6F) * 0.08F);
	}

	/** The tail: held out behind, twitching at the tip, swinging with the walk, streaming in a charge. */
	private void tail(float t, float speed, float run) {
		for (int i = 0; i < tail.length; i++) {
			float w = t * (0.09F + 0.2F * run) + i * 0.5F;
			float tip = i / (float) tail.length;
			tail[i].xRot = (i == 0 ? -0.5F - 0.1F * run : -0.015F + 0.04F * Mth.sin(w * 0.8F) * (1.0F - run)) + 0.02F * speed;
			tail[i].yRot = Mth.sin(w) * (0.05F + 0.1F * tip + 0.04F * speed) * (1.0F - 0.6F * run);
		}
		if (Mth.sin(t * 0.07F) > 0.92F) {
			tail[12].yRot += Mth.sin(t * 1.4F) * 0.4F; // a flick
		}
	}

	private void action(GuestRenderer.State state, float a, float t) {
		switch (state.action) {
			case ROAR -> {
				// Rears up on its hind legs: arms flung up and out, head back, jaw wide, shaking with it.
				float up = key(a, 0, 0, 10, 1, 32, 1, 40, 0);
				float shake = a > 10.0F && a < 34.0F ? Mth.sin(a * 2.4F) : 0.0F;
				rear(up, 0.15F);
				neck.xRot = Mth.lerp(up, neck.xRot, -0.35F);
				head.xRot = Mth.lerp(up, head.xRot, -0.75F) + shake * 0.06F;
				head.yRot = Mth.lerp(up, head.yRot, shake * 0.14F);
				jaw.xRot = Mth.lerp(up, jaw.xRot, 1.05F);
				armR.xRot = Mth.lerp(up, armR.xRot, -2.4F);
				armL.xRot = Mth.lerp(up, armL.xRot, -2.4F);
				armR.zRot = Mth.lerp(up, armR.zRot, 0.75F) + shake * 0.06F;
				armL.zRot = Mth.lerp(up, armL.zRot, -0.75F) - shake * 0.06F;
				forearmR.xRot = Mth.lerp(up, forearmR.xRot, -0.7F);
				forearmL.xRot = Mth.lerp(up, forearmL.xRot, -0.7F);
			}
			case SWIPE_WINDUP -> {
				float up = key(a, 0, 0, 6, 0.55F);
				rear(up, 0.5F);
				armR.xRot = key(a, 0, armR.xRot, 6, -2.7F);
				armR.zRot = key(a, 0, armR.zRot, 6, 0.6F);
				forearmR.xRot = key(a, 0, forearmR.xRot, 6, -1.2F);
				torso.yRot = key(a, 0, 0.0F, 6, 0.35F);
				jaw.xRot = 0.6F;
			}
			case SWIPE -> {
				float up = key(a, 0, 0.55F, 3, 0.3F, 8, 0.0F);
				rear(up, 0.5F);
				armR.xRot = key(a, 0, -2.7F, 3, -1.0F, 8, ARM);
				armR.zRot = key(a, 0, 0.6F, 3, -0.35F, 8, 0.28F);
				forearmR.xRot = key(a, 0, -1.2F, 3, -0.3F, 8, FOREARM);
				torso.yRot = key(a, 0, 0.35F, 3, -0.45F, 8, 0.0F);
				jaw.xRot = 0.8F;
			}
			case GRAB_WINDUP -> {
				// Drops low, haunches coiled, arms spread.
				pelvis.y += key(a, 0, 0, 20, 3.5F);
				legR.xRot = key(a, 0, legR.xRot, 20, LEG - 0.35F);
				legL.xRot = key(a, 0, legL.xRot, 20, LEG - 0.35F);
				shinR.xRot = key(a, 0, shinR.xRot, 20, SHIN + 0.6F);
				shinL.xRot = key(a, 0, shinL.xRot, 20, SHIN + 0.6F);
				armR.zRot = key(a, 0, armR.zRot, 20, 0.7F);
				armL.zRot = key(a, 0, armL.zRot, 20, -0.7F);
				neck.xRot = key(a, 0, neck.xRot, 20, -1.35F);
				jaw.xRot = key(a, 0, jaw.xRot, 20, 0.9F);
				tail[0].xRot += key(a, 0, 0, 20, 0.4F);
			}
			case GRAB_LUNGE -> {
				// Springs: both arms thrown forward, hind legs fully extended.
				armR.xRot = key(a, 0, ARM, 4, -2.9F);
				armL.xRot = key(a, 0, ARM, 4, -2.9F);
				armR.zRot = 0.35F;
				armL.zRot = -0.35F;
				forearmR.xRot = -0.2F;
				forearmL.xRot = -0.2F;
				legR.xRot = LEG + 0.9F;
				legL.xRot = LEG + 0.9F;
				shinR.xRot = 0.2F;
				shinL.xRot = 0.2F;
				jaw.xRot = 1.05F;
				pelvis.y -= 1.5F;
			}
			case GRAB_HOLD -> {
				// Rears up with the survivor held high.
				float up = key(a, 0, 0.2F, 10, 1.0F);
				rear(up, 0.0F);
				armR.xRot = Mth.lerp(up, -2.9F, -2.85F);
				armL.xRot = Mth.lerp(up, -2.9F, -2.85F);
				armR.zRot = 0.2F;
				armL.zRot = -0.2F;
				forearmR.xRot = -0.8F;
				forearmL.xRot = -0.8F;
				head.xRot = -0.55F;
				jaw.xRot = 0.95F + Mth.sin(a * 1.6F) * 0.08F;
			}
			case SLAM -> {
				// Drives them down: body crashing forward, arms hammering to the ground.
				float up = key(a, 0, 1.0F, 4, 0.0F);
				rear(up, 0.0F);
				armR.xRot = key(a, 0, -2.85F, 4, -1.3F, 12, ARM);
				armL.xRot = key(a, 0, -2.85F, 4, -1.3F, 12, ARM);
				forearmR.xRot = key(a, 0, -0.8F, 4, -0.2F, 12, FOREARM);
				forearmL.xRot = key(a, 0, -0.8F, 4, -0.2F, 12, FOREARM);
				pelvis.y += key(a, 0, 0, 4, 2.5F, 12, 1.0F);
				jaw.xRot = key(a, 0, 0.95F, 4, 1.05F, 12, 0.4F);
			}
			case STUN -> {
				float fade = 1.0F - Mth.clamp(a / 30.0F, 0.0F, 1.0F);
				head.zRot = Mth.sin(a * 0.55F) * 0.35F * fade;
				head.xRot = 0.3F;
				neck.xRot = -0.8F;
				torso.zRot = Mth.sin(a * 0.35F) * 0.15F * fade;
				pelvis.x += Mth.sin(a * 0.35F) * 1.0F * fade;
				armR.xRot = ARM + 0.35F;
				armL.xRot = ARM + 0.2F;
				forearmR.xRot = FOREARM + 0.5F;
				pelvis.y += 1.5F * fade;
				jaw.xRot = 0.5F;
			}
			case KNOCK -> {
				rear(0.6F, 0.4F);
				armR.xRot = key(a, 0, -2.6F, 5, -2.75F, 8, -1.8F, 14, -2.4F);
				armR.zRot = 0.3F;
				forearmR.xRot = key(a, 0, -1.3F, 5, -1.4F, 8, -0.2F, 14, -0.9F);
			}
			case TEAR -> {
				// Half-reared, raking the wall with both claws in turn.
				float pull = Mth.sin(a * 0.9F);
				rear(0.55F, 0.3F);
				armR.xRot = -2.3F - pull * 0.55F;
				armL.xRot = -2.3F + pull * 0.55F;
				armR.zRot = 0.35F;
				armL.zRot = -0.35F;
				forearmR.xRot = -0.6F + pull * 0.45F;
				forearmL.xRot = -0.6F - pull * 0.45F;
				jaw.xRot = 0.8F;
			}
			case FEED -> {
				// Face down at the body, hunched over it, jaw working, tail low.
				neck.xRot = -0.2F;
				head.xRot = 0.55F + Mth.sin(a * 0.6F) * 0.15F;
				head.yRot = Mth.sin(a * 0.35F) * 0.25F;
				jaw.xRot = 0.3F + Math.max(0.0F, Mth.sin(a * 0.95F)) * 0.75F;
				torso.xRot = TORSO + 0.3F;
				pelvis.y += 2.0F;
				armR.xRot = ARM + Mth.sin(a * 0.45F) * 0.25F;
				armL.xRot = ARM - Mth.sin(a * 0.45F + 1.0F) * 0.25F;
				tail[0].xRot -= 0.3F;
			}
			case NONE -> {
			}
		}
	}

	/**
	 * Rearing up on the hind legs, by {@code amount} (0 on all fours, 1 upright). {@code lean} keeps some
	 * forward lean at full height.
	 */
	private void rear(float amount, float lean) {
		pelvis.xRot = Mth.lerp(amount, pelvis.xRot, lean * 0.4F);
		torso.xRot = Mth.lerp(amount, torso.xRot, lean * 0.6F);
		neck.xRot = Mth.lerp(amount, neck.xRot, -0.3F);
		legR.xRot = Mth.lerp(amount, legR.xRot, -0.35F);
		legL.xRot = Mth.lerp(amount, legL.xRot, -0.35F);
		shinR.xRot = Mth.lerp(amount, shinR.xRot, 0.45F);
		shinL.xRot = Mth.lerp(amount, shinL.xRot, 0.45F);
		pelvis.y -= amount * (LOW - 1.0F);
		tail[0].xRot = Mth.lerp(amount, tail[0].xRot, -0.15F); // the tail props it up
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
