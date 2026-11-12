package com.deadlands.client.render;

import com.deadlands.Deadlands;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * An imported mesh rigged onto a boxless model (the format {@code tools/assetgen/import_guest.py} and
 * {@code import_aberrant.py} write: triangles per bone and material, each vertex relative to its bone's
 * pivot), and the layer that draws it on the animated rig, plus each material's glow map full bright.
 */
public final class RiggedMesh {
	public record Material(Identifier texture, @Nullable Identifier glow) {
	}

	public record Group(int bone, int material, float[] data) {
	}

	private static final Map<Identifier, RiggedMesh> LOADED = new HashMap<>();
	private static final Map<Identifier, Long> FAILED = new HashMap<>();

	public final List<Material> materials;
	public final List<Group> groups;

	private RiggedMesh(List<Material> materials, List<Group> groups) {
		this.materials = materials;
		this.groups = groups;
	}

	/** The mesh at {@code file}, its textures in {@code textureFolder}; null if it cannot be read (logged; tried again five seconds later). */
	public static @Nullable RiggedMesh get(Identifier file, String textureFolder) {
		RiggedMesh mesh = LOADED.get(file);
		if (mesh == null && System.currentTimeMillis() >= FAILED.getOrDefault(file, 0L)) {
			try {
				mesh = read(file, textureFolder);
				LOADED.put(file, mesh);
			} catch (IOException | RuntimeException failure) {
				FAILED.put(file, System.currentTimeMillis() + 5000L);
				Deadlands.LOGGER.error("DEADLANDS: could not read the mesh {}", file, failure);
			}
		}
		return mesh;
	}

	private static RiggedMesh read(Identifier file, String folder) throws IOException {
		try (InputStream raw = Minecraft.getInstance().getResourceManager().getResourceOrThrow(file).open();
			DataInputStream in = new DataInputStream(new BufferedInputStream(raw))) {
			byte[] magic = new byte[8];
			in.readFully(magic);
			if (!new String(magic, 0, 7, StandardCharsets.US_ASCII).equals("DLMESH1")) {
				throw new IOException("not a DEADLANDS mesh");
			}
			int count = in.readInt();
			List<Material> materials = new ArrayList<>(count);
			for (int i = 0; i < count; i++) {
				byte[] name = new byte[in.readShort()];
				in.readFully(name);
				String id = new String(name, StandardCharsets.UTF_8);
				boolean glow = in.readBoolean();
				materials.add(new Material(Deadlands.id("textures/entity/" + folder + "/" + id + ".png"),
					glow ? Deadlands.id("textures/entity/" + folder + "/" + id + "_glow.png") : null));
			}
			int groups = in.readInt();
			List<Group> out = new ArrayList<>(groups);
			for (int g = 0; g < groups; g++) {
				int bone = in.readShort();
				int material = in.readShort();
				float[] data = new float[in.readInt() * 24];
				for (int k = 0; k < data.length; k++) {
					data[k] = in.readFloat();
				}
				out.add(new Group(bone, material, data));
			}
			return new RiggedMesh(List.copyOf(materials), List.copyOf(out));
		}
	}

	/** Draws a {@link RiggedMesh} on a boxless rig whose bones and parents are given in the mesh file's order. */
	public static class Layer<S extends LivingEntityRenderState, M extends EntityModel<S>> extends RenderLayer<S, M> {
		private static final int FULL_BRIGHT = 0xF000F0;
		private final Identifier file;
		private final String folder;
		private final ModelPart[][] chains;

		public Layer(RenderLayerParent<S, M> parent, ModelPart root, String[] bones, String[] parents, Identifier file, String folder) {
			super(parent);
			this.file = file;
			this.folder = folder;
			Map<String, ModelPart> parts = new HashMap<>();
			Map<String, String> up = new HashMap<>();
			ModelPart base = root.getChild("root");
			parts.put("root", base);
			for (int i = 0; i < bones.length; i++) {
				parts.put(bones[i], parts.get(parents[i]).getChild(bones[i]));
				up.put(bones[i], parents[i]);
			}
			chains = new ModelPart[bones.length][];
			for (int i = 0; i < bones.length; i++) {
				ArrayList<ModelPart> chain = new ArrayList<>();
				for (String bone = bones[i]; !bone.equals("root"); bone = up.get(bone)) {
					chain.add(0, parts.get(bone));
				}
				chain.add(0, base);
				chain.add(0, root);
				chains[i] = chain.toArray(ModelPart[]::new);
			}
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, S state, float yRot, float xRot) {
			RiggedMesh mesh = RiggedMesh.get(file, folder);
			if (mesh == null || state.isInvisible) {
				return;
			}
			int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
			for (Group group : mesh.groups) {
				Material material = mesh.materials.get(group.material());
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

		private static void draw(SubmitNodeCollector collector, PoseStack poseStack, RenderType type, Group group, int light, int overlay) {
			float[] d = group.data();
			collector.submitCustomGeometry(poseStack, type, (pose, consumer) -> {
				for (int t = 0; t < d.length; t += 24) {
					vertex(consumer, pose, d, t, light, overlay);
					vertex(consumer, pose, d, t + 8, light, overlay);
					vertex(consumer, pose, d, t + 16, light, overlay);
					vertex(consumer, pose, d, t + 16, light, overlay);
				}
			});
		}

		private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float[] d, int i, int light, int overlay) {
			consumer.addVertex(pose, d[i] / 16.0F, d[i + 1] / 16.0F, d[i + 2] / 16.0F).setColor(-1).setUv(d[i + 3], d[i + 4])
				.setOverlay(overlay).setLight(light).setNormal(pose, d[i + 5], d[i + 6], d[i + 7]);
		}
	}
}
