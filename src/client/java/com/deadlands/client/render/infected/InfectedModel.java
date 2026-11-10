package com.deadlands.client.render.infected;

import com.deadlands.entity.InfectedKind;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.AnimationState;
import org.jspecify.annotations.Nullable;

/**
 * Shared animation for both infected rigs (see {@link InfectedGeometry}).
 *
 * <p>Each frame: the variant's optional parts are shown or hidden, the kind's procedural pose is
 * built (a shamble, a run, a heavy lumber, a crouched stalk, a crawl, a waddle), with small
 * per-infected asymmetries so a crowd never moves in step; then the keyframed actions
 * ({@link InfectedAnimations}) are blended on top. While an action drives the arms, the
 * procedural arm pose fades out under it.
 */
public abstract class InfectedModel extends EntityModel<InfectedRenderState> {
	protected final ModelPart body;
	protected final ModelPart torso;
	protected final ModelPart chest;
	protected final ModelPart head;
	protected final ModelPart jaw;
	protected final ModelPart helmet;
	protected final ModelPart rightArm;
	protected final ModelPart rightForearm;
	protected final ModelPart leftArm;
	protected final ModelPart leftForearm;
	protected final ModelPart rightLeg;
	protected final ModelPart rightShin;
	protected final ModelPart leftLeg;
	protected final ModelPart leftShin;

	private final KeyframeAnimation attack;
	private final KeyframeAnimation slam;
	private final KeyframeAnimation scream;
	private final KeyframeAnimation pounce;
	private final KeyframeAnimation breach;
	private final KeyframeAnimation emerge;
	private final @Nullable KeyframeAnimation swell;

	protected InfectedModel(ModelPart root) {
		super(root, RenderTypes::entityCutoutNoCull);
		this.body = root.getChild("body");
		this.torso = body.getChild("torso");
		this.chest = torso.getChild("chest");
		this.head = chest.getChild("head");
		this.jaw = head.getChild("jaw");
		this.helmet = head.getChild("helmet");
		this.rightArm = chest.getChild("right_arm");
		this.rightForearm = rightArm.getChild("right_forearm");
		this.leftArm = chest.getChild("left_arm");
		this.leftForearm = leftArm.getChild("left_forearm");
		this.rightLeg = body.getChild("right_leg");
		this.rightShin = rightLeg.getChild("right_shin");
		this.leftLeg = body.getChild("left_leg");
		this.leftShin = leftLeg.getChild("left_shin");
		this.attack = InfectedAnimations.ATTACK.bake(root);
		this.slam = InfectedAnimations.SLAM.bake(root);
		this.scream = InfectedAnimations.SCREAM.bake(root);
		this.pounce = InfectedAnimations.POUNCE.bake(root);
		this.breach = InfectedAnimations.BREACH.bake(root);
		this.emerge = InfectedAnimations.EMERGE.bake(root);
		this.swell = root.createPartLookup().apply("belly") != null ? InfectedAnimations.SWELL.bake(root) : null;
	}

	@Override
	public void setupAnim(InfectedRenderState state) {
		super.setupAnim(state);
		showParts(state);
		float age = state.ageInTicks;
		float walk = state.walkAnimationPos;
		float speed = Math.min(1.0F, state.walkAnimationSpeed);

		switch (state.kind) {
			case RUNNER -> run(state, age, walk, speed);
			case BRUTE -> lumber(state, age, walk, speed);
			case CRAWLER -> crawl(state, age, walk, speed);
			case STALKER -> {
				if (state.climbing) {
					climb(age);
				} else {
					stalk(state, age, walk, speed);
				}
			}
			case BLOATER -> waddle(state, age, walk, speed);
			default -> shamble(state, age, walk, speed);
		}
		idle(state, age);
		extras(state, age, walk, speed);
		if (state.kind != InfectedKind.CRAWLER) {
			look(state);
			actions(state, age);
		}
	}

	/** Show or hide the variant's optional parts. */
	protected abstract void showParts(InfectedRenderState state);

	/** Rig-specific touches after the main pose (tatters, claws, belly). */
	protected void extras(InfectedRenderState state, float age, float walk, float speed) {
	}

	// ======================================================================================== poses

	/** The ordinary infected walk: dragging, uneven, a limp on one side for some, arms reaching or hanging. */
	private void shamble(InfectedRenderState state, float age, float walk, float speed) {
		float f = walk * 0.55F;
		boolean limpRight = state.seeded(1) < 0.15F;
		boolean limpLeft = !limpRight && state.seeded(1) < 0.3F;
		boolean stiff = state.kind == InfectedKind.CHARRED;
		float knee = stiff ? 0.5F : 1.1F;
		rightLeg.xRot = Mth.cos(f) * 1.0F * speed * (limpRight ? 0.5F : 1.0F);
		leftLeg.xRot = Mth.cos(f + Mth.PI) * 1.0F * speed * (limpLeft ? 0.5F : 1.0F);
		rightShin.xRot = 0.05F + Math.max(0.0F, -Mth.sin(f)) * knee * speed;
		leftShin.xRot = 0.05F + Math.max(0.0F, Mth.sin(f)) * knee * speed;
		if (limpRight) {
			rightLeg.yRot = 0.25F;
		} else if (limpLeft) {
			leftLeg.yRot = -0.25F;
		}
		body.y += Math.abs(Mth.sin(f)) * 0.9F * speed;
		body.zRot = Mth.sin(f) * 0.07F * speed + (state.seeded(2) - 0.5F) * 0.08F;
		torso.xRot = 0.12F + 0.06F * speed;
		torso.yRot = Mth.sin(f) * 0.12F * speed;
		head.xRot = 0.1F;
		head.zRot = (state.seeded(3) - 0.5F) * 0.5F + Mth.sin(age * 0.05F + state.seed) * 0.05F;

		// 0: both arms hang, 1: the right reaches, 2: both reach. Screamers keep their arms in.
		int reach = state.kind == InfectedKind.SCREAMER ? 0 : (int) (state.seeded(4) * 3.0F);
		if (state.kind == InfectedKind.MILITARY && reach == 2) {
			reach = 1;
		}
		float sway = Mth.sin(age * 0.07F + state.seed) * 0.08F;
		if (reach >= 1) {
			rightArm.xRot = -1.3F + sway + Mth.cos(f) * 0.15F * speed;
			rightArm.zRot = 0.08F;
			rightForearm.xRot = -0.25F;
		} else {
			hangArm(rightArm, rightForearm, f + Mth.PI, speed, 0.12F);
		}
		if (reach == 2) {
			leftArm.xRot = -1.2F - sway + Mth.cos(f + Mth.PI) * 0.15F * speed;
			leftArm.zRot = -0.08F;
			leftForearm.xRot = -0.3F;
		} else {
			hangArm(leftArm, leftForearm, f, speed, -0.12F);
		}
		if (state.kind == InfectedKind.SCREAMER && !state.screaming) {
			head.zRot += Mth.sin(age * 1.7F) * 0.06F;
			rightArm.zRot = 0.04F;
			leftArm.zRot = -0.04F;
		}
	}

	private static void hangArm(ModelPart arm, ModelPart forearm, float phase, float speed, float out) {
		arm.xRot = -0.15F + Mth.cos(phase) * 0.35F * speed;
		arm.zRot = out;
		forearm.xRot = -0.15F - 0.2F * speed;
	}

	/** Runners: a flat-out sprint, leaning in, arms pumping; standing, a restless twitch. */
	private void run(InfectedRenderState state, float age, float walk, float speed) {
		float sprint = state.sprinting ? 1.0F : Mth.clamp(speed * 1.6F - 0.3F, 0.0F, 1.0F);
		float f = walk * 0.6662F;
		rightLeg.xRot = Mth.cos(f) * 1.3F * speed;
		leftLeg.xRot = Mth.cos(f + Mth.PI) * 1.3F * speed;
		rightShin.xRot = 0.1F + Math.max(0.0F, -Mth.sin(f)) * 1.4F * speed;
		leftShin.xRot = 0.1F + Math.max(0.0F, Mth.sin(f)) * 1.4F * speed;
		body.y += Math.abs(Mth.cos(f)) * 1.2F * speed;
		torso.xRot = 0.12F + 0.25F * sprint;
		head.xRot = 0.05F - 0.25F * sprint;
		rightArm.xRot = Mth.cos(f + Mth.PI) * 1.1F * speed - 0.3F * sprint - 0.6F * (1.0F - sprint);
		leftArm.xRot = Mth.cos(f) * 1.1F * speed - 0.3F * sprint - 0.5F * (1.0F - sprint);
		rightArm.zRot = 0.1F;
		leftArm.zRot = -0.1F;
		rightForearm.xRot = -0.4F - 0.7F * sprint;
		leftForearm.xRot = -0.4F - 0.7F * sprint;
		// Never quite still: small jerks of the head and shoulders.
		float jerk = Mth.sin(age * 0.9F + state.seed) * Mth.sin(age * 0.23F) * (1.0F - sprint);
		head.yRot += jerk * 0.25F;
		chest.zRot = jerk * 0.05F;
	}

	/** Brutes: a slow, heavy, wide-legged lumber with the arms swinging low. */
	private void lumber(InfectedRenderState state, float age, float walk, float speed) {
		float f = walk * 0.45F;
		rightLeg.xRot = Mth.cos(f) * 0.7F * speed;
		leftLeg.xRot = Mth.cos(f + Mth.PI) * 0.7F * speed;
		rightShin.xRot = 0.08F + Math.max(0.0F, -Mth.sin(f)) * 0.6F * speed;
		leftShin.xRot = 0.08F + Math.max(0.0F, Mth.sin(f)) * 0.6F * speed;
		rightLeg.zRot = 0.08F;
		leftLeg.zRot = -0.08F;
		body.y += Math.abs(Mth.sin(f)) * 1.2F * speed;
		body.zRot = Mth.sin(f) * 0.08F * speed;
		torso.xRot = 0.2F;
		torso.yRot = Mth.sin(f) * 0.1F * speed;
		head.xRot = -0.15F;
		rightArm.xRot = 0.05F + Mth.cos(f + Mth.PI) * 0.45F * speed;
		leftArm.xRot = 0.05F + Mth.cos(f) * 0.45F * speed;
		rightArm.zRot = 0.15F;
		leftArm.zRot = -0.15F;
		rightForearm.xRot = -0.25F;
		leftForearm.xRot = -0.3F;
		float breath = Mth.sin(age * 0.06F + state.seed);
		chest.xScale = 1.0F + 0.025F * breath;
		chest.zScale = 1.0F + 0.03F * breath;
	}

	/**
	 * Crawlers drag themselves along on their arms, face down, legs trailing. The rig is laid flat
	 * (the body pitched 90 degrees), so the head turns about the body's long axis; attacks are a
	 * lunge and emerging is a slow rise, both done here rather than with the standing keyframes.
	 */
	private void crawl(InfectedRenderState state, float age, float walk, float speed) {
		float f = walk * 0.5F + age * 0.01F;
		body.xRot = Mth.HALF_PI;
		body.y = 21.0F;
		body.z = 5.0F;
		body.zRot = Mth.sin(f) * 0.08F * Math.max(speed, 0.2F);
		float pull = Math.max(speed, 0.15F);
		rightArm.xRot = Mth.PI + 0.7F - 0.5F * Mth.sin(f) * pull;
		leftArm.xRot = Mth.PI + 0.7F - 0.5F * Mth.sin(f + Mth.PI) * pull;
		rightArm.zRot = -0.25F;
		leftArm.zRot = 0.25F;
		rightForearm.xRot = -0.6F - 0.3F * Math.max(0.0F, Mth.cos(f)) * pull;
		leftForearm.xRot = -0.6F - 0.3F * Math.max(0.0F, Mth.cos(f + Mth.PI)) * pull;
		rightLeg.xRot = Mth.sin(f) * 0.15F * speed;
		leftLeg.xRot = Mth.sin(f + Mth.PI) * 0.15F * speed;
		rightLeg.zRot = 0.12F;
		leftLeg.zRot = -0.18F;
		rightShin.xRot = 0.15F;
		leftShin.xRot = 0.35F;
		head.xRot = -1.15F + Mth.clamp(state.xRot, -30.0F, 30.0F) * Mth.DEG_TO_RAD;
		head.zRot = -Mth.clamp(state.yRot, -60.0F, 60.0F) * Mth.DEG_TO_RAD * 0.8F;

		float lunge = progress(state.attackAnimationState, age, 0.5F);
		if (lunge >= 0.0F) {
			float k = Mth.sin(lunge * Mth.PI);
			rightArm.xRot = Mth.lerp(k, rightArm.xRot, Mth.PI + 0.05F);
			leftArm.xRot = Mth.lerp(k * 0.6F, leftArm.xRot, Mth.PI + 0.15F);
			head.xRot -= 0.25F * k;
			jaw.xRot = 0.7F * k;
			body.z -= 2.5F * k;
		}
		float rise = progress(state.emergeAnimationState, age, 1.4F);
		if (rise >= 0.0F) {
			body.y += 6.0F * (1.0F - rise);
		}
	}

	/** Stalkers: crouched low, hunched, arms forward with the claws out. */
	private void stalk(InfectedRenderState state, float age, float walk, float speed) {
		float f = walk * 0.6F;
		body.y = 15.0F;
		rightLeg.xRot = -0.75F + Mth.cos(f) * 0.6F * speed;
		leftLeg.xRot = -0.75F + Mth.cos(f + Mth.PI) * 0.6F * speed;
		rightShin.xRot = 1.3F + Math.max(0.0F, -Mth.sin(f)) * 0.5F * speed;
		leftShin.xRot = 1.3F + Math.max(0.0F, Mth.sin(f)) * 0.5F * speed;
		rightLeg.zRot = 0.1F;
		leftLeg.zRot = -0.1F;
		torso.xRot = 0.55F;
		chest.xRot = 0.2F;
		head.xRot = -0.75F;
		rightArm.xRot = -1.0F + Mth.cos(f + Mth.PI) * 0.4F * speed + Mth.sin(age * 0.09F) * 0.05F;
		leftArm.xRot = -1.0F + Mth.cos(f) * 0.4F * speed - Mth.sin(age * 0.09F) * 0.05F;
		rightArm.zRot = 0.12F;
		leftArm.zRot = -0.12F;
		rightForearm.xRot = -0.6F;
		leftForearm.xRot = -0.6F;
	}

	/** A stalker on a wall: reaching up hand over hand, legs pushing. */
	private void climb(float age) {
		float c = Mth.sin(age * 0.4F);
		torso.xRot = -0.1F;
		rightArm.xRot = -2.6F + c * 0.5F;
		leftArm.xRot = -2.6F - c * 0.5F;
		rightForearm.xRot = -0.4F;
		leftForearm.xRot = -0.4F;
		rightLeg.xRot = -0.6F + c * 0.4F;
		leftLeg.xRot = -0.6F - c * 0.4F;
		rightShin.xRot = 0.8F;
		leftShin.xRot = 0.8F;
		head.xRot = -0.4F;
	}

	/** Bloaters: short waddling steps under the weight, leaning back, arms held out from the belly. */
	private void waddle(InfectedRenderState state, float age, float walk, float speed) {
		float f = walk * 0.45F;
		rightLeg.xRot = Mth.cos(f) * 0.6F * speed;
		leftLeg.xRot = Mth.cos(f + Mth.PI) * 0.6F * speed;
		rightShin.xRot = 0.05F + Math.max(0.0F, -Mth.sin(f)) * 0.5F * speed;
		leftShin.xRot = 0.05F + Math.max(0.0F, Mth.sin(f)) * 0.5F * speed;
		rightLeg.zRot = 0.1F;
		leftLeg.zRot = -0.1F;
		body.zRot = Mth.sin(f) * 0.16F * speed + Mth.sin(age * 0.05F) * 0.03F;
		torso.xRot = -0.08F;
		head.xRot = 0.15F;
		rightArm.xRot = -0.2F + Mth.cos(f) * 0.25F * speed;
		leftArm.xRot = -0.2F + Mth.cos(f + Mth.PI) * 0.25F * speed;
		rightArm.zRot = 0.45F;
		leftArm.zRot = -0.45F;
		rightForearm.xRot = -0.3F;
		leftForearm.xRot = -0.3F;
	}

	/** Breathing, the slack jaw working, and the odd violent twitch of the head. */
	private void idle(InfectedRenderState state, float age) {
		chest.xRot += Mth.sin(age * 0.08F + state.seed) * 0.02F;
		jaw.xRot += 0.18F + 0.12F * Math.max(0.0F, Mth.sin(age * 0.11F + state.seed));
		int twitchEvery = state.kind == InfectedKind.SCREAMER ? 13 : 37;
		if (((int) (age / 6.0F) + state.seed) % twitchEvery == 0) {
			head.yRot += 0.35F * Mth.sin(age * 3.0F);
			head.zRot += 0.15F * Mth.cos(age * 2.3F);
		}
	}

	/** Turn the head toward what the infected is looking at. */
	private void look(InfectedRenderState state) {
		head.yRot += Mth.clamp(state.yRot, -70.0F, 70.0F) * Mth.DEG_TO_RAD;
		head.xRot += Mth.clamp(state.xRot, -40.0F, 50.0F) * Mth.DEG_TO_RAD;
	}

	// ======================================================================================== actions

	private void actions(InfectedRenderState state, float age) {
		float wAttack = weight(state.attackAnimationState, age, 0.55F, false);
		float wSlam = weight(state.slamAnimationState, age, 1.1F, false);
		float wScream = weight(state.screamAnimationState, age, 2.0F, false);
		float wPounce = weight(state.pounceAnimationState, age, 0.8F, false);
		float wBreach = state.breaching ? weight(state.breachAnimationState, age, 1.0F, true) : 0.0F;
		float wEmerge = weight(state.emergeAnimationState, age, 1.4F, false);
		float arms = Math.max(Math.max(Math.max(wAttack, wSlam), Math.max(wScream, wPounce)), Math.max(wBreach, wEmerge));
		if (arms > 0.0F) {
			float keep = 1.0F - arms;
			fade(rightArm, keep);
			fade(rightForearm, keep);
			fade(leftArm, keep);
			fade(leftForearm, keep);
		}
		play(attack, state.attackAnimationState, age, wAttack);
		play(slam, state.slamAnimationState, age, wSlam);
		play(scream, state.screamAnimationState, age, wScream);
		play(pounce, state.pounceAnimationState, age, wPounce);
		play(breach, state.breachAnimationState, age, wBreach);
		play(emerge, state.emergeAnimationState, age, wEmerge);
		if (swell != null && state.swelling) {
			play(swell, state.swellAnimationState, age, weight(state.swellAnimationState, age, 0.8F, true));
		}
	}

	private static void fade(ModelPart part, float keep) {
		part.xRot *= keep;
		part.yRot *= keep;
		part.zRot *= keep;
	}

	private static void play(KeyframeAnimation animation, AnimationState state, float age, float weight) {
		if (weight > 0.0F && state.isStarted()) {
			animation.apply(state.getTimeInMillis(age), weight);
		}
	}

	/** How strongly an action plays: eased in over 0.1 s and out over its last 0.15 s; 0 when not playing. */
	private static float weight(AnimationState state, float age, float length, boolean looping) {
		if (!state.isStarted()) {
			return 0.0F;
		}
		float t = state.getTimeInMillis(age) / 1000.0F;
		if (t < 0.0F || (!looping && t >= length)) {
			return 0.0F;
		}
		float in = Mth.clamp(t / 0.1F, 0.0F, 1.0F);
		float out = looping ? 1.0F : Mth.clamp((length - t) / 0.15F, 0.0F, 1.0F);
		return Math.min(in, out);
	}

	/** 0..1 through an action, or -1 when it is not playing. */
	protected static float progress(AnimationState state, float age, float length) {
		if (!state.isStarted()) {
			return -1.0F;
		}
		float t = state.getTimeInMillis(age) / 1000.0F;
		return t < 0.0F || t >= length ? -1.0F : t / length;
	}
}
