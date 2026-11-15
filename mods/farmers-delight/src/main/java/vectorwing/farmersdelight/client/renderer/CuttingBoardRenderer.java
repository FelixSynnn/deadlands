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
import net.minecraft.core.Direction;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import vectorwing.farmersdelight.client.renderer.state.CuttingBoardRenderState;
import vectorwing.farmersdelight.common.block.CuttingBoardBlock;
import vectorwing.farmersdelight.common.block.entity.CuttingBoardBlockEntity;
import vectorwing.farmersdelight.common.tag.ModTags;

public class CuttingBoardRenderer implements BlockEntityRenderer<CuttingBoardBlockEntity, CuttingBoardRenderState> {
    private final ItemModelResolver itemModelResolver;

    public CuttingBoardRenderer(Context pContext) {
        this.itemModelResolver = pContext.itemModelResolver();
    }

    public CuttingBoardRenderState createRenderState() {
        return new CuttingBoardRenderState();
    }

    public void extractRenderState(
        CuttingBoardBlockEntity cuttingBoardEntity,
        CuttingBoardRenderState renderState,
        float partialTick,
        @NonNull Vec3 cameraPosition,
        @Nullable CrumblingOverlay breakProgress
    ) {
        BlockEntityRenderer.super.extractRenderState(cuttingBoardEntity, renderState, partialTick, cameraPosition, breakProgress);
        renderState.direction = cuttingBoardEntity.getBlockState().getValue(CuttingBoardBlock.FACING).getOpposite();
        ItemStack boardStack = cuttingBoardEntity.getStoredItem();
        renderState.boardStack = boardStack.copy();
        int posLong = (int)cuttingBoardEntity.getBlockPos().asLong();
        int seed = boardStack.isEmpty() ? 187 : Item.getId(boardStack.getItem()) + boardStack.getDamageValue();
        renderState.random.setSeed(seed);
        renderState.displayItem = new ItemStackRenderState();
        this.itemModelResolver.updateForTopItem(renderState.displayItem, boardStack, ItemDisplayContext.FIXED, cuttingBoardEntity.getLevel(), null, posLong);
        renderState.isItemCarvingBoard = cuttingBoardEntity.isItemCarvingBoard();
    }

    public void submit(
        CuttingBoardRenderState state, @NonNull PoseStack poseStack, @NonNull SubmitNodeCollector nodeCollector, @NonNull CameraRenderState camera
    ) {
        Direction direction = state.direction;
        ItemStack boardStack = state.boardStack;
        int itemRenderCount = this.getModelCount(boardStack);

        for (int i = 0; i < itemRenderCount; i++) {
            poseStack.pushPose();
            poseStack.pushPose();
            boolean isBlockItem = state.displayItem.usesBlockLight();
            float xOffset = itemRenderCount == 1 ? 0.0F : (state.random.nextFloat() * 2.0F - 1.0F) * 0.15F * 0.5F;
            float zOffset = itemRenderCount == 1 ? 0.0F : (state.random.nextFloat() * 2.0F - 1.0F) * 0.15F * 0.5F;
            if (state.isItemCarvingBoard) {
                this.renderItemCarved(poseStack, direction, boardStack);
            } else if (isBlockItem && !boardStack.is(ModTags.Items.FLAT_ON_CUTTING_BOARD)) {
                this.renderBlock(poseStack, direction, xOffset, i, zOffset);
            } else {
                this.renderItemLayingDown(poseStack, direction, xOffset, i, zOffset);
            }

            state.displayItem.submit(poseStack, nodeCollector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
            poseStack.popPose();
        }
    }

    public void renderItemLayingDown(PoseStack matrixStackIn, Direction direction, float xOffset, int yIndex, float zOffset) {
        matrixStackIn.translate(0.5 + xOffset, 0.08 + 0.03 * (yIndex + 1), 0.5 + zOffset);
        float f = -direction.toYRot();
        matrixStackIn.mulPose(Axis.YP.rotationDegrees(f));
        matrixStackIn.mulPose(Axis.XP.rotationDegrees(90.0F));
        matrixStackIn.scale(0.6F, 0.6F, 0.6F);
    }

    public void renderBlock(PoseStack matrixStackIn, Direction direction, float xOffset, int yIndex, float zOffset) {
        matrixStackIn.translate(0.5 + xOffset, 0.27 + 0.03 * (yIndex + 1), 0.5 + zOffset);
        float f = -direction.toYRot();
        matrixStackIn.mulPose(Axis.YP.rotationDegrees(f));
        matrixStackIn.scale(0.8F, 0.8F, 0.8F);
    }

    public void renderItemCarved(PoseStack matrixStackIn, Direction direction, ItemStack itemStack) {
        matrixStackIn.translate(0.5, 0.23, 0.5);
        float f = -direction.toYRot() + 180.0F;
        matrixStackIn.mulPose(Axis.YP.rotationDegrees(f));
        Item toolItem = itemStack.getItem();
        float poseAngle;
        if (itemStack.is(ItemTags.PICKAXES) || toolItem instanceof HoeItem) {
            poseAngle = 225.0F;
        } else if (toolItem instanceof TridentItem) {
            poseAngle = 135.0F;
        } else {
            poseAngle = 180.0F;
        }

        matrixStackIn.mulPose(Axis.ZP.rotationDegrees(poseAngle));
        matrixStackIn.scale(0.6F, 0.6F, 0.6F);
    }

    protected int getModelCount(ItemStack stack) {
        int modelCount = 1;
        if (stack.getCount() > 1) {
            modelCount += Mth.ceil((float)stack.getCount() / stack.getMaxStackSize() * 4.0F);
        }

        return modelCount;
    }
}
