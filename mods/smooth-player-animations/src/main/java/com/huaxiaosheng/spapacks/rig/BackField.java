package com.huaxiaosheng.spapacks.rig;

import java.util.Arrays;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class BackField implements CapeSim.Collider {
    public static final float CELL = 0.5F;
    private static final float X0 = -9.0F;
    private static final float Y0 = -9.0F;
    private static final int NX = 36;
    private static final int NY = 72;
    private static final float EMPTY = -Float.MAX_VALUE;
    private final float[] height = new float[2592];
    private float margin;
    private final Vector3f a = new Vector3f();
    private final Vector3f b = new Vector3f();
    private final Vector3f c = new Vector3f();
    private float lowest;
    private static final Matrix4f TO_MODEL = new Matrix4f().translation(0.0F, 1.501F, 0.0F).scale(1.0666667F, -1.0666667F, -1.0666667F);

    public static Matrix4f toChest(Matrix4f back, Matrix4f out) {
        return out.set(back).invert().mul(TO_MODEL).scaleLocal(16.0F);
    }

    public BackField() {
        this.clear();
    }

    public void clear() {
        Arrays.fill(this.height, -Float.MAX_VALUE);
    }

    public void margin(float margin) {
        this.margin = Math.max(0.0F, margin);
    }

    public void add(SkinnedMesh mesh, RigSurface surface, Matrix4f toChest, float[] grow, float[] from) {
        float[] p = surface.positions;

        for (SkinnedMesh.Triangle t : mesh.triangles()) {
            int[] v = t.v();
            int part = surface.partIndex(v[0]);
            if (!Float.isNaN(grow[part]) && (part != 1 || surface.torso == null)) {
                toChest.transformPosition(p[v[0] * 3], p[v[0] * 3 + 1], p[v[0] * 3 + 2], this.a);
                toChest.transformPosition(p[v[1] * 3], p[v[1] * 3 + 1], p[v[1] * 3 + 2], this.b);
                toChest.transformPosition(p[v[2] * 3], p[v[2] * 3 + 1], p[v[2] * 3 + 2], this.c);
                this.lowest = from[part];
                this.triangle(this.a, this.b, this.c, grow[part]);
            }
        }

        TorsoSurface torso = surface.torso;
        if (torso != null && !Float.isNaN(grow[1])) {
            float[] q = torso.positions;
            this.lowest = from[1];

            for (int[] quad : torso.quads) {
                for (int k = 0; k + 2 < quad.length; k++) {
                    int i0 = quad[0] * 3;
                    int i1 = quad[k + 1] * 3;
                    int i2 = quad[k + 2] * 3;
                    toChest.transformPosition(q[i0], q[i0 + 1], q[i0 + 2], this.a);
                    toChest.transformPosition(q[i1], q[i1 + 1], q[i1 + 2], this.b);
                    toChest.transformPosition(q[i2], q[i2 + 1], q[i2 + 2], this.c);
                    this.triangle(this.a, this.b, this.c, grow[1]);
                }
            }
        }

        this.lowest = -Float.MAX_VALUE;
    }

    public void triangle(Vector3f a, Vector3f b, Vector3f c, float extra) {
        float minX = Math.min(a.x, Math.min(b.x, c.x));
        float maxX = Math.max(a.x, Math.max(b.x, c.x));
        float minY = Math.min(a.y, Math.min(b.y, c.y));
        float maxY = Math.max(a.y, Math.max(b.y, c.y));
        int i0 = Math.max(0, (int)Math.floor((minX - -9.0F) / 0.5F));
        int i1 = Math.min(35, (int)Math.floor((maxX - -9.0F) / 0.5F));
        int j0 = Math.max(0, (int)Math.floor((minY - -9.0F) / 0.5F));
        int j1 = Math.min(71, (int)Math.floor((maxY - -9.0F) / 0.5F));
        float den = (b.y - c.y) * (a.x - c.x) + (c.x - b.x) * (a.y - c.y);
        if (Math.abs(den) > 1.0E-9F) {
            for (int j = j0; j <= j1; j++) {
                float y = -9.0F + (j + 0.5F) * 0.5F;

                for (int i = i0; i <= i1; i++) {
                    float x = -9.0F + (i + 0.5F) * 0.5F;
                    float l1 = ((b.y - c.y) * (x - c.x) + (c.x - b.x) * (y - c.y)) / den;
                    float l2 = ((c.y - a.y) * (x - c.x) + (a.x - c.x) * (y - c.y)) / den;
                    float l3 = 1.0F - l1 - l2;
                    if (l1 >= -1.0E-4F && l2 >= -1.0E-4F && l3 >= -1.0E-4F) {
                        this.put(x, y, l1 * a.z + l2 * b.z + l3 * c.z + extra);
                    }
                }
            }
        }

        this.edge(a, b, extra);
        this.edge(b, c, extra);
        this.edge(c, a, extra);
    }

    private void edge(Vector3f a, Vector3f b, float extra) {
        float length = (float)Math.sqrt((b.x - a.x) * (b.x - a.x) + (b.y - a.y) * (b.y - a.y));
        int steps = Math.max(1, (int)Math.ceil(length / 0.25F));

        for (int s = 0; s <= steps; s++) {
            float t = (float)s / steps;
            this.put(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t + extra);
        }
    }

    private void put(float x, float y, float z) {
        if (!(y <= this.lowest)) {
            int i = (int)Math.floor((x - -9.0F) / 0.5F);
            int j = (int)Math.floor((y - -9.0F) / 0.5F);
            if (i >= 0 && i < 36 && j >= 0 && j < 72) {
                int k = j * 36 + i;
                this.height[k] = Math.max(this.height[k], z);
            }
        }
    }

    public void bridge(float x, float halfWidth, float y, float z, float length, float maxSlope) {
        int i0 = Math.max(0, (int)Math.floor((x - halfWidth - -9.0F) / 0.5F));
        int i1 = Math.min(35, (int)Math.floor((x + halfWidth - -9.0F) / 0.5F));
        float slope = 0.0F;
        float touch = y;

        for (int j = 0; j < 72; j++) {
            float row = -9.0F + (j + 0.5F) * 0.5F;
            if (!(row <= y + 0.5F) && !(row > y + length)) {
                float most = -Float.MAX_VALUE;

                for (int i = i0; i <= i1; i++) {
                    most = Math.max(most, this.height[j * 36 + i]);
                }

                float need = most == -Float.MAX_VALUE ? 0.0F : (most + this.margin - z) / (row - y);
                if (need > slope) {
                    slope = need;
                    touch = row;
                }
            }
        }

        slope = Math.min(slope, maxSlope);
        if (!(slope <= 0.0F)) {
            for (int j = 0; j < 72; j++) {
                float row = -9.0F + (j + 0.5F) * 0.5F;
                if (!(row <= y) && !(row >= touch)) {
                    float line = z + slope * (row - y) - this.margin;

                    for (int i = i0; i <= i1; i++) {
                        int k = j * 36 + i;
                        if (this.height[k] != -Float.MAX_VALUE && this.height[k] < line) {
                            this.height[k] = line;
                        }
                    }
                }
            }
        }
    }

    public float behind(float x0, float x1, float y) {
        return this.behind(x0, x1, y, y);
    }

    public float behind(float x0, float x1, float y0, float y1) {
        int i0 = Math.max(0, (int)Math.floor((Math.min(x0, x1) - -9.0F) / 0.5F));
        int i1 = Math.min(35, (int)Math.floor((Math.max(x0, x1) - -9.0F) / 0.5F));
        int j0 = (int)Math.floor((Math.min(y0, y1) - -9.0F) / 0.5F - 0.5F);
        int j1 = (int)Math.floor((Math.max(y0, y1) - -9.0F) / 0.5F - 0.5F) + 1;
        float most = -Float.MAX_VALUE;

        for (int j = Math.max(0, j0); j <= Math.min(71, j1); j++) {
            for (int i = i0; i <= i1; i++) {
                most = Math.max(most, this.height[j * 36 + i]);
            }
        }

        return most == -Float.MAX_VALUE ? Float.NaN : most;
    }

    @Override
    public float least(float x, float y0, float y1, float halfWidth) {
        float h = this.behind(x - halfWidth, x + halfWidth, y0, y1);
        return Float.isNaN(h) ? Float.NaN : h + this.margin;
    }
}
