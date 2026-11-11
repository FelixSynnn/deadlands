package com.deadlands.client.render.infected;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/**
 * Lays one of the rig's wear overlays (blood spatter, a feeding, mud, fresh gashes, ash, bites and
 * scratches; tools/assetgen/gen_infected.py) over the infected's look, chosen from its UUID, so two
 * infected in the same clothes still differ. Drawn translucent over the same posed model.
 */
public class InfectedWearLayer extends RenderLayer<InfectedRenderState, InfectedModel> {
	public InfectedWearLayer(RenderLayerParent<InfectedRenderState, InfectedModel> parent) {
		super(parent);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, InfectedRenderState state, float yRot, float xRot) {
		if (state.wearTexture == null || state.isInvisible) {
			return;
		}
		collector.order(1).submitModel(getParentModel(), state, poseStack, RenderTypes.entityTranslucent(state.wearTexture), light,
			LivingEntityRenderer.getOverlayCoords(state, 0.0F), -1, null, state.outlineColor, null);
	}
}
