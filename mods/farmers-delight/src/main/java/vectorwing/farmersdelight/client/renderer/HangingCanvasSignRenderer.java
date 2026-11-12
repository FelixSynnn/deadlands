package vectorwing.farmersdelight.client.renderer;

import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.client.model.Model.Simple;
import net.minecraft.client.renderer.blockentity.HangingSignRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.client.renderer.blockentity.HangingSignRenderer.AttachmentType;
import net.minecraft.client.resources.model.Material;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.phys.Vec3;
import vectorwing.farmersdelight.common.registry.ModAtlases;

public class HangingCanvasSignRenderer extends AbstractCanvasSignRenderer {
    private static final Vec3 TEXT_OFFSET = new Vec3(0.0, -0.32F, 0.073F);
    private final Map<AttachmentType, Simple> hangingSignModels;

    public HangingCanvasSignRenderer(Context context) {
        super(context);
        this.hangingSignModels = Stream.of(AttachmentType.values())
            .collect(
                ImmutableMap.toImmutableMap(
                    attachmentType -> attachmentType,
                    attachmentType -> HangingSignRenderer.createSignModel(context.entityModelSet(), WoodType.SPRUCE, attachmentType)
                )
            );
    }

    @Override
    protected Simple getSignModel(BlockState state) {
        AttachmentType attachmentType = AttachmentType.byBlockState(state);
        return this.hangingSignModels.get(attachmentType);
    }

    @Override
    protected Material getSignMaterial(DyeColor dyeColor) {
        return ModAtlases.getHangingCanvasSignMaterial(dyeColor);
    }

    @Override
    public float getSignModelRenderScale() {
        return 1.0F;
    }

    @Override
    public float getSignTextRenderScale() {
        return 0.8F;
    }

    @Override
    protected Vec3 getTextOffset() {
        return TEXT_OFFSET;
    }

    @Override
    protected void translateSign(PoseStack poseStack, float yRot, BlockState state) {
        HangingSignRenderer.translateBase(poseStack, yRot);
    }
}
