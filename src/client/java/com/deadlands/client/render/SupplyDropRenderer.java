package com.deadlands.client.render;

import com.deadlands.Deadlands;
import com.deadlands.event.SupplyDropEntity;
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
import net.minecraft.util.Mth;

/** Draws a supply drop: the crate under its parachute ({@code SupplyDropGeometry}), swaying as it comes down. */
public class SupplyDropRenderer extends EntityRenderer<SupplyDropEntity, SupplyDropRenderer.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Deadlands.id("supply_drop"), "main");
	private static final Identifier TEXTURE = Deadlands.id("textures/entity/supply_drop.png");

	public static class State extends EntityRenderState {
		public float sway;
	}

	public static class Model extends EntityModel<State> {
		public Model(ModelPart root) {
			super(root, RenderTypes::entityCutoutNoCull);
		}
	}

	private final Model model;

	public SupplyDropRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new Model(context.bakeLayer(LAYER));
		this.shadowRadius = 0.6F;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(SupplyDropEntity drop, State state, float partialTick) {
		super.extractRenderState(drop, state, partialTick);
		state.sway = Mth.sin((drop.tickCount + partialTick) * 0.05F) * 6.0F;
	}

	@Override
	public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		pose.pushPose();
		pose.mulPose(Axis.ZP.rotationDegrees(state.sway));
		pose.scale(-1.0F, -1.0F, 1.0F);
		pose.translate(0.0F, -1.501F, 0.0F);
		collector.submitModel(model, state, pose, model.renderType(TEXTURE), state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor, null);
		pose.popPose();
		super.submit(state, pose, collector, camera);
	}
}
