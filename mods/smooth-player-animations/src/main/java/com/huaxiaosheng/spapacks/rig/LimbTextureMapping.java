package com.huaxiaosheng.spapacks.rig;

import com.huaxiaosheng.spapacks.util.MathUtil;
import java.util.Arrays;
import org.joml.Matrix4f;

public final class LimbTextureMapping {
    public static final float ERROR_PIXELS = 0.25F;
    private static final int MAX_DEPTH = 5;
    private final SkinnedMesh mesh;
    private final RigSurface surface;
    private final LimbTextureMapping.Chart[] charts;
    private final float[][] bounds = new float[4][6];
    private final float[][] inverse = new float[8][12];
    private final float[][] anchors = new float[4][6];
    private final int[] bones = new int[8];
    private final float[] row = new float[4];
    private final float dilation;
    private final float[][] root = new float[3][5];
    private final float[][][] work = new float[7][4][5];
    private final Matrix4f matrix = new Matrix4f();

    LimbTextureMapping(SkinnedMesh mesh, RigSurface surface, boolean outer) {
        this.mesh = mesh;
        this.surface = surface;
        this.dilation = outer ? 0.0146484375F : 0.0F;
        this.charts = new LimbTextureMapping.Chart[mesh.triangles().length];
        int[] starts = mesh.partStarts();
        String[] names = new String[]{"right_arm", "left_arm", "right_leg", "left_leg"};

        for (int l = 0; l < 4; l++) {
            this.bones[l * 2] = mesh.boneIndex(names[l]);
            this.bones[l * 2 + 1] = mesh.boneIndex(names[l] + "_lower_def");
            float[] b = this.bounds[l];
            Arrays.fill(b, 0, 3, Float.MAX_VALUE);
            Arrays.fill(b, 3, 6, -Float.MAX_VALUE);

            for (int v = starts[l + 2]; v < starts[l + 3]; v++) {
                for (int a = 0; a < 3; a++) {
                    b[a] = Math.min(b[a], surface.rest[v * 3 + a]);
                    b[a + 3] = Math.max(b[a + 3], surface.rest[v * 3 + a]);
                }
            }

            this.row[l] = Float.MAX_VALUE;
        }

        for (int i = 0; i < this.charts.length; i++) {
            SkinnedMesh.Triangle t = mesh.triangles()[i];
            int l = surface.partIndex(t.v()[0]) - 2;
            if (l >= 0) {
                float meanV = 0.0F;

                for (float[] uv : t.uv()) {
                    meanV += uv[1] * 64.0F / 3.0F;
                }

                int baseRow = l != 1 && l != 3 ? 16 : 48;
                if (!(meanV < baseRow + 4 - 0.001F)) {
                    int axis = -1;
                    float slope = 0.0F;
                    float offset = 0.0F;
                    float min = 1.0F;
                    float max = 0.0F;

                    for (int a : new int[]{0, 2}) {
                        for (int c = 1; c < 3; c++) {
                            float delta = surface.rest[t.v()[c] * 3 + a] - surface.rest[t.v()[0] * 3 + a];
                            float du = surface.uv[i][c][0] - surface.uv[i][0][0];
                            if (Math.abs(delta) > 1.0E-6F && Math.abs(du) > 1.0E-6F) {
                                axis = a;
                                slope = du / delta;
                                offset = surface.uv[i][0][0] - slope * surface.rest[t.v()[0] * 3 + a];
                            }
                        }
                    }

                    if (axis < 0) {
                        throw new IllegalStateException("Missing limb side chart " + i);
                    }

                    for (float[] uv : surface.uv[i]) {
                        min = Math.min(min, uv[0]);
                        max = Math.max(max, uv[0]);
                        this.row[l] = Math.min(this.row[l], uv[1]);
                    }

                    this.charts[i] = new LimbTextureMapping.Chart(l, axis, slope, offset, min, max);
                }
            }
        }
    }

    void sample(float[][] palette) {
        for (int l = 0; l < 4; l++) {
            for (int end = 0; end < 2; end++) {
                float[] m = palette[this.bones[l * 2 + end]];
                float[] b = this.bounds[l];
                float[] a = this.anchors[l];
                float x = (b[0] + b[3]) * 0.5F;
                float y = end == 0 ? b[4] : b[1];
                float z = (b[2] + b[5]) * 0.5F;

                for (int k = 0; k < 3; k++) {
                    a[end * 3 + k] = Palette.transform(m, k, x, y, z);
                }

                Palette.toMatrix(m, this.matrix).invert();
                Palette.fromMatrix(this.matrix, this.inverse[l * 2 + end]);
            }
        }
    }

    private boolean mapped(int triangle) {
        return this.charts[triangle] != null;
    }

    private void map(int triangle, float x, float y, float z, float[] out) {
        LimbTextureMapping.Chart c = this.charts[triangle];
        float[] a = this.anchors[c.limb];
        float[] b = this.bounds[c.limb];
        float ds = distance(x, y, z, a, 0);
        float dw = distance(x, y, z, a, 3);
        float w = MathUtil.smoothstep01((ds / Math.max(1.0E-8F, ds + dw) - 0.25F) * 2.0F);
        float[] u = this.inverse[c.limb * 2];
        float[] v = this.inverse[c.limb * 2 + 1];
        float q = local(u, c.axis, x, y, z) * (1.0F - w) + local(v, c.axis, x, y, z) * w;
        q = b[c.axis] + (q - b[c.axis] + this.dilation) * (b[c.axis + 3] - b[c.axis]) / (b[c.axis + 3] - b[c.axis] + 2.0F * this.dilation);
        float qy = local(u, 1, x, y, z) * (1.0F - w) + local(v, 1, x, y, z) * w;
        out[0] = x;
        out[1] = y;
        out[2] = z;
        out[3] = MathUtil.clamp(c.slope * q + c.offset, c.minU + 1.0E-7F, c.maxU - 1.0E-7F);
        out[4] = this.row[c.limb] + MathUtil.clamp((b[4] + this.dilation - qy) / (b[4] - b[1] + 2.0F * this.dilation), 1.0E-6F, 0.999999F) * 12.0F / 64.0F;
    }

    private static float local(float[] m, int axis, float x, float y, float z) {
        int k = axis * 4;
        return m[k] * x + m[k + 1] * y + m[k + 2] * z + m[k + 3];
    }

    private static float distance(float x, float y, float z, float[] p, int k) {
        x -= p[k];
        y -= p[k + 1];
        z -= p[k + 2];
        return (float)Math.sqrt(x * x + y * y + z * z);
    }

    public void emit(LimbTextureMapping.Sink sink) {
        for (int i = 0; i < this.charts.length; i++) {
            SkinnedMesh.Triangle tri = this.mesh.triangles()[i];

            for (int j = 0; j < 3; j++) {
                int v = tri.v()[j] * 3;
                float[] p = this.surface.positions;
                if (this.mapped(i)) {
                    this.map(i, p[v], p[v + 1], p[v + 2], this.root[j]);
                } else {
                    System.arraycopy(p, v, this.root[j], 0, 3);
                    System.arraycopy(this.surface.uv[i][j], 0, this.root[j], 3, 2);
                }
            }

            if (this.mapped(i)) {
                this.split(i, this.root[0], this.root[1], this.root[2], 0, sink);
            } else {
                sink.triangle(i, this.root[0], this.root[1], this.root[2]);
            }
        }
    }

    private void split(int i, float[] a, float[] b, float[] c, int depth, LimbTextureMapping.Sink sink) {
        float[][] s = this.work[depth];
        this.mid(i, a, b, s[0]);
        this.mid(i, b, c, s[1]);
        this.mid(i, c, a, s[2]);
        this.map(i, (a[0] + b[0] + c[0]) / 3.0F, (a[1] + b[1] + c[1]) / 3.0F, (a[2] + b[2] + c[2]) / 3.0F, s[3]);
        float error = 0.0F;

        for (int k = 3; k < 5; k++) {
            error = Math.max(error, Math.abs(s[0][k] - (a[k] + b[k]) * 0.5F));
            error = Math.max(error, Math.abs(s[1][k] - (b[k] + c[k]) * 0.5F));
            error = Math.max(error, Math.abs(s[2][k] - (c[k] + a[k]) * 0.5F));
            error = Math.max(error, Math.abs(s[3][k] - (a[k] + b[k] + c[k]) / 3.0F));
        }

        if (error * 64.0F > 0.25F && depth < 5) {
            this.split(i, a, s[0], s[2], depth + 1, sink);
            this.split(i, s[0], b, s[1], depth + 1, sink);
            this.split(i, s[2], s[1], c, depth + 1, sink);
            this.split(i, s[0], s[1], s[2], depth + 1, sink);
        } else {
            sink.triangle(i, a, b, c);
        }
    }

    private void mid(int i, float[] a, float[] b, float[] out) {
        this.map(i, (a[0] + b[0]) * 0.5F, (a[1] + b[1]) * 0.5F, (a[2] + b[2]) * 0.5F, out);
    }

    private record Chart(int limb, int axis, float slope, float offset, float minU, float maxU) {
    }

    @FunctionalInterface
    public interface Sink {
        void triangle(int var1, float[] var2, float[] var3, float[] var4);
    }
}
