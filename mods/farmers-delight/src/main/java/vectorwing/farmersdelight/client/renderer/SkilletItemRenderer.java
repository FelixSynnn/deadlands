package vectorwing.farmersdelight.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.serialization.MapCodec;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer.BakingContext;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3fc;
import vectorwing.farmersdelight.FarmersDelight;
import vectorwing.farmersdelight.common.item.component.ItemStackWrapper;
import vectorwing.farmersdelight.common.registry.ModDataComponents;
import vectorwing.farmersdelight.refabricated.duck.SkilletEntityRenderState;

public class SkilletItemRenderer implements SpecialModelRenderer<SkilletItemRenderer.SkilletData> {
    public static final Identifier ID = FarmersDelight.id("skillet");

    public void submit(
        @Nullable SkilletItemRenderer.SkilletData patterns,
        ItemDisplayContext mode,
        PoseStack poseStack,
        SubmitNodeCollector nodeCollector,
        int packedLight,
        int packedOverlay,
        boolean hasFoilType,
        int outlineColor
    ) {
        if (patterns != null) {
            Minecraft mc = Minecraft.getInstance();
            float animation = 0.0F;
            if (!patterns.ingredient.isEmpty() && mc.level != null) {
                poseStack.pushPose();
                poseStack.translate(0.5, 0.0625, 0.5);
                long gameTime = mc.level.getGameTime();
                if (patterns.skilletFlipTimestamp != -1L && mode != ItemDisplayContext.GUI) {
                    float partialTicks = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
                    float var16 = ((float)(gameTime - patterns.skilletFlipTimestamp) + partialTicks) / 12.0F;
                    animation = Mth.clamp(var16, 0.0F, 1.0F);
                    float maxH = 0.4F;
                    poseStack.translate(0.0F, maxH * Mth.sin(animation * (float) Math.PI), 0.0F);
                    float rotationAnimation = patterns.flipped ? animation + 1.0F : animation;
                    poseStack.mulPose(Axis.XP.rotationDegrees(180.0F * rotationAnimation));
                } else {
                    poseStack.mulPose(Axis.XP.rotationDegrees(patterns.flipped ? 180.0F : 0.0F));
                }

                poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
                poseStack.scale(0.5F, 0.5F, 0.5F);
                if (mode != ItemDisplayContext.GUI) {
                    ItemStackRenderState itemStackRenderState = new ItemStackRenderState();
                    Minecraft.getInstance()
                        .getItemModelResolver()
                        .updateForTopItem(itemStackRenderState, patterns.ingredient, ItemDisplayContext.FIXED, Minecraft.getInstance().level, null, 0);
                    itemStackRenderState.submit(poseStack, nodeCollector, packedLight, packedOverlay, outlineColor);
                }

                poseStack.popPose();
            }

            poseStack.pushPose();
            if (animation != 0.0F && mode.firstPerson()) {
                poseStack.translate(0.0F, 0.0F, 1.0F);
                poseStack.mulPose(Axis.XN.rotationDegrees(Mth.sin(animation * (float) (Math.PI * 2)) * 15.0F));
                poseStack.translate(0.0F, 0.0F, -1.0F);
                poseStack.translate(0.0, 0.0, -Mth.sin(animation * (float) Math.PI) * 0.2);
            }

            nodeCollector.submitBlock(poseStack, patterns.state, packedLight, packedOverlay, outlineColor);
            poseStack.popPose();
        }
    }

    @Override
    public void getExtents(Consumer<Vector3fc> consumer) {
    }

    @Nullable
    public SkilletItemRenderer.SkilletData extractArgument(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem blockItem) {
            BlockState var5 = blockItem.getBlock().defaultBlockState();
            ItemStackWrapper stackWrapper = stack.getOrDefault(ModDataComponents.SKILLET_INGREDIENT.get(), ItemStackWrapper.EMPTY);
            return new SkilletItemRenderer.SkilletData(
                var5,
                stackWrapper.getStack(),
                stack.getOrDefault(ModDataComponents.SKILLET_FLIP_TIMESTAMP.get(), -1L),
                stack.getOrDefault(ModDataComponents.SKILLET_FLIPPED.get(), false)
            );
        } else {
            return null;
        }
    }

    public static void applyTransform(HumanoidModel<?> model, SkilletEntityRenderState renderState, HumanoidArm arm) {
        long time = renderState.fdrf$getSkilletFlipTimestamp();
        if (time != -1L) {
            float partialTicks = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
            float animation = ((float)(Minecraft.getInstance().level.getGameTime() - time) + partialTicks) / 12.0F;
            animation = Mth.clamp(animation, 0.0F, 1.0F);
            if (arm == HumanoidArm.LEFT) {
                model.leftArm.xRot = (-Mth.sin(animation * (float) (Math.PI * 2)) * 15.0F - 20.0F) * (float) (Math.PI / 180.0);
            } else {
                model.rightArm.xRot = (-Mth.sin(animation * (float) (Math.PI * 2)) * 15.0F - 20.0F) * (float) (Math.PI / 180.0);
            }
        }
    }

    public record SkilletData(BlockState state, ItemStack ingredient, long skilletFlipTimestamp, boolean flipped) {
    }

    public static class Unbaked implements net.minecraft.client.renderer.special.SpecialModelRenderer.Unbaked {
        public static final MapCodec<SkilletItemRenderer.Unbaked> CODEC = MapCodec.unit(new SkilletItemRenderer.Unbaked());

        @Nullable
        @Override
        public SpecialModelRenderer<?> bake(BakingContext context) {
            return new SkilletItemRenderer();
        }

        @Override
        public MapCodec<SkilletItemRenderer.Unbaked> type() {
            return CODEC;
        }
    }
}
