package com.huaxiaosheng.spapacks.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;

public final class PunchyFaces {
    private static final float LIFT = 0.02F;
    private static ModelPart head;
    private static ModelPart hat;
    private static float headScale;
    private static float hatScale;

    private PunchyFaces() {
    }

    public static void begin(Object model) {
        end();
        if (model instanceof RigPlayerModel rig && rig.drawsRig()) {
            HumanoidModel<?> humanoid = rig;
            head = humanoid.head;
            hat = humanoid.hat;
            headScale = head.zScale;
            hatScale = hat.zScale;
            head.zScale = headScale * 1.02F;
            hat.zScale = hatScale * 1.02F;
        }
    }

    public static void end() {
        if (head != null) {
            head.zScale = headScale;
            hat.zScale = hatScale;
            head = null;
            hat = null;
        }
    }
}
