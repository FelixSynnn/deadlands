package com.deadlands.client.render;

import com.deadlands.container.ContainerKind;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * The moving parts of each container (lid, drawer, doors), in block pixels with the front facing
 * north and y up. Texture layouts (standard cube UVs) are generated to match by
 * tools/assetgen/gen_containers.py; keep the two in sync.
 *
 * <p>UVs are declared against a 64x32 texture but the images are 128x64: Minecraft maps UVs
 * relative to the declared size, so the parts get 2 texels per pixel, matching the block faces.
 */
public final class ContainerPartModels {
	public static final int TEX_W = 64;
	public static final int TEX_H = 32;

	private ContainerPartModels() {
	}

	public static Map<ContainerKind, ModelPart> bakeAll() {
		Map<ContainerKind, ModelPart> parts = new EnumMap<>(ContainerKind.class);
		for (ContainerKind kind : ContainerKind.values()) {
			parts.put(kind, create(kind).bakeRoot());
		}
		return parts;
	}

	static LayerDefinition create(ContainerKind kind) {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		switch (kind) {
			// Heavy hinged lid: hinge along the back top edge, lid extends toward the front.
			case MILITARY_CRATE -> root.addOrReplaceChild("lid",
				CubeListBuilder.create().texOffs(0, 0).addBox(1, 0, -12, 14, 2, 12),
				PartPose.offset(0, 10, 14));
			// Small lid with a carry handle on top.
			case TOOLBOX -> root.addOrReplaceChild("lid",
				CubeListBuilder.create().texOffs(0, 0).addBox(3, 0, -6, 10, 2, 6)
					.texOffs(0, 16).addBox(6, 2, -3.5F, 4, 1, 1),
				PartPose.offset(0, 5, 11));
			// Top drawer: front panel plus the tray behind it.
			case FILING_CABINET -> root.addOrReplaceChild("drawer",
				CubeListBuilder.create().texOffs(0, 0).addBox(2.5F, 8.5F, 0, 11, 7, 1)
					.texOffs(0, 8).addBox(3, 9, 1, 10, 6, 12),
				PartPose.ZERO);
			// Two glass doors hinged on the outer edges.
			case MEDICAL_CABINET -> {
				root.addOrReplaceChild("left_door", CubeListBuilder.create().texOffs(0, 0).addBox(0, 1, -1, 6, 14, 1), PartPose.offset(2, 0, 4));
				root.addOrReplaceChild("right_door", CubeListBuilder.create().texOffs(16, 0).addBox(-6, 1, -1, 6, 14, 1), PartPose.offset(14, 0, 4));
			}
			// One vented door hinged on the left (the fridge door is the same size).
			case SUPPLY_LOCKER, FRIDGE -> root.addOrReplaceChild("door",
				CubeListBuilder.create().texOffs(0, 0).addBox(0, 1, -1, 10, 14, 1),
				PartPose.offset(3, 0, 3));
			// Two painted doors under the counter top.
			case KITCHEN_CUPBOARD -> {
				root.addOrReplaceChild("left_door", CubeListBuilder.create().texOffs(0, 0).addBox(0, 1, -1, 6, 14, 1), PartPose.offset(2, 0, 4));
				root.addOrReplaceChild("right_door", CubeListBuilder.create().texOffs(16, 0).addBox(-6, 1, -1, 6, 14, 1), PartPose.offset(14, 0, 4));
			}
			// Top drawer: front panel plus a shallow tray.
			case DRESSER -> root.addOrReplaceChild("drawer",
				CubeListBuilder.create().texOffs(0, 0).addBox(2, 8, 0, 12, 5, 1)
					.texOffs(0, 8).addBox(2.5F, 8.5F, 1, 11, 4, 10),
				PartPose.ZERO);
			// A thick steel door hinged on the left.
			case SAFE -> root.addOrReplaceChild("door",
				CubeListBuilder.create().texOffs(0, 0).addBox(0, 1, -1, 10, 11, 1),
				PartPose.offset(3, 0, 3));
			// The desk top lifts on a hinge along the back edge.
			case SCHOOL_DESK -> root.addOrReplaceChild("lid",
				CubeListBuilder.create().texOffs(0, 0).addBox(1, 0, -12, 14, 1, 12),
				PartPose.offset(0, 11, 14));
		}
		return LayerDefinition.create(mesh, TEX_W, TEX_H);
	}

	/** Pose the parts for an openness between 0 (closed) and 1 (fully open). */
	public static void pose(ContainerKind kind, ModelPart root, float open) {
		root.getAllParts().forEach(ModelPart::resetPose);
		switch (kind) {
			case MILITARY_CRATE -> root.getChild("lid").xRot = open * 1.75F;
			case TOOLBOX -> root.getChild("lid").xRot = open * 1.9F;
			case FILING_CABINET -> root.getChild("drawer").z = -open * 9.0F;
			case MEDICAL_CABINET -> {
				root.getChild("left_door").yRot = open * 1.75F;
				root.getChild("right_door").yRot = -open * 1.75F;
			}
			case SUPPLY_LOCKER, FRIDGE -> root.getChild("door").yRot = open * 1.85F;
			case KITCHEN_CUPBOARD -> {
				root.getChild("left_door").yRot = open * 1.75F;
				root.getChild("right_door").yRot = -open * 1.75F;
			}
			case DRESSER -> root.getChild("drawer").z = -open * 8.0F;
			case SAFE -> root.getChild("door").yRot = open * 1.6F;
			case SCHOOL_DESK -> root.getChild("lid").xRot = open * 1.4F;
		}
	}
}
