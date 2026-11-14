package com.deadlands.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/**
 * Draws the Aberrant's imported mesh ({@link AberrantMesh}) on his animated rig: each bone's triangles
 * at that bone's pose this frame (the rig's parts are posed by {@link AsesinoModel} before the layers run),
 * then the glowing parts again, full bright, on the emissive pass so they show in the dark.
 */
public class AberrantMeshLayer extends RenderLayer<AsesinoRenderer.State, AsesinoModel> {
	private static final int FULL_BRIGHT = 0xF000F0;
	private final ModelPart[][] chains;

	public AberrantMeshLayer(RenderLayerParent<AsesinoRenderer.State, AsesinoModel> parent, ModelPart root) {
		super(parent);
		// For each bone, the parts from the model's root down to it (translateAndRotate in order).
		Map<String, ModelPart> parts = new HashMap<>();
		ModelPart base = root.getChild("root");
		parts.put("root", base);
		for (int i = 0; i < AberrantGeometry.BONES.length; i++) {
			parts.put(AberrantGeometry.BONES[i], parts.get(AberrantGeometry.PARENTS[i]).getChild(AberrantGeometry.BONES[i]));
		}
		chains = new ModelPart[AberrantGeometry.BONES.length][];
		for (int i = 0; i < AberrantGeometry.BONES.length; i++) {
			java.util.ArrayList<ModelPart> chain = new java.util.ArrayList<>();
			String bone = AberrantGeometry.BONES[i];
			while (!bone.equals("root")) {
				chain.add(0, parts.get(bone));
				bone = parent(bone);
			}
			chain.add(0, base);
			chain.add(0, root);
			chains[i] = chain.toArray(ModelPart[]::new);
		}
	}

	private static String parent(String bone) {
		for (int i = 0; i < AberrantGeometry.BONES.length; i++) {
			if (AberrantGeometry.BONES[i].equals(bone)) {
				return AberrantGeometry.PARENTS[i];
			}
		}
		return "root";
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AsesinoRenderer.State state, float yRot, float xRot) {
		AberrantMesh mesh = AberrantMesh.get();
		if (mesh == null || state.isInvisible) {
			return;
		}
		int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
		for (AberrantMesh.Group group : mesh.groups) {
			AberrantMesh.Material material = mesh.materials.get(group.material());
			poseStack.pushPose();
			for (ModelPart part : chains[group.bone()]) {
				part.translateAndRotate(poseStack);
			}
			draw(collector, poseStack, RenderTypes.entityCutoutNoCull(material.texture()), group, light, overlay);
			if (material.glow() != null) {
				draw(collector, poseStack, RenderTypes.eyes(material.glow()), group, FULL_BRIGHT, overlay);
			}
			poseStack.popPose();
		}
	}

	private static void draw(SubmitNodeCollector collector, PoseStack poseStack, RenderType type, AberrantMesh.Group group, int light, int overlay) {
		float[] d = group.data();
		collector.submitCustomGeometry(poseStack, type, (pose, consumer) -> {
			for (int t = 0; t < d.length; t += 24) {
				// Entity render types draw quads: each triangle is a quad with its last corner repeated.
				vertex(consumer, pose, d, t, light, overlay);
				vertex(consumer, pose, d, t + 8, light, overlay);
				vertex(consumer, pose, d, t + 16, light, overlay);
				vertex(consumer, pose, d, t + 16, light, overlay);
			}
		});
	}

	private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float[] d, int i, int light, int overlay) {
		consumer.addVertex(pose, d[i] / 16.0F, d[i + 1] / 16.0F, d[i + 2] / 16.0F)
			.setColor(-1)
			.setUv(d[i + 3], d[i + 4])
			.setOverlay(overlay)
			.setLight(light)
			.setNormal(pose, d[i + 5], d[i + 6], d[i + 7]);
	}
}
