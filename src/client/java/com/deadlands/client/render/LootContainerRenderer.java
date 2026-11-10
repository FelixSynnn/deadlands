package com.deadlands.client.render;

import com.deadlands.Deadlands;
import com.deadlands.container.ContainerCondition;
import com.deadlands.container.ContainerKind;
import com.deadlands.container.LootContainerBlock;
import com.deadlands.container.LootContainerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Draws the moving part of every loot container (the body is a baked block model) and animates it:
 * crate and toolbox lids swing up, the filing cabinet drawer slides out, cabinet and locker doors
 * swing open. Openness comes from the block entity, eased and interpolated per frame.
 */
public class LootContainerRenderer implements BlockEntityRenderer<LootContainerBlockEntity, LootContainerRenderState> {
	private final Map<ContainerKind, ModelPart> parts = ContainerPartModels.bakeAll();
	private final Map<ContainerKind, Map<ContainerCondition, Identifier>> textures = new EnumMap<>(ContainerKind.class);

	public LootContainerRenderer(BlockEntityRendererProvider.Context context) {
		for (ContainerKind kind : ContainerKind.values()) {
			Map<ContainerCondition, Identifier> byCondition = new EnumMap<>(ContainerCondition.class);
			for (ContainerCondition condition : ContainerCondition.values()) {
				byCondition.put(condition, Deadlands.id("textures/entity/container/" + kind.id() + "_" + condition.getSerializedName() + ".png"));
			}
			textures.put(kind, byCondition);
		}
	}

	@Override
	public LootContainerRenderState createRenderState() {
		return new LootContainerRenderState();
	}

	@Override
	public void extractRenderState(LootContainerBlockEntity container, LootContainerRenderState state, float partialTick, Vec3 camera,
		ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling) {
		BlockEntityRenderer.super.extractRenderState(container, state, partialTick, camera, crumbling);
		state.kind = container.kind();
		state.condition = container.condition();
		Direction facing = container.getBlockState().hasProperty(LootContainerBlock.FACING)
			? container.getBlockState().getValue(LootContainerBlock.FACING) : Direction.NORTH;
		// Same turn as the blockstate JSON: north 0, east 90, south 180, west 270 (clockwise from above).
		state.yRotation = (facing.toYRot() + 180.0F) % 360.0F;
		state.openness = container.openness(partialTick);
	}

	@Override
	public void submit(LootContainerRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		ModelPart root = parts.get(state.kind);
		ContainerPartModels.pose(state.kind, root, state.openness);
		poseStack.pushPose();
		poseStack.translate(0.5F, 0.5F, 0.5F);
		poseStack.mulPose(Axis.YP.rotationDegrees(-state.yRotation));
		poseStack.translate(-0.5F, -0.5F, -0.5F);
		collector.submitModelPart(root, poseStack, RenderTypes.entityCutout(textures.get(state.kind).get(state.condition)),
			state.lightCoords, OverlayTexture.NO_OVERLAY, null, -1, state.breakProgress);
		poseStack.popPose();
	}
}
