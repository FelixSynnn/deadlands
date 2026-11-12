package com.huaxiaosheng.spapacks.client;

import com.huaxiaosheng.spapacks.platform.Vertices;
import com.huaxiaosheng.spapacks.util.MathUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class ItemDissolve {
    private static final Map<String, double[]> TIMING = Map.of("draw_weapon", new double[]{0.3, 0.8}, "draw_item", new double[]{0.48, 0.8});
    private static final double[] DEFAULT_TIMING = new double[]{0.28, 0.8};
    private static final int GRID = 16;
    private static final float NOISE = 0.3F;
    private static final float EDGE = 0.08F;
    private static final int GLOW_R = 170;
    private static final int GLOW_G = 245;
    private static final int GLOW_B = 255;
    private static final int GLOW_ALPHA = 210;
    private static final float GLOW_LIFT = 0.002F;
    private static final int FULL_LIGHT = 15728880;
    private static final int MAX_CELLS = 32;
    private static final ItemDissolve INSTANCE = new ItemDissolve();
    private final Map<RenderType, ItemDissolve.Collector> layers = new IdentityHashMap<>();
    private final List<ItemDissolve.Collector> pool = new ArrayList<>();
    private final ItemDissolve.Collector glowCells = new ItemDissolve.Collector();
    private final Matrix4f toHand = new Matrix4f();
    private float reveal;
    private boolean active;
    private final Vector3f origin = new Vector3f();
    private final Vector3f axisU = new Vector3f();
    private final Vector3f axisV = new Vector3f();
    private final Vector3f scratch = new Vector3f();
    private static final PoseStack DRAWN = new PoseStack();
    private SubmitNodeCollector standIn;
    private SubmitNodeCollector standInTarget;
    private static final int SUBMIT_ARGS = 8;
    private final float[] corner = new float[8];
    private final float[] cell = new float[44];

    private ItemDissolve() {
    }

    static float reveal(String clip, double since) {
        double[] timing = TIMING.getOrDefault(clip, DEFAULT_TIMING);
        return MathUtil.clamp01((float)((since - timing[0]) / timing[1]));
    }

    public static void submit(SubmitNodeCollector target, PoseStack matrices, float reveal, Consumer<SubmitNodeCollector> submit) {
        ItemDissolve d = INSTANCE;
        if (d.active) {
            submit.accept(target);
        } else {
            d.active = true;
            d.toHand.set(matrices.last().pose()).invert();
            d.reveal = reveal;

            try {
                submit.accept(d.standIn(target));
                Map<RenderType, ItemDissolve.Collector> cells = new IdentityHashMap<>();
                d.flush(layer -> cells.computeIfAbsent(layer, l -> {
                    ItemDissolve.Collector cx = new ItemDissolve.Collector();
                    cx.reset();
                    return cx;
                }));

                for (Entry<RenderType, ItemDissolve.Collector> e : cells.entrySet()) {
                    ItemDissolve.Collector c = e.getValue();
                    c.finish();
                    target.submitCustomGeometry(DRAWN, e.getKey(), (pose, vertices) -> {
                        try {
                            c.emitAll(vertices);
                        } catch (Throwable ex) {
                            SafeMode.fail("the held item's draw-in", ex);
                        }
                    });
                }
            } finally {
                d.pool.addAll(d.layers.values());
                d.layers.clear();
                d.standInTarget = null;
                d.active = false;
            }
        }
    }

    private SubmitNodeCollector standIn(SubmitNodeCollector target) {
        this.standInTarget = target;
        if (this.standIn == null) {
            this.standIn = (SubmitNodeCollector)Proxy.newProxyInstance(
                SubmitNodeCollector.class.getClassLoader(), new Class[]{SubmitNodeCollector.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "order":
                            return proxy;
                        case "submitItem":
                            this.collect(args);
                            return null;
                        case "equals":
                            return proxy == args[0];
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        case "toString":
                            return "ItemDissolve stand-in";
                        default:
                            if (method.isDefault()) {
                                return InvocationHandler.invokeDefault(proxy, method, args);
                            } else {
                                return this.reveal >= 0.5F ? method.invoke(this.standInTarget, args) : null;
                            }
                    }
                }
            );
        }

        return this.standIn;
    }

    private static int tint(int[] tints, boolean tinted, int index) {
        return tinted && tints != null && index >= 0 && index < tints.length ? tints[index] : -1;
    }

    private void collect(Object[] args) {
        if (args != null && args.length >= 8) {
            Pose pose = ((PoseStack)args[0]).last();
            int light = (Integer)args[2];
            int overlay = (Integer)args[3];
            int[] tints = (int[])args[5];
            VertexConsumer out = this.getBuffer((RenderType)args[7]);

            for (BakedQuad quad : (List<BakedQuad>)args[6]) {
                int color = tint(tints, quad.isTinted(), quad.tintIndex());
                out.putBulkData(
                    pose, quad, (color >> 16 & 0xFF) / 255.0F, (color >> 8 & 0xFF) / 255.0F, (color & 0xFF) / 255.0F, (color >>> 24) / 255.0F, light, overlay
                );
            }
        }
    }

    public VertexConsumer getBuffer(RenderType layer) {
        return this.layers.computeIfAbsent(layer, l -> {
            ItemDissolve.Collector c = this.pool.isEmpty() ? new ItemDissolve.Collector() : this.pool.remove(this.pool.size() - 1);
            c.reset();
            return c;
        });
    }

    private static boolean quads(RenderType layer) {
        return layer.mode() == Mode.QUADS;
    }

    private void flush(Function<RenderType, VertexConsumer> target) {
        for (ItemDissolve.Collector c : this.layers.values()) {
            c.finish();
        }

        if (this.mapping()) {
            float front = this.reveal * 1.23F;
            float glow = 1.0F - MathUtil.smoothstep01((this.reveal - 0.75F) / 0.25F);
            ItemDissolve.Collector glowCells = this.glowCells;
            glowCells.reset();

            for (Entry<RenderType, ItemDissolve.Collector> e : this.layers.entrySet()) {
                ItemDissolve.Collector c = e.getValue();
                if (c.count != 0) {
                    VertexConsumer out = target.apply(e.getKey());
                    if (quads(e.getKey()) && c.count % 4 == 0) {
                        TextureMasks.Mask mask = TextureMasks.of(e.getKey());

                        for (int q = 0; q < c.count; q += 4) {
                            this.quad(c, q, out, front, glowCells, mask);
                        }
                    } else if (this.reveal >= 0.5F) {
                        c.emitAll(out);
                    }
                }
            }

            if (glowCells.count > 0 && glow > 0.0F) {
                VertexConsumer out = target.apply(RenderTypes.lightning());
                int alpha = Math.round(210.0F * glow);

                for (int i = 0; i < glowCells.count; i++) {
                    float[] p = glowCells.position;
                    Vertices.put(
                        out,
                        p[i * 3],
                        p[i * 3 + 1],
                        p[i * 3 + 2],
                        alpha << 24 | 11141120 | 62720 | 0xFF,
                        0.0F,
                        0.0F,
                        0,
                        15728880,
                        glowCells.normal[i * 3],
                        glowCells.normal[i * 3 + 1],
                        glowCells.normal[i * 3 + 2]
                    );
                }
            }
        }
    }

    private boolean mapping() {
        Vector3f far = new Vector3f();
        float farthest = -1.0F;
        int total = 0;

        for (ItemDissolve.Collector c : this.layers.values()) {
            for (int i = 0; i < c.count; i++) {
                this.local(c, i, this.scratch);
                float d = this.scratch.lengthSquared();
                if (d > farthest) {
                    farthest = d;
                    far.set(this.scratch);
                }
            }

            total += c.count;
        }

        if (total != 0 && !(farthest <= 1.0E-10F)) {
            Vector3f dirU = far.normalize(new Vector3f());
            float[] minU = new float[]{Float.MAX_VALUE};
            float[] maxU = new float[]{-Float.MAX_VALUE};
            Vector3f[] across = new Vector3f[3];
            float[] spread = new float[3];
            float[][] range = new float[3][2];

            for (int a = 0; a < 3; a++) {
                Vector3f axis = new Vector3f(a == 0 ? 1.0F : 0.0F, a == 1 ? 1.0F : 0.0F, a == 2 ? 1.0F : 0.0F);
                axis.fma(-axis.dot(dirU), dirU);
                across[a] = axis.lengthSquared() < 1.0E-6F ? null : axis.normalize();
                range[a][0] = Float.MAX_VALUE;
                range[a][1] = -Float.MAX_VALUE;
            }

            for (ItemDissolve.Collector c : this.layers.values()) {
                for (int i = 0; i < c.count; i++) {
                    this.local(c, i, this.scratch);
                    float u = this.scratch.dot(dirU);
                    minU[0] = Math.min(minU[0], u);
                    maxU[0] = Math.max(maxU[0], u);

                    for (int a = 0; a < 3; a++) {
                        if (across[a] != null) {
                            float v = this.scratch.dot(across[a]);
                            range[a][0] = Math.min(range[a][0], v);
                            range[a][1] = Math.max(range[a][1], v);
                        }
                    }
                }
            }

            int best = -1;

            for (int a = 0; a < 3; a++) {
                spread[a] = across[a] == null ? -1.0F : range[a][1] - range[a][0];
                if (best < 0 || spread[a] > spread[best]) {
                    best = a;
                }
            }

            float lengthU = Math.max(maxU[0] - minU[0], 1.0E-4F);
            float lengthV = Math.max(spread[best], 1.0E-4F);
            this.axisU.set(dirU).div(lengthU);
            this.axisV.set(across[best]).div(lengthV);
            this.origin.set(dirU).mul(minU[0]).fma(range[best][0], across[best]);
            return true;
        } else {
            return false;
        }
    }

    private void local(ItemDissolve.Collector c, int i, Vector3f out) {
        this.toHand.transformPosition(c.position[i * 3], c.position[i * 3 + 1], c.position[i * 3 + 2], out);
    }

    private float mapU(float x, float y, float z) {
        this.toHand.transformPosition(x, y, z, this.scratch);
        return this.scratch.sub(this.origin).dot(this.axisU);
    }

    private float mapV(float x, float y, float z) {
        this.toHand.transformPosition(x, y, z, this.scratch);
        return this.scratch.sub(this.origin).dot(this.axisV);
    }

    private void quad(ItemDissolve.Collector c, int q, VertexConsumer out, float front, ItemDissolve.Collector glow, TextureMasks.Mask mask) {
        for (int k = 0; k < 4; k++) {
            int i = q + k;
            float x = c.position[i * 3];
            float y = c.position[i * 3 + 1];
            float z = c.position[i * 3 + 2];
            this.corner[k * 2] = this.mapU(x, y, z);
            this.corner[k * 2 + 1] = this.mapV(x, y, z);
        }

        int along = Math.max(cells(this.corner, 0, 1), texels(c, q, 1, mask));
        int across = Math.max(cells(this.corner, 0, 3), texels(c, q, 3, mask));

        for (int b = 0; b < across; b++) {
            for (int a = 0; a < along; a++) {
                float a0 = (float)a / along;
                float a1 = (float)(a + 1) / along;
                float b0 = (float)b / across;
                float b1 = (float)(b + 1) / across;
                float cu = bilinear(this.corner, 0, (a0 + a1) * 0.5F, (b0 + b1) * 0.5F);
                float cv = bilinear(this.corner, 1, (a0 + a1) * 0.5F, (b0 + b1) * 0.5F);
                float value = 0.7F * MathUtil.clamp01(cu) + 0.3F * noise(cu, cv);
                if (!(value >= front)) {
                    boolean edge = value >= front - 0.08F && this.reveal < 1.0F;
                    c.interpolate(q, a0, b0, this.cell, 0);
                    c.interpolate(q, a1, b0, this.cell, 1);
                    c.interpolate(q, a1, b1, this.cell, 2);
                    c.interpolate(q, a0, b1, this.cell, 3);
                    if (edge) {
                        float tu = (this.cell[4] + this.cell[26]) * 0.5F;
                        float tv = (this.cell[5] + this.cell[27]) * 0.5F;
                        edge = mask.solid(tu, tv);
                    }

                    for (int k = 0; k < 4; k++) {
                        int o = k * 11;
                        Vertices.put(
                            out,
                            this.cell[o],
                            this.cell[o + 1],
                            this.cell[o + 2],
                            Float.floatToRawIntBits(this.cell[o + 3]),
                            this.cell[o + 4],
                            this.cell[o + 5],
                            Float.floatToRawIntBits(this.cell[o + 6]),
                            edge ? 15728880 : Float.floatToRawIntBits(this.cell[o + 7]),
                            this.cell[o + 8],
                            this.cell[o + 9],
                            this.cell[o + 10]
                        );
                        if (edge) {
                            glow.add(
                                this.cell[o] + this.cell[o + 8] * 0.002F,
                                this.cell[o + 1] + this.cell[o + 9] * 0.002F,
                                this.cell[o + 2] + this.cell[o + 10] * 0.002F,
                                -1,
                                0.0F,
                                0.0F,
                                0,
                                15728880,
                                this.cell[o + 8],
                                this.cell[o + 9],
                                this.cell[o + 10]
                            );
                        }
                    }
                }
            }
        }
    }

    private static int texels(ItemDissolve.Collector c, int q, int to, TextureMasks.Mask mask) {
        float[] d = c.data;
        int o0 = q * 11;
        int o1 = (q + to) * 11;
        float n = mask.texels(d[o1 + 4] - d[o0 + 4], d[o1 + 5] - d[o0 + 5], (d[o0 + 4] + d[o1 + 4]) * 0.5F, (d[o0 + 5] + d[o1 + 5]) * 0.5F);
        return Math.max(1, Math.min(32, (int)Math.ceil(n - 0.001)));
    }

    private static int cells(float[] corner, int from, int to) {
        float du = corner[to * 2] - corner[from * 2];
        float dv = corner[to * 2 + 1] - corner[from * 2 + 1];
        return Math.max(1, Math.min(32, (int)Math.ceil(Math.sqrt(du * du + dv * dv) * 16.0 - 0.001)));
    }

    private static float bilinear(float[] corner, int c, float a, float b) {
        float low = corner[c] + (corner[2 + c] - corner[c]) * a;
        float high = corner[6 + c] + (corner[4 + c] - corner[6 + c]) * a;
        return low + (high - low) * b;
    }

    private static float noise(float u, float v) {
        int x = (int)Math.floor(u * 16.0F);
        int y = (int)Math.floor(v * 16.0F);
        int h = x * 668265261 ^ y * 374761393;
        h ^= h >>> 15;
        h *= 739982445;
        h ^= h >>> 12;
        return (h & 65535) / 65535.0F;
    }

    private static final class Collector extends Vertices.Sink {
        static final int FLOATS = 11;
        float[] position = new float[768];
        float[] normal = new float[768];
        float[] data = new float[2816];
        int count;
        private static final int[] LERPED = new int[]{0, 1, 2, 4, 5};
        private int color = -1;
        private float u;
        private float v;
        private int overlay;
        private int light;
        private boolean building;
        private float x;
        private float y;
        private float z;
        private float nx;
        private float ny;
        private float nz;

        void reset() {
            this.count = 0;
            this.building = false;
        }

        private void grow() {
            if (this.count == this.position.length / 3) {
                this.position = Arrays.copyOf(this.position, this.position.length * 2);
                this.normal = Arrays.copyOf(this.normal, this.normal.length * 2);
                this.data = Arrays.copyOf(this.data, this.data.length * 2);
            }
        }

        void add(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz) {
            this.grow();
            int i = this.count++;
            this.position[i * 3] = x;
            this.position[i * 3 + 1] = y;
            this.position[i * 3 + 2] = z;
            this.normal[i * 3] = nx;
            this.normal[i * 3 + 1] = ny;
            this.normal[i * 3 + 2] = nz;
            float[] d = this.data;
            int o = i * 11;
            d[o] = x;
            d[o + 1] = y;
            d[o + 2] = z;
            d[o + 3] = Float.intBitsToFloat(color);
            d[o + 4] = u;
            d[o + 5] = v;
            d[o + 6] = Float.intBitsToFloat(overlay);
            d[o + 7] = Float.intBitsToFloat(light);
            d[o + 8] = nx;
            d[o + 9] = ny;
            d[o + 10] = nz;
        }

        void interpolate(int q, float a, float b, float[] out, int k) {
            float[] d = this.data;
            int o0 = q * 11;
            int o1 = (q + 1) * 11;
            int o2 = (q + 2) * 11;
            int o3 = (q + 3) * 11;
            int o = k * 11;

            for (int f : LERPED) {
                float low = d[o0 + f] + (d[o1 + f] - d[o0 + f]) * a;
                float high = d[o3 + f] + (d[o2 + f] - d[o3 + f]) * a;
                out[o + f] = low + (high - low) * b;
            }

            out[o + 3] = Float.intBitsToFloat(
                lerpColor(
                    Float.floatToRawIntBits(d[o0 + 3]),
                    Float.floatToRawIntBits(d[o1 + 3]),
                    Float.floatToRawIntBits(d[o2 + 3]),
                    Float.floatToRawIntBits(d[o3 + 3]),
                    a,
                    b
                )
            );
            out[o + 6] = d[o0 + 6];
            out[o + 7] = d[o0 + 7];
            out[o + 8] = d[o0 + 8];
            out[o + 9] = d[o0 + 9];
            out[o + 10] = d[o0 + 10];
        }

        private static int lerpColor(int c0, int c1, int c2, int c3, float a, float b) {
            int out = 0;

            for (int s = 0; s < 32; s += 8) {
                float x0 = c0 >>> s & 0xFF;
                float x1 = c1 >>> s & 0xFF;
                float x2 = c2 >>> s & 0xFF;
                float x3 = c3 >>> s & 0xFF;
                float low = x0 + (x1 - x0) * a;
                float high = x3 + (x2 - x3) * a;
                out |= Math.round(low + (high - low) * b) << s;
            }

            return out;
        }

        void emitAll(VertexConsumer out) {
            float[] d = this.data;

            for (int i = 0; i < this.count; i++) {
                int o = i * 11;
                Vertices.put(
                    out,
                    d[o],
                    d[o + 1],
                    d[o + 2],
                    Float.floatToRawIntBits(d[o + 3]),
                    d[o + 4],
                    d[o + 5],
                    Float.floatToRawIntBits(d[o + 6]),
                    Float.floatToRawIntBits(d[o + 7]),
                    d[o + 8],
                    d[o + 9],
                    d[o + 10]
                );
            }
        }

        void finish() {
            if (this.building) {
                this.building = false;
                this.add(this.x, this.y, this.z, this.color, this.u, this.v, this.overlay, this.light, this.nx, this.ny, this.nz);
            }
        }

        @Override
        protected void end() {
            this.finish();
        }

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz) {
            this.finish();
            this.add(x, y, z, color, u, v, overlay, light, nx, ny, nz);
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.finish();
            this.building = true;
            this.x = x;
            this.y = y;
            this.z = z;
            this.color = -1;
            this.u = this.v = 0.0F;
            this.overlay = 0;
            this.light = 15728880;
            this.nx = 0.0F;
            this.ny = 1.0F;
            this.nz = 0.0F;
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            this.color = alpha << 24 | red << 16 | green << 8 | blue;
            return this;
        }

        @Override
        public VertexConsumer setColor(int argb) {
            return this.setColor(argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF, argb >>> 24);
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.u = u;
            this.v = v;
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            this.overlay = u & 65535 | v << 16;
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            this.light = u & 65535 | v << 16;
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            this.nx = x;
            this.ny = y;
            this.nz = z;
            this.finish();
            return this;
        }
    }
}
