package vectorwing.farmersdelight.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import vectorwing.farmersdelight.client.renderer.state.StoveRenderState;
import vectorwing.farmersdelight.common.block.AbstractStoveBlock;
import vectorwing.farmersdelight.common.block.entity.AbstractStoveBlockEntity;

public abstract class AbstractStoveRenderer<T extends AbstractStoveBlockEntity, S extends StoveRenderState> implements BlockEntityRenderer<T, S> {
    private static final float SIZE = 0.375F;
    private final ItemModelResolver itemModelResolver;

    public AbstractStoveRenderer(Context context) {
        this.itemModelResolver = context.itemModelResolver();
    }

    public void extractRenderState(
        @NonNull T blockEntity, @NonNull S state, float partialTicks, @NonNull Vec3 cameraPosition, @Nullable CrumblingOverlay breakProgress
    ) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
        ItemStackRenderState[] stacks = new ItemStackRenderState[blockEntity.getItems().getSlotCount()];
        BlockPos pos = blockEntity.getBlockPos();

        for (int i = 0; i < blockEntity.getItems().getSlotCount(); i++) {
            ItemStack stack = blockEntity.getItems().getSlot(i).getResource().toStack();
            ItemStackRenderState stackRenderState = new ItemStackRenderState();
            this.itemModelResolver.updateForTopItem(stackRenderState, stack, ItemDisplayContext.FIXED, blockEntity.getLevel(), null, (int)pos.asLong() + i);
            stacks[i] = stackRenderState;
        }

        state.stoveStacks = stacks;
        state.facing = blockEntity.getBlockState().getValue(AbstractStoveBlock.FACING).getOpposite();
    }

    public void submit(S state, @NonNull PoseStack poseStack, @NonNull SubmitNodeCollector submitNodeCollector, @NonNull CameraRenderState camera) {
        Direction direction = state.facing.getOpposite();
        ItemStackRenderState[] items = state.stoveStacks;

        for (int i = 0; i < items.length; i++) {
            ItemStackRenderState stoveStack = items[i];
            if (!stoveStack.isEmpty()) {
                poseStack.pushPose();
                poseStack.translate(0.5, 1.02, 0.5);
                float f = -direction.toYRot();
                poseStack.mulPose(Axis.YP.rotationDegrees(f));
                poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
                Vec2 itemOffset = this.getStoveItemOffset(i);
                poseStack.translate(itemOffset.x, itemOffset.y, 0.0);
                poseStack.scale(0.375F, 0.375F, 0.375F);
                stoveStack.submit(poseStack, submitNodeCollector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
                poseStack.popPose();
            }
        }
    }

    public abstract Vec2 getStoveItemOffset(int var1);
}
