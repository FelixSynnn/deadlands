package com.huaxiaosheng.spapacks.client;

import com.huaxiaosheng.spapacks.mixin.ModelPartAccessor;
import com.huaxiaosheng.spapacks.platform.Vertices;
import com.huaxiaosheng.spapacks.rig.RigSurface;
import com.huaxiaosheng.spapacks.rig.SkinnedMesh;
import com.huaxiaosheng.spapacks.rig.TorsoSurface;
import com.huaxiaosheng.spapacks.util.MathUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.ModelPart.Cube;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class RigBend {
    static final String[] BONES = new String[]{
        "head",
        "torso",
        "torso_mid",
        "torso_upper",
        "right_arm",
        "right_arm_lower_def",
        "left_arm",
        "left_arm_lower_def",
        "right_leg",
        "right_leg_lower_def",
        "left_leg",
        "left_leg_lower_def"
    };
    private static final int HEAD = 0;
    private static final int TORSO = 1;
    private static final int UPPER_LIMB = 4;
    private static final float MODEL_SCALE = 1.0666667F;
    private static final float MODEL_Y = 1.501F;
    private static final float PIXEL = 0.05859375F;
    private static final float HIPS = 0.703125F;
    private static final float ELBOW = 1.0546875F;
    private static final float KNEE = 0.3515625F;
    private static final float JOINT_BLEND = 0.087890625F;
    private static final float MATCH = 1.0E-4F;
    private static final float ABOVE_SKIN = 0.02F;
    private static final float[][] POSED = new float[6][6];
    private static final Matrix4f[] MATRICES = new Matrix4f[BONES.length];
    private static final PoseStack REST = new PoseStack();
    private static final RigBend.Skinning SKINNING = new RigBend.Skinning();
    private static boolean active;
    private static boolean drawing;
    private static float[][] palette;
    private static int frame;
    private static final List<RigBend.ArmorSurface> SURFACES = new ArrayList<>();
    private static final Map<Cube, float[]> SHAPES = new WeakHashMap<>();
    private static final RigBend.UvProbe PROBE = new RigBend.UvProbe();
    private static final Vector3f EMIT_POSITION;
    private static final Vector3f EMIT_NORMAL;
    private static final RigBend.Skinning[] AT_REST;

    private RigBend() {
    }

    static void begin(float[][] posed, Matrix4f[] bones, float[][] skin) {
        palette = skin;
        frame++;

        for (int i = 0; i < 6; i++) {
            System.arraycopy(posed[i], 0, POSED[i], 0, 6);
        }

        for (int i = 0; i < MATRICES.length; i++) {
            MATRICES[i].set(bones[i]);
        }

        active = true;
    }

    public static void end() {
        active = false;
    }

    public static boolean render(ModelPart part, PoseStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
        if (active && !drawing && part.visible && !CapeCloth.owns(part)) {
            int slot = slot(part);
            if (slot < 0) {
                return false;
            }

            float dilation = armorDilation(part, slot);
            if (dilation >= 0.0F) {
                drawArmor(slot, dilation, matrices, vertices, light, overlay, color);
                return true;
            }

            drawing = true;
            PartPose posed = part.storePose();

            try {
                part.loadPose(part.getInitialPose());
                REST.setIdentity();
                SKINNING.begin(vertices, matrices.last(), slot);
                Vertices.render(part, REST, SKINNING, light, overlay, color);
            } finally {
                SKINNING.delegate = null;
                part.loadPose(posed);
                drawing = false;
            }

            return true;
        } else {
            return false;
        }
    }

    private static void drawArmor(int slot, float dilation, PoseStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
        RigSurface surface = armorSurface(dilation);
        matrices.pushPose();

        try {
            AlexRenderer.enterModelSpace(matrices);
            Pose entry = matrices.last();
            Matrix4f pose = entry.pose();
            if (slot == 1 && surface.torso != null) {
                TorsoSurface torso = surface.torso;

                for (int[] quad : torso.quads) {
                    for (int v : quad) {
                        int k = v * 3;
                        emit(
                            vertices,
                            entry,
                            pose,
                            torso.positions,
                            k,
                            torso.uv[v][0],
                            torso.uv[v][1],
                            torso.normals[k],
                            torso.normals[k + 1],
                            torso.normals[k + 2],
                            light,
                            overlay,
                            color
                        );
                    }
                }
            } else {
                SkinnedMesh mesh = SkinnedMesh.ALEX;
                float[] p = surface.positions;

                for (int i = 0; i < mesh.triangles().length; i++) {
                    SkinnedMesh.Triangle tri = mesh.triangles()[i];
                    if (surface.partIndex(tri.v()[0]) == slot) {
                        int f = tri.face() * 3;

                        for (int j = 0; j < 4; j++) {
                            int corner = Math.min(j, 2);
                            float[] uv = surface.uv[i][corner];
                            emit(
                                vertices,
                                entry,
                                pose,
                                p,
                                tri.v()[corner] * 3,
                                uv[0],
                                uv[1],
                                surface.faceNormals[f],
                                surface.faceNormals[f + 1],
                                surface.faceNormals[f + 2],
                                light,
                                overlay,
                                color
                            );
                        }
                    }
                }
            }
        } finally {
            matrices.popPose();
        }
    }

    private static RigSurface armorSurface(float dilation) {
        RigBend.ArmorSurface found = null;

        for (int i = 0; i < SURFACES.size(); i++) {
            RigBend.ArmorSurface s = SURFACES.get(i);
            if (s.dilation == dilation) {
                found = s;
                break;
            }
        }

        if (found == null) {
            found = new RigBend.ArmorSurface(dilation, new RigSurface(SkinnedMesh.ALEX, false, false, dilation + 0.02F));
            SURFACES.add(found);
        }

        if (!found.sampled || found.frame != frame) {
            found.surface.sample(palette);
            found.sampled = true;
            found.frame = frame;
        }

        return found.surface;
    }

    private static void emit(
        VertexConsumer vertices,
        Pose entry,
        Matrix4f pose,
        float[] positions,
        int k,
        float u,
        float v,
        float nx,
        float ny,
        float nz,
        int light,
        int overlay,
        int color
    ) {
        pose.transformPosition(positions[k], positions[k + 1], positions[k + 2], EMIT_POSITION);
        Vertices.transformNormal(entry, nx, ny, nz, EMIT_NORMAL);
        Vertices.put(vertices, EMIT_POSITION.x, EMIT_POSITION.y, EMIT_POSITION.z, color, u, v, overlay, light, EMIT_NORMAL.x, EMIT_NORMAL.y, EMIT_NORMAL.z);
    }

    private static float armorDilation(ModelPart part, int slot) {
        if (slot == 0) {
            return -1.0F;
        } else {
            ModelPartAccessor access = (ModelPartAccessor)(Object)part;
            List<Cube> cuboids = access.spaPacks$cuboids();
            if (cuboids.size() == 1 && access.spaPacks$children().isEmpty()) {
                Cube c = cuboids.get(0);
                float[] shape = SHAPES.computeIfAbsent(c, RigBend::shape);
                return shape[slot] > 0.0F ? shape[slot] : -1.0F;
            } else {
                return -1.0F;
            }
        }
    }

    private static float[] shape(Cube c) {
        float[] out = new float[]{-1.0F, -1.0F, -1.0F, -1.0F, -1.0F, -1.0F};
        PROBE.reset();
        c.compile(PROBE.entry, PROBE, 0, 0, -1);
        if (Math.abs(PROBE.minV * 32.0F - 16.0F) > 0.01F) {
            return out;
        }

        float u = PROBE.minU * 64.0F;
        float[][] boxes = new float[][]{
            null,
            {-4.0F, 0.0F, -2.0F, 4.0F, 12.0F, 2.0F, 16.0F},
            {-3.0F, -2.0F, -2.0F, 1.0F, 10.0F, 2.0F, 40.0F},
            {-1.0F, -2.0F, -2.0F, 3.0F, 10.0F, 2.0F, 40.0F},
            {-2.0F, 0.0F, -2.0F, 2.0F, 12.0F, 2.0F, 0.0F},
            {-2.0F, 0.0F, -2.0F, 2.0F, 12.0F, 2.0F, 0.0F}
        };
        float[] min = PROBE.min;
        float[] max = PROBE.max;

        for (int slot = 1; slot < 6; slot++) {
            float[] b = boxes[slot];
            float d = b[1] - min[1];
            if (d > 0.0F
                && d < 4.0F
                && Math.abs(u - b[6]) < 0.01F
                && near(min[0], b[0] - d)
                && near(min[2], b[2] - d)
                && near(max[0], b[3] + d)
                && near(max[1], b[4] + d)
                && near(max[2], b[5] + d)) {
                out[slot] = d;
            }
        }

        return out;
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 0.01F;
    }

    private static int slot(ModelPart part) {
        for (int i = 0; i < 6; i++) {
            if (posedAs(part, i)) {
                return i;
            }
        }

        return -1;
    }

    private static boolean posedAs(ModelPart part, int slot) {
        float[] p = POSED[slot];
        return Math.abs(part.x - p[0]) < 1.0E-4F
            && Math.abs(part.y - p[1]) < 1.0E-4F
            && Math.abs(part.z - p[2]) < 1.0E-4F
            && Math.abs(part.xRot - p[3]) < 1.0E-4F
            && Math.abs(part.yRot - p[4]) < 1.0E-4F
            && Math.abs(part.zRot - p[5]) < 1.0E-4F;
    }

    static boolean posedHere(HumanoidModel<?> model) {
        return active
            && !drawing
            && posedAs(model.body, 1)
            && posedAs(model.rightArm, 2)
            && posedAs(model.leftArm, 3)
            && posedAs(model.rightLeg, 4)
            && posedAs(model.leftLeg, 5);
    }

    static VertexConsumer skinnedAtRest(VertexConsumer vertices, Pose root, int slot) {
        RigBend.Skinning s = AT_REST[slot];
        s.begin(vertices, root, slot);
        return s;
    }

    static {
        for (int i = 0; i < MATRICES.length; i++) {
            MATRICES[i] = new Matrix4f();
        }

        EMIT_POSITION = new Vector3f();
        EMIT_NORMAL = new Vector3f();
        AT_REST = new RigBend.Skinning[6];

        for (int i = 0; i < AT_REST.length; i++) {
            AT_REST[i] = new RigBend.Skinning();
        }
    }

    private static final class ArmorSurface {
        final float dilation;
        final RigSurface surface;
        boolean sampled;
        int frame;

        ArmorSurface(float dilation, RigSurface surface) {
            this.dilation = dilation;
            this.surface = surface;
        }
    }

    private static final class Skinning extends Vertices.Sink {
        private VertexConsumer delegate;
        private Pose entry;
        private int slot;
        private final int[] bones = new int[3];
        private final float[] weights = new float[3];
        private final float[] bands = new float[3];
        private int count;
        private final Vector3f position = new Vector3f();
        private final Vector3f normal = new Vector3f();

        void begin(VertexConsumer delegate, Pose entry, int slot) {
            this.delegate = delegate;
            this.entry = entry;
            this.slot = slot;
        }

        @Override
        protected void end() {
            Vertices.end(this.delegate);
        }

        private void weigh(float y) {
            float height = (1.501F - y) / 1.0666667F;
            if (this.slot == 0) {
                this.set1(0);
            } else if (this.slot == 1) {
                TorsoSurface.bands((height - 0.703125F) / 0.703125F * 12.0F, this.bands);
                this.count = 0;

                for (int i = 0; i < 3; i++) {
                    if (this.bands[i] != 0.0F) {
                        this.bones[this.count] = 1 + i;
                        this.weights[this.count++] = this.bands[i];
                    }
                }
            } else {
                int upper = 4 + (this.slot - 2) * 2;
                float joint = this.slot < 4 ? 1.0546875F : 0.3515625F;
                float t = MathUtil.smoothstep01((joint + 0.087890625F - height) / 0.17578125F);
                this.set2(upper, 1.0F - t, upper + 1, t);
            }
        }

        private void set1(int bone) {
            this.bones[0] = bone;
            this.weights[0] = 1.0F;
            this.count = 1;
        }

        private void set2(int a, float wa, int b, float wb) {
            this.bones[0] = a;
            this.weights[0] = wa;
            this.bones[1] = b;
            this.weights[1] = wb;
            this.count = 2;
        }

        private void skinPosition(float x, float y, float z) {
            float sx = 0.0F;
            float sy = 0.0F;
            float sz = 0.0F;

            for (int i = 0; i < this.count; i++) {
                Matrix4f m = RigBend.MATRICES[this.bones[i]];
                float w = this.weights[i];
                sx += w * (m.m00() * x + m.m10() * y + m.m20() * z + m.m30());
                sy += w * (m.m01() * x + m.m11() * y + m.m21() * z + m.m31());
                sz += w * (m.m02() * x + m.m12() * y + m.m22() * z + m.m32());
            }

            this.entry.pose().transformPosition(sx, sy, sz, this.position);
        }

        private void skinNormal(float x, float y, float z) {
            float sx = 0.0F;
            float sy = 0.0F;
            float sz = 0.0F;

            for (int i = 0; i < this.count; i++) {
                Matrix4f m = RigBend.MATRICES[this.bones[i]];
                float w = this.weights[i];
                sx += w * (m.m00() * x + m.m10() * y + m.m20() * z);
                sy += w * (m.m01() * x + m.m11() * y + m.m21() * z);
                sz += w * (m.m02() * x + m.m12() * y + m.m22() * z);
            }

            Vertices.transformNormal(this.entry, this.normal.set(sx, sy, sz).normalize(), this.normal);
        }

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz) {
            this.weigh(y);
            this.skinPosition(x, y, z);
            this.skinNormal(nx, ny, nz);
            Vertices.put(
                this.delegate, this.position.x, this.position.y, this.position.z, color, u, v, overlay, light, this.normal.x, this.normal.y, this.normal.z
            );
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.weigh(y);
            this.skinPosition(x, y, z);
            Vertices.begin(this.delegate, this.position.x, this.position.y, this.position.z);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            this.skinNormal(x, y, z);
            Vertices.normal(this.delegate, this.normal.x, this.normal.y, this.normal.z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            Vertices.color(this.delegate, red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer setColor(int argb) {
            return this.setColor(argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF, argb >>> 24);
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            Vertices.uv(this.delegate, u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            Vertices.overlay(this.delegate, u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            Vertices.light(this.delegate, u, v);
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            this.delegate.setLineWidth(width);
            return this;
        }
    }

    private static final class UvProbe extends Vertices.Sink {
        final Pose entry = new PoseStack().last();
        final float[] min = new float[3];
        final float[] max = new float[3];
        float minU;
        float minV;

        void reset() {
            this.minU = Float.MAX_VALUE;
            this.minV = Float.MAX_VALUE;
            Arrays.fill(this.min, Float.MAX_VALUE);
            Arrays.fill(this.max, -Float.MAX_VALUE);
        }

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz) {
            this.minU = Math.min(this.minU, u);
            this.minV = Math.min(this.minV, v);
            this.point(x, y, z);
        }

        private void point(float x, float y, float z) {
            float[] p = new float[]{x * 16.0F, y * 16.0F, z * 16.0F};

            for (int a = 0; a < 3; a++) {
                this.min[a] = Math.min(this.min[a], p[a]);
                this.max[a] = Math.max(this.max[a], p[a]);
            }
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.point(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int argb) {
            return this.setColor(argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF, argb >>> 24);
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.minU = Math.min(this.minU, u);
            this.minV = Math.min(this.minV, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }
    }
}
