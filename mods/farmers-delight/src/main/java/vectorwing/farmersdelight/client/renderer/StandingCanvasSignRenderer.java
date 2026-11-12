package vectorwing.farmersdelight.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.Model.Simple;
import net.minecraft.client.renderer.blockentity.SignRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.client.resources.model.Material;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.phys.Vec3;
import vectorwing.farmersdelight.common.registry.ModAtlases;

public class StandingCanvasSignRenderer extends AbstractCanvasSignRenderer {
    public static final float RENDER_SCALE = 0.6666667F;
    private static final Vec3 TEXT_OFFSET = new Vec3(0.0, 0.33333334F, 0.046666667F);
    private final StandingCanvasSignRenderer.Models signModels;

    public StandingCanvasSignRenderer(Context context) {
        super(context);
        this.signModels = new StandingCanvasSignRenderer.Models(
            SignRenderer.createSignModel(context.entityModelSet(), WoodType.SPRUCE, true),
            SignRenderer.createSignModel(context.entityModelSet(), WoodType.SPRUCE, false)
        );
    }

    @Override
    protected Simple getSignModel(BlockState state) {
        return state.getBlock() instanceof StandingSignBlock ? this.signModels.standing() : this.signModels.wall();
    }

    @Override
    protected Material getSignMaterial(DyeColor dyeColor) {
        return ModAtlases.getCanvasSignMaterial(dyeColor);
    }

    @Override
    public float getSignModelRenderScale() {
        return 0.6666667F;
    }

    @Override
    public float getSignTextRenderScale() {
        return 0.6666667F;
    }

    private static void translateBase(PoseStack poseStack, float yRot) {
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(yRot));
    }

    @Override
    protected Vec3 getTextOffset() {
        return TEXT_OFFSET;
    }

    @Override
    protected void translateSign(PoseStack poseStack, float yRot, BlockState state) {
        translateBase(poseStack, yRot);
        if (!(state.getBlock() instanceof StandingSignBlock)) {
            poseStack.translate(0.0F, -0.3125F, -0.4375F);
        }
    }

    record Models(Simple standing, Simple wall) {
    }
}
