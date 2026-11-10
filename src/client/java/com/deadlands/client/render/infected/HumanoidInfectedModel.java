package com.deadlands.client.render.infected;

import com.deadlands.entity.InfectedVariants;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * The human-sized rig (walkers, runners, screamers, military, crawlers, bloaters, stalkers,
 * charred), with the optional parts each variant switches on: helmet, cap and brim, gas mask,
 * vest, belt, backpack, claws, the bloater's belly, growths and torn cloth, and missing forearms or
 * shins.
 */
public class HumanoidInfectedModel extends InfectedModel {
	private final ModelPart belly;
	private final ModelPart belt;
	private final ModelPart frontTatter;
	private final ModelPart backTatter;
	private final ModelPart vest;
	private final ModelPart backpack;
	private final ModelPart growths;
	private final ModelPart cap;
	private final ModelPart brim;
	private final ModelPart mask;
	private final ModelPart rightClaws;
	private final ModelPart leftClaws;

	public HumanoidInfectedModel(ModelPart root) {
		super(root);
		this.belly = torso.getChild("belly");
		this.belt = torso.getChild("belt");
		this.frontTatter = torso.getChild("front_tatter");
		this.backTatter = torso.getChild("back_tatter");
		this.vest = chest.getChild("vest");
		this.backpack = chest.getChild("backpack");
		this.growths = chest.getChild("growths");
		this.cap = head.getChild("cap");
		this.brim = head.getChild("brim");
		this.mask = head.getChild("mask");
		this.rightClaws = rightForearm.getChild("right_claws");
		this.leftClaws = leftForearm.getChild("left_claws");
	}

	@Override
	protected void showParts(InfectedRenderState state) {
		helmet.visible = state.has(InfectedVariants.HELMET);
		vest.visible = state.has(InfectedVariants.VEST);
		belt.visible = state.has(InfectedVariants.BELT);
		mask.visible = state.has(InfectedVariants.MASK);
		cap.visible = state.has(InfectedVariants.CAP);
		brim.visible = state.has(InfectedVariants.CAP);
		rightClaws.visible = state.has(InfectedVariants.CLAWS);
		leftClaws.visible = state.has(InfectedVariants.CLAWS);
		belly.visible = state.has(InfectedVariants.BELLY);
		growths.visible = state.has(InfectedVariants.GROWTHS);
		frontTatter.visible = state.has(InfectedVariants.TATTERS);
		backTatter.visible = state.has(InfectedVariants.TATTERS);
		backpack.visible = state.has(InfectedVariants.BACKPACK);
		leftForearm.visible = !state.has(InfectedVariants.NO_LEFT_FOREARM);
		rightForearm.visible = !state.has(InfectedVariants.NO_RIGHT_FOREARM);
		boolean shins = !state.has(InfectedVariants.NO_SHINS);
		rightShin.visible = shins;
		leftShin.visible = shins;
	}

	@Override
	protected void extras(InfectedRenderState state, float age, float walk, float speed) {
		if (frontTatter.visible) {
			float flap = Math.min(0.9F, speed * 0.9F) * (0.6F + 0.4F * Math.abs(Mth.sin(walk * 0.55F)));
			float stir = Mth.sin(age * 0.13F + state.seed) * 0.06F;
			frontTatter.xRot = -flap - stir;
			backTatter.xRot = flap * 0.8F + stir;
		}
		if (belly.visible) {
			float heave = Mth.sin(age * 0.07F + state.seed);
			belly.xScale = 1.0F + 0.03F * heave;
			belly.zScale = 1.0F + 0.05F * heave;
		}
		if (rightClaws.visible) {
			float flex = Mth.sin(age * 0.2F + state.seed) * 0.12F;
			rightClaws.xRot = -0.3F + flex;
			leftClaws.xRot = -0.3F - flex;
		}
	}
}
