package com.huaxiaosheng.spapacks.client;

import com.huaxiaosheng.spapacks.SpaPacks;
import com.huaxiaosheng.spapacks.platform.Vertices;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class GeoArmorBend {
    private static final String RENDERER = "software.bernie.geckolib.renderer.GeoArmorRenderer";
    private static final String BONE = "software.bernie.geckolib.cache.object.GeoBone";
    private static final String[] ARMOR = new String[]{"head", "body", "rightArm", "leftArm", "rightLeg", "leftLeg", "rightBoot", "leftBoot"};
    private static final int[] SLOTS = new int[]{0, 1, 2, 3, 4, 5, 4, 5};
    private static Field[] fields;
    private static Method parent;
    private static boolean broken;
    private static Object bending;
    private static final Object[] BONES = new Object[ARMOR.length];
    private static final PoseStack ROOT = new PoseStack();
    private static final Matrix4f INVERSE = new Matrix4f();
    private static final Matrix3f INVERSE_NORMAL = new Matrix3f();
    private static HumanoidModel<?> resting;
    private static final PartPose[] POSES = new PartPose[6];
    private static Object cubes;

    private GeoArmorBend() {
    }

    public static void root(PoseStack matrices) {
        ROOT.last().pose().set(matrices.last().pose());
        ROOT.last().normal().set(matrices.last().normal());
    }

    public static void base(Object renderer, HumanoidModel<?> model) {
        bending = null;
        if (!broken && RigBend.posedHere(model) && bones(renderer)) {
            INVERSE.set(ROOT.last().pose()).invert();
            INVERSE_NORMAL.set(ROOT.last().normal()).invert();
            ModelPart[] parts = parts(model);

            for (int i = 0; i < parts.length; i++) {
                POSES[i] = parts[i].storePose();
                parts[i].loadPose(parts[i].getInitialPose());
            }

            resting = model;
            bending = renderer;
        }
    }

    public static void based(HumanoidModel<?> model) {
        if (resting == model) {
            ModelPart[] parts = parts(model);

            for (int i = 0; i < parts.length; i++) {
                parts[i].loadPose(POSES[i]);
            }

            resting = null;
        }
    }

    public static VertexConsumer vertices(Object renderer, VertexConsumer vertices) {
        return renderer == bending && vertices != null ? new GeoArmorBend.Bent(vertices) : vertices;
    }

    public static void cubes(Object bone) {
        cubes = bone;
    }

    private static ModelPart[] parts(HumanoidModel<?> model) {
        return new ModelPart[]{model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
    }

    private static boolean bones(Object renderer) {
        try {
            if (fields == null) {
                Class<?> type = renderer.getClass();

                while (type != null && !type.getName().equals("software.bernie.geckolib.renderer.GeoArmorRenderer")) {
                    type = type.getSuperclass();
                }

                if (type == null) {
                    throw new ClassNotFoundException("software.bernie.geckolib.renderer.GeoArmorRenderer");
                }

                Field[] found = new Field[ARMOR.length];

                for (int i = 0; i < ARMOR.length; i++) {
                    found[i] = type.getDeclaredField(ARMOR[i]);
                    found[i].setAccessible(true);
                }

                parent = Class.forName("software.bernie.geckolib.cache.object.GeoBone", false, type.getClassLoader()).getMethod("getParent");
                fields = found;
            }

            for (int i = 0; i < ARMOR.length; i++) {
                BONES[i] = fields[i].get(renderer);
            }

            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            broken = true;
            SpaPacks.LOGGER.warn("GeckoLib found but its armor renderer differs; GeckoLib armor is drawn straight on the bent rig", e);
            return false;
        }
    }

    private static int slot(Object bone) {
        try {
            for (Object b = bone; b != null; b = parent.invoke(b)) {
                for (int i = 0; i < BONES.length; i++) {
                    if (BONES[i] == b) {
                        return SLOTS[i];
                    }
                }
            }
        } catch (ReflectiveOperationException e) {
            SafeMode.fail("bending GeckoLib armor", e);
        }

        return -1;
    }

    private static final class Bent extends Vertices.Sink {
        private final VertexConsumer delegate;
        private Object bone;
        private int slot = -1;
        private VertexConsumer current;
        private final Vector3f position = new Vector3f();
        private final Vector3f normal = new Vector3f();

        Bent(VertexConsumer delegate) {
            this.delegate = delegate;
            this.current = delegate;
        }

        private VertexConsumer target() {
            if (GeoArmorBend.cubes != this.bone) {
                this.bone = GeoArmorBend.cubes;
                this.slot = GeoArmorBend.cubes == null ? -1 : GeoArmorBend.slot(GeoArmorBend.cubes);
            }

            return this.slot < 0 ? this.delegate : RigBend.skinnedAtRest(this.delegate, GeoArmorBend.ROOT.last(), this.slot);
        }

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz) {
            VertexConsumer target = this.target();
            if (target == this.delegate) {
                Vertices.put(this.delegate, x, y, z, color, u, v, overlay, light, nx, ny, nz);
            } else {
                GeoArmorBend.INVERSE.transformPosition(x, y, z, this.position);
                GeoArmorBend.INVERSE_NORMAL.transform(this.normal.set(nx, ny, nz)).normalize();
                Vertices.put(
                    target, this.position.x, this.position.y, this.position.z, color, u, v, overlay, light, this.normal.x, this.normal.y, this.normal.z
                );
            }
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.current = this.target();
            if (this.current == this.delegate) {
                Vertices.begin(this.delegate, x, y, z);
            } else {
                GeoArmorBend.INVERSE.transformPosition(x, y, z, this.position);
                Vertices.begin(this.current, this.position.x, this.position.y, this.position.z);
            }

            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            if (this.current == this.delegate) {
                Vertices.normal(this.delegate, x, y, z);
            } else {
                GeoArmorBend.INVERSE_NORMAL.transform(this.normal.set(x, y, z)).normalize();
                Vertices.normal(this.current, this.normal.x, this.normal.y, this.normal.z);
            }

            return this;
        }

        @Override
        protected void end() {
            Vertices.end(this.current);
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            Vertices.color(this.current, red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer setColor(int argb) {
            return this.setColor(argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF, argb >>> 24);
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            Vertices.uv(this.current, u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            Vertices.overlay(this.current, u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            Vertices.light(this.current, u, v);
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            this.current.setLineWidth(width);
            return this;
        }
    }
}
