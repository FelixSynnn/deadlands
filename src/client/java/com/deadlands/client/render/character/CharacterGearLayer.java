package com.deadlands.client.render.character;

import com.deadlands.client.render.NpcRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/**
 * Draws a character's 3D gear ({@link CharacterGear}) over the skin, posed exactly like the body
 * (the gear model is a humanoid model set up from the same render state, as armour is).
 */
public class CharacterGearLayer extends RenderLayer<NpcRenderer.State, HumanoidModel<NpcRenderer.State>> {
	private final HumanoidModel<NpcRenderer.State> itzbreadloaf = new HumanoidModel<>(CharacterGear.itzbreadloaf().bakeRoot());

	public CharacterGearLayer(RenderLayerParent<NpcRenderer.State, HumanoidModel<NpcRenderer.State>> parent) {
		super(parent);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, NpcRenderer.State state, float yRot, float xRot) {
		if (state.gear != null) {
			coloredCutoutModelCopyLayerRender(itzbreadloaf, state.gear, poseStack, collector, light, state, -1, 1);
		}
	}
}
