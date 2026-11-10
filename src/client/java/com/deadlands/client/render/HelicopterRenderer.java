package com.deadlands.client.render;

import com.deadlands.Deadlands;
import com.deadlands.story.HelicopterEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/** Draws the Site Calder helicopter: its rig, the main and tail rotors spinning during the extraction, and no blades until the rotor blade is fitted. */
public class HelicopterRenderer extends EntityRenderer<HelicopterEntity, HelicopterRenderer.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Deadlands.id("helicopter"), "main");
	private static final Identifier TEXTURE = Deadlands.id("textures/entity/helicopter.png");

	public static class State extends EntityRenderState {
		public float yRot;
		public float rotor;
		public boolean blades;
	}

	public static class Model extends EntityModel<State> {
		private final ModelPart rotor;
		private final ModelPart tailRotor;

		public Model(ModelPart root) {
			super(root, RenderTypes::entityCutoutNoCull);
			ModelPart chassis = root.getChild("chassis");
			this.rotor = chassis.getChild("rotor");
			this.tailRotor = chassis.getChild("tail_rotor");
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			rotor.yRot = state.rotor;
			rotor.visible = state.blades;
			tailRotor.xRot = state.rotor * 2.0F;
		}
	}

	private final Model model;

	public HelicopterRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new Model(context.bakeLayer(LAYER));
		this.shadowRadius = 2.5F;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(HelicopterEntity helicopter, State state, float partialTick) {
		super.extractRenderState(helicopter, state, partialTick);
		state.yRot = helicopter.getYRot(partialTick);
		state.rotor = helicopter.rotor(partialTick);
		state.blades = helicopter.hasPart(0);
	}

	@Override
	public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(180.0F - state.yRot));
		pose.scale(-1.0F, -1.0F, 1.0F);
		pose.translate(0.0F, -1.501F, 0.0F);
		collector.submitModel(model, state, pose, model.renderType(TEXTURE), state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor, null);
		pose.popPose();
		super.submit(state, pose, collector, camera);
	}
}
