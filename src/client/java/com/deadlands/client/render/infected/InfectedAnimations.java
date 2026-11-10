package com.deadlands.client.render.infected;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;

/**
 * Keyframed infected actions, played on top of the procedural walk and idle poses (see
 * {@link InfectedModel}). They only touch bones both rigs share, except {@link #SWELL}, which needs
 * the humanoid's belly.
 *
 * <p>Rotations are in degrees (x pitches a limb forward when negative, z swings it sideways);
 * positions are in model pixels with y up.
 */
public final class InfectedAnimations {
	/** A clawing swipe with the right arm, the left grabbing, the head lunging in to bite. */
	public static final AnimationDefinition ATTACK = AnimationDefinition.Builder.withLength(0.55F)
		.addAnimation("right_arm", rot(k(0.0F, 0, 0, 0), k(0.14F, -155, 10, -15), k(0.28F, -55, -10, 15), k(0.55F, -20, 0, 0)))
		.addAnimation("right_forearm", rot(k(0.0F, 0, 0, 0), k(0.14F, -40, 0, 0), k(0.28F, -10, 0, 0), k(0.55F, 0, 0, 0)))
		.addAnimation("left_arm", rot(k(0.0F, 0, 0, 0), k(0.14F, -70, 0, 10), k(0.3F, -85, 0, 0), k(0.55F, -40, 0, 0)))
		.addAnimation("left_forearm", rot(k(0.0F, 0, 0, 0), k(0.3F, -30, 0, 0), k(0.55F, 0, 0, 0)))
		.addAnimation("torso", rot(k(0.0F, 0, 0, 0), k(0.14F, -5, 15, 0), k(0.28F, 12, -15, 0), k(0.55F, 0, 0, 0)))
		.addAnimation("head", rot(k(0.0F, 0, 0, 0), k(0.2F, -10, 0, 0), k(0.3F, 15, 0, 0), k(0.55F, 0, 0, 0)))
		.addAnimation("jaw", rot(k(0.0F, 0, 0, 0), k(0.18F, 35, 0, 0), k(0.3F, 5, 0, 0), k(0.4F, 25, 0, 0), k(0.55F, 0, 0, 0)))
		.build();

	/** The brute's two-handed ground slam: rear back, fists overhead, smash, knees buckling. */
	public static final AnimationDefinition SLAM = AnimationDefinition.Builder.withLength(1.1F)
		.addAnimation("right_arm", rot(k(0.0F, 0, 0, 0), k(0.45F, -170, 0, -15), k(0.62F, -40, 0, 5), k(0.75F, -30, 0, 5), k(1.1F, 0, 0, 0)))
		.addAnimation("left_arm", rot(k(0.0F, 0, 0, 0), k(0.45F, -170, 0, 15), k(0.62F, -40, 0, -5), k(0.75F, -30, 0, -5), k(1.1F, 0, 0, 0)))
		.addAnimation("right_forearm", rot(k(0.0F, 0, 0, 0), k(0.45F, -30, 0, 0), k(0.62F, -5, 0, 0), k(1.1F, 0, 0, 0)))
		.addAnimation("left_forearm", rot(k(0.0F, 0, 0, 0), k(0.45F, -30, 0, 0), k(0.62F, -5, 0, 0), k(1.1F, 0, 0, 0)))
		.addAnimation("torso", rot(k(0.0F, 0, 0, 0), k(0.45F, -20, 0, 0), k(0.62F, 30, 0, 0), k(0.8F, 25, 0, 0), k(1.1F, 0, 0, 0)))
		.addAnimation("body", pos(k(0.0F, 0, 0, 0), k(0.45F, 0, 1, 0), k(0.62F, 0, -3, 0), k(0.85F, 0, -2.5F, 0), k(1.1F, 0, 0, 0)))
		.addAnimation("right_leg", rot(k(0.0F, 0, 0, 0), k(0.62F, -25, 0, 0), k(0.85F, -20, 0, 0), k(1.1F, 0, 0, 0)))
		.addAnimation("right_shin", rot(k(0.0F, 0, 0, 0), k(0.62F, 35, 0, 0), k(0.85F, 30, 0, 0), k(1.1F, 0, 0, 0)))
		.addAnimation("left_leg", rot(k(0.0F, 0, 0, 0), k(0.62F, -25, 0, 0), k(0.85F, -20, 0, 0), k(1.1F, 0, 0, 0)))
		.addAnimation("left_shin", rot(k(0.0F, 0, 0, 0), k(0.62F, 35, 0, 0), k(0.85F, 30, 0, 0), k(1.1F, 0, 0, 0)))
		.addAnimation("jaw", rot(k(0.0F, 0, 0, 0), k(0.45F, 20, 0, 0), k(0.62F, 40, 0, 0), k(1.1F, 0, 0, 0)))
		.build();

	/** The screamer throws its head back, jaw wide, arms spread and shaking. */
	public static final AnimationDefinition SCREAM = AnimationDefinition.Builder.withLength(2.0F)
		.addAnimation("head", rot(k(0.0F, 0, 0, 0), k(0.3F, -40, 0, 0), k(0.6F, -45, 5, 0), k(0.9F, -40, -5, 0), k(1.2F, -45, 5, 0),
			k(1.5F, -40, -5, 0), k(1.7F, -42, 0, 0), k(2.0F, 0, 0, 0)))
		.addAnimation("jaw", rot(k(0.0F, 0, 0, 0), k(0.3F, 55, 0, 0), k(1.7F, 55, 0, 0), k(2.0F, 0, 0, 0)))
		.addAnimation("chest", rot(k(0.0F, 0, 0, 0), k(0.3F, -15, 0, 0), k(1.7F, -15, 0, 0), k(2.0F, 0, 0, 0)))
		.addAnimation("right_arm", rot(k(0.0F, 0, 0, 0), k(0.35F, -20, 0, 60), k(0.8F, -25, 0, 65), k(1.3F, -20, 0, 58), k(1.7F, -22, 0, 62), k(2.0F, 0, 0, 0)))
		.addAnimation("left_arm", rot(k(0.0F, 0, 0, 0), k(0.35F, -20, 0, -60), k(0.8F, -25, 0, -65), k(1.3F, -20, 0, -58), k(1.7F, -22, 0, -62), k(2.0F, 0, 0, 0)))
		.addAnimation("right_forearm", rot(k(0.0F, 0, 0, 0), k(0.35F, -25, 0, 0), k(1.7F, -25, 0, 0), k(2.0F, 0, 0, 0)))
		.addAnimation("left_forearm", rot(k(0.0F, 0, 0, 0), k(0.35F, -25, 0, 0), k(1.7F, -25, 0, 0), k(2.0F, 0, 0, 0)))
		.build();

	/** The stalker's leap: a deep crouch, then arms thrust forward and legs swept back. */
	public static final AnimationDefinition POUNCE = AnimationDefinition.Builder.withLength(0.8F)
		.addAnimation("right_arm", rot(k(0.0F, 0, 0, 0), k(0.15F, 20, 0, 0), k(0.35F, -110, 0, -10), k(0.6F, -90, 0, -5), k(0.8F, 0, 0, 0)))
		.addAnimation("left_arm", rot(k(0.0F, 0, 0, 0), k(0.15F, 20, 0, 0), k(0.35F, -110, 0, 10), k(0.6F, -90, 0, 5), k(0.8F, 0, 0, 0)))
		.addAnimation("right_leg", rot(k(0.0F, 0, 0, 0), k(0.15F, -50, 0, 0), k(0.35F, 35, 0, 0), k(0.8F, 0, 0, 0)))
		.addAnimation("left_leg", rot(k(0.0F, 0, 0, 0), k(0.15F, -50, 0, 0), k(0.35F, 30, 0, 0), k(0.8F, 0, 0, 0)))
		.addAnimation("right_shin", rot(k(0.0F, 0, 0, 0), k(0.15F, 70, 0, 0), k(0.35F, 10, 0, 0), k(0.8F, 0, 0, 0)))
		.addAnimation("left_shin", rot(k(0.0F, 0, 0, 0), k(0.15F, 70, 0, 0), k(0.35F, 15, 0, 0), k(0.8F, 0, 0, 0)))
		.addAnimation("body", pos(k(0.0F, 0, 0, 0), k(0.15F, 0, -2, 0), k(0.35F, 0, 1, 0), k(0.8F, 0, 0, 0)))
		.addAnimation("head", rot(k(0.0F, 0, 0, 0), k(0.35F, -25, 0, 0), k(0.8F, 0, 0, 0)))
		.addAnimation("jaw", rot(k(0.0F, 0, 0, 0), k(0.3F, 40, 0, 0), k(0.8F, 0, 0, 0)))
		.build();

	/** Pounding on a door or barricade, fists alternating (loops while breaching). */
	public static final AnimationDefinition BREACH = AnimationDefinition.Builder.withLength(1.0F).looping()
		.addAnimation("right_arm", rot(k(0.0F, -150, 0, -10), k(0.25F, -80, 0, 0), k(0.5F, -150, 0, -10), k(0.75F, -125, 0, -5), k(1.0F, -150, 0, -10)))
		.addAnimation("left_arm", rot(k(0.0F, -115, 0, 10), k(0.25F, -150, 0, 10), k(0.5F, -125, 0, 5), k(0.75F, -80, 0, 0), k(1.0F, -115, 0, 10)))
		.addAnimation("right_forearm", rot(k(0.0F, -35, 0, 0), k(0.25F, -5, 0, 0), k(0.5F, -35, 0, 0), k(1.0F, -35, 0, 0)))
		.addAnimation("left_forearm", rot(k(0.0F, -20, 0, 0), k(0.5F, -35, 0, 0), k(0.75F, -5, 0, 0), k(1.0F, -20, 0, 0)))
		.addAnimation("torso", rot(k(0.0F, 5, 0, 0), k(0.25F, 12, 8, 0), k(0.5F, 5, 0, 0), k(0.75F, 12, -8, 0), k(1.0F, 5, 0, 0)))
		.addAnimation("head", rot(k(0.0F, 0, 0, 0), k(0.25F, 10, 0, 0), k(0.5F, 0, 0, 0), k(0.75F, 10, 0, 0), k(1.0F, 0, 0, 0)))
		.addAnimation("jaw", rot(k(0.0F, 10, 0, 0), k(0.3F, 30, 0, 0), k(0.55F, 10, 0, 0), k(0.8F, 30, 0, 0), k(1.0F, 10, 0, 0)))
		.build();

	/** Dragging itself up out of a nest: kneeling, bent double, then rising. */
	public static final AnimationDefinition EMERGE = AnimationDefinition.Builder.withLength(1.4F)
		.addAnimation("body", pos(k(0.0F, 0, -10, 0), k(0.6F, 0, -5, 0), k(1.0F, 0, -1, 0), k(1.4F, 0, 0, 0)))
		.addAnimation("torso", rot(k(0.0F, 60, 0, 0), k(0.7F, 35, 0, 0), k(1.4F, 0, 0, 0)))
		.addAnimation("head", rot(k(0.0F, -40, 0, 0), k(0.7F, -25, 0, 0), k(1.4F, 0, 0, 0)))
		.addAnimation("right_arm", rot(k(0.0F, -60, 0, 0), k(0.7F, -30, 0, -10), k(1.4F, 0, 0, 0)))
		.addAnimation("left_arm", rot(k(0.0F, -50, 0, 0), k(0.7F, -35, 0, 10), k(1.4F, 0, 0, 0)))
		.addAnimation("right_leg", rot(k(0.0F, -70, 0, 0), k(0.8F, -30, 0, 0), k(1.4F, 0, 0, 0)))
		.addAnimation("right_shin", rot(k(0.0F, 100, 0, 0), k(0.8F, 40, 0, 0), k(1.4F, 0, 0, 0)))
		.addAnimation("left_leg", rot(k(0.0F, -60, 0, 0), k(0.9F, -25, 0, 0), k(1.4F, 0, 0, 0)))
		.addAnimation("left_shin", rot(k(0.0F, 95, 0, 0), k(0.9F, 35, 0, 0), k(1.4F, 0, 0, 0)))
		.addAnimation("jaw", rot(k(0.0F, 20, 0, 0), k(0.7F, 30, 0, 0), k(1.4F, 0, 0, 0)))
		.build();

	/** The bloater about to burst: the belly heaves, the body shakes (loops while swelling; humanoid rig only). */
	public static final AnimationDefinition SWELL = AnimationDefinition.Builder.withLength(0.8F).looping()
		.addAnimation("belly", new AnimationChannel(AnimationChannel.Targets.SCALE,
			new Keyframe(0.0F, KeyframeAnimations.scaleVec(1.0, 1.0, 1.0), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(0.4F, KeyframeAnimations.scaleVec(1.25, 1.2, 1.3), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(0.8F, KeyframeAnimations.scaleVec(1.0, 1.0, 1.0), AnimationChannel.Interpolations.CATMULLROM)))
		.addAnimation("torso", rot(k(0.0F, 0, 0, -4), k(0.2F, 0, 0, 4), k(0.4F, 0, 0, -4), k(0.6F, 0, 0, 4), k(0.8F, 0, 0, -4)))
		.addAnimation("head", rot(k(0.0F, -5, 0, 0), k(0.4F, -20, 0, 0), k(0.8F, -5, 0, 0)))
		.addAnimation("jaw", rot(k(0.0F, 10, 0, 0), k(0.4F, 40, 0, 0), k(0.8F, 10, 0, 0)))
		.build();

	private InfectedAnimations() {
	}

	private static float[] k(float time, float x, float y, float z) {
		return new float[] {time, x, y, z};
	}

	private static AnimationChannel rot(float[]... keys) {
		Keyframe[] frames = new Keyframe[keys.length];
		for (int i = 0; i < keys.length; i++) {
			frames[i] = new Keyframe(keys[i][0], KeyframeAnimations.degreeVec(keys[i][1], keys[i][2], keys[i][3]), AnimationChannel.Interpolations.CATMULLROM);
		}
		return new AnimationChannel(AnimationChannel.Targets.ROTATION, frames);
	}

	private static AnimationChannel pos(float[]... keys) {
		Keyframe[] frames = new Keyframe[keys.length];
		for (int i = 0; i < keys.length; i++) {
			frames[i] = new Keyframe(keys[i][0], KeyframeAnimations.posVec(keys[i][1], keys[i][2], keys[i][3]), AnimationChannel.Interpolations.CATMULLROM);
		}
		return new AnimationChannel(AnimationChannel.Targets.POSITION, frames);
	}
}
