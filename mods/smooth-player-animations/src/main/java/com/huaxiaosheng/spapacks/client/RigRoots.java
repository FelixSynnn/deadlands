package com.huaxiaosheng.spapacks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;

public final class RigRoots {
    private RigRoots() {
    }

    static void register(ModelPart root, RigRoots.Drawn model) {
        ((RigRoots.Holder)(Object)root).spaPacks$setDrawn(model);
    }

    public static boolean draw(ModelPart part, PoseStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
        RigRoots.Drawn model = ((RigRoots.Holder)(Object)part).spaPacks$drawn();
        return model != null && model.drawRig(matrices, vertices, light, overlay, color);
    }

    public interface Drawn {
        boolean drawRig(PoseStack var1, VertexConsumer var2, int var3, int var4, int var5);
    }

    public interface Holder {
        RigRoots.Drawn spaPacks$drawn();

        void spaPacks$setDrawn(RigRoots.Drawn var1);
    }
}
