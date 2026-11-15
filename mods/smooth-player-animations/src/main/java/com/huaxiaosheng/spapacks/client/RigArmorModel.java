package com.huaxiaosheng.spapacks.client;

import com.huaxiaosheng.spapacks.rig.RigSurface;
import com.huaxiaosheng.spapacks.rig.SkinnedMesh;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.reflect.Method;
import java.util.function.Predicate;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

public final class RigArmorModel extends HumanoidModel<AvatarRenderState> implements RigRoots.Drawn {
    public static final boolean[] HEAD = new boolean[]{true, false, false, false, false, false};
    public static final boolean[] CHEST = new boolean[]{false, true, true, true, false, false};
    public static final boolean[] LEGS = new boolean[]{false, true, false, false, true, true};
    public static final boolean[] FEET = new boolean[]{false, false, false, false, true, true};
    private final RigSurface surface;
    private final ModelPart[] parts;
    private final boolean[] slot;
    private final boolean[] visible = new boolean[6];
    private boolean custom;
    private static final ClassValue<Predicate<Object>> FIRST_PERSON = new ClassValue<Predicate<Object>>() {
        protected Predicate<Object> computeValue(Class<?> type) {
            try {
                Method pass = type.getMethod("playerAnimLib$isFirstPersonPass");
                return state -> {
                    try {
                        return Boolean.TRUE.equals(pass.invoke(state));
                    } catch (ReflectiveOperationException | RuntimeException ex) {
                        return false;
                    }
                };
            } catch (NoSuchMethodException | RuntimeException e) {
                return state -> false;
            }
        }
    };

    public RigArmorModel(ModelPart root, float dilation) {
        this(root, dilation, null);
    }

    public RigArmorModel(ModelPart root, float dilation, boolean[] slot) {
        super(root);
        this.surface = new RigSurface(SkinnedMesh.ALEX, false, false, dilation);
        this.parts = RigPlayerModel.parts(this);
        this.slot = slot;
        RigRoots.register(root, this);
    }

    void showParts(Object state) {
        if (this.slot != null && !FIRST_PERSON.get(state.getClass()).test(state)) {
            for (ModelPart part : this.parts) {
                part.visible = true;
            }
        }
    }

    void follow(float[][] palette) {
        this.custom = palette != null;
        if (this.custom) {
            this.surface.sample(palette);
        }
    }

    @Override
    public boolean drawRig(PoseStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
        if (!this.custom) {
            return false;
        }

        if (this.slot == null) {
            AlexRenderer.draw(SkinnedMesh.ALEX, this.surface, this.parts, matrices, vertices, light, overlay, color);
            return true;
        }

        for (int i = 0; i < 6; i++) {
            this.visible[i] = this.parts[i].visible;
            this.parts[i].visible = this.visible[i] && this.slot[i];
        }

        try {
            AlexRenderer.draw(SkinnedMesh.ALEX, this.surface, this.parts, matrices, vertices, light, overlay, color);
        } finally {
            for (int i = 0; i < 6; i++) {
                this.parts[i].visible = this.visible[i];
            }
        }

        return true;
    }
}
