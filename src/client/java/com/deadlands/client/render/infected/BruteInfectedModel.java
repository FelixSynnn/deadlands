package com.deadlands.client.render.infected;

import com.deadlands.entity.InfectedVariants;
import net.minecraft.client.model.geom.ModelPart;

/** The brute rig: a huge hunched frame with long, heavy arms. Only the helmet is optional. */
public class BruteInfectedModel extends InfectedModel {
	public BruteInfectedModel(ModelPart root) {
		super(root);
	}

	@Override
	protected void showParts(InfectedRenderState state) {
		helmet.visible = state.has(InfectedVariants.HELMET);
	}
}
