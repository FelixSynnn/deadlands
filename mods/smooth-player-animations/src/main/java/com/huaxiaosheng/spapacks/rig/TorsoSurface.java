package com.huaxiaosheng.spapacks.rig;

import com.huaxiaosheng.spapacks.util.MathUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public final class TorsoSurface {
    public final float[] rest;
    public final float[] expandedRest;
    public final float[] positions;
    public final float[] normals;
    public final float[][] uv;
    public final float[][] weights;
    public final int[][] quads;
    private final int[] bones = new int[3];
    private final int[] normalGroups;
    private final float[] groupNormals;
    private final float bottom;
    private final float height;

    TorsoSurface(SkinnedMesh mesh, RigSurface surface, float[] offsets) {
        this.bones[0] = mesh.boneIndex("torso");
        this.bones[1] = mesh.boneIndex("torso_mid");
        this.bones[2] = mesh.boneIndex("torso_upper");
        int[] starts = mesh.partStarts();
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;

        for (int i = starts[1]; i < starts[2]; i++) {
            min = Math.min(min, surface.rest[i * 3 + 1]);
            max = Math.max(max, surface.rest[i * 3 + 1]);
        }

        this.bottom = min;
        this.height = max - min;
        ArrayList<float[]> points = new ArrayList<>();
        ArrayList<float[]> expanded = new ArrayList<>();
        ArrayList<float[]> coords = new ArrayList<>();
        ArrayList<int[]> faces = new ArrayList<>();
        ArrayList<Integer> groups = new ArrayList<>();
        HashMap<String, Integer> lookup = new HashMap<>();
        HashSet<Integer> handled = new HashSet<>();
        float pixel = 0.05859375F;

        for (int i = 0; i < mesh.triangles().length; i++) {
            SkinnedMesh.Triangle t = mesh.triangles()[i];
            if (surface.partIndex(t.v()[0]) == 1 && handled.add(t.face())) {
                HashMap<Integer, float[]> byVertex = new HashMap<>();

                for (int k = 0; k < 3; k++) {
                    byVertex.put(t.v()[k], surface.uv[i][k]);
                }

                int fourth = -1;

                for (int j = i + 1; j < mesh.triangles().length; j++) {
                    if (mesh.triangles()[j].face() == t.face()) {
                        SkinnedMesh.Triangle other = mesh.triangles()[j];

                        for (int k = 0; k < 3; k++) {
                            int v = other.v()[k];
                            if (!byVertex.containsKey(v)) {
                                fourth = v;
                            }

                            byVertex.put(v, surface.uv[j][k]);
                        }
                        break;
                    }
                }

                if (fourth < 0) {
                    throw new IllegalArgumentException("Torso quad missing fourth corner");
                }

                int[] corners = new int[]{t.v()[0], t.v()[1], t.v()[2], fourth};
                float[] a = point(surface.rest, corners[0]);
                float[] b = point(surface.rest, corners[1]);
                float[] d = point(surface.rest, corners[3]);
                float[] u = sub(b, a);
                float[] v = sub(d, a);
                float[] n = cross(u, v);
                int axis = 0;

                for (int k = 1; k < 3; k++) {
                    if (Math.abs(n[k]) > Math.abs(n[axis])) {
                        axis = k;
                    }
                }

                String side = axis + ":" + (n[axis] > 0.0F ? 1 : -1);
                int nu = segments(u, pixel);
                int nv = segments(v, pixel);
                int start = points.size();

                for (int y = 0; y <= nv; y++) {
                    for (int x = 0; x <= nu; x++) {
                        float s = (float)x / nu;
                        float tv = (float)y / nv;
                        float[] p = new float[3];
                        float[] e = new float[3];
                        float[] tex = new float[2];
                        float[] blend = new float[]{(1.0F - s) * (1.0F - tv), s * (1.0F - tv), s * tv, (1.0F - s) * tv};

                        for (int c = 0; c < 4; c++) {
                            int at = corners[c] * 3;

                            for (int kx = 0; kx < 3; kx++) {
                                p[kx] += surface.rest[at + kx] * blend[c];
                                e[kx] += (surface.rest[at + kx] + offsets[at + kx]) * blend[c];
                            }

                            for (int kx = 0; kx < 2; kx++) {
                                tex[kx] += ((float[])byVertex.get(corners[c]))[kx] * blend[c];
                            }
                        }

                        String key = side + ":" + Math.round(p[0] * 1000000.0F) + ":" + Math.round(p[1] * 1000000.0F) + ":" + Math.round(p[2] * 1000000.0F);
                        groups.add(lookup.computeIfAbsent(key, unused -> lookup.size()));
                        points.add(p);
                        expanded.add(e);
                        coords.add(tex);
                    }
                }

                for (int y = 0; y < nv; y++) {
                    for (int x = 0; x < nu; x++) {
                        int kx = start + y * (nu + 1) + x;
                        faces.add(new int[]{kx, kx + 1, kx + nu + 2, kx + nu + 1});
                    }
                }
            }
        }

        this.rest = flatten(points);
        this.expandedRest = flatten(expanded);
        this.positions = new float[this.rest.length];
        this.normals = new float[this.rest.length];
        this.uv = coords.toArray(float[][]::new);
        this.quads = faces.toArray(int[][]::new);
        this.normalGroups = groups.stream().mapToInt(Integer::intValue).toArray();
        this.groupNormals = new float[lookup.size() * 3];
        this.weights = new float[points.size()][3];

        for (int ix = 0; ix < this.weights.length; ix++) {
            bands((this.rest[ix * 3 + 1] - this.bottom) / this.height * 12.0F, this.weights[ix]);
        }
    }

    private static int segments(float[] delta, float pixel) {
        float resolution = pixel * 0.5F;
        return Math.max(1, Math.round((float)Math.sqrt(delta[0] * delta[0] + delta[1] * delta[1] + delta[2] * delta[2]) / resolution));
    }

    public static void bands(float h, float[] w) {
        w[0] = 0.0F;
        w[1] = 0.0F;
        w[2] = 0.0F;
        if (h <= 2.0F) {
            w[0] = 1.0F;
        } else if (h >= 10.0F) {
            w[2] = 1.0F;
        } else {
            int lower = h < 6.0F ? 0 : 1;
            float t = MathUtil.smoothstep((h - (lower == 0 ? 2 : 6)) / 4.0F);
            w[lower] = 1.0F - t;
            w[lower + 1] = t;
        }
    }

    void sample(float[][] palette) {
        for (int i = 0; i < this.weights.length; i++) {
            int at = i * 3;
            float x = this.expandedRest[at];
            float y = this.expandedRest[at + 1];
            float z = this.expandedRest[at + 2];

            for (int k = 0; k < 3; k++) {
                float sum = 0.0F;

                for (int b = 0; b < 3; b++) {
                    float[] m = palette[this.bones[b]];
                    int row = k * 4;
                    sum += this.weights[i][b] * (m[row] * x + m[row + 1] * y + m[row + 2] * z + m[row + 3]);
                }

                this.positions[at + k] = sum;
            }
        }

        Arrays.fill(this.groupNormals, 0.0F);

        for (int[] q : this.quads) {
            int a = q[0] * 3;
            int b = q[1] * 3;
            int c = q[2] * 3;
            int d = q[3] * 3;
            float ux = this.positions[c] - this.positions[a];
            float uy = this.positions[c + 1] - this.positions[a + 1];
            float uz = this.positions[c + 2] - this.positions[a + 2];
            float vx = this.positions[d] - this.positions[b];
            float vy = this.positions[d + 1] - this.positions[b + 1];
            float vz = this.positions[d + 2] - this.positions[b + 2];
            float nx = uy * vz - uz * vy;
            float ny = uz * vx - ux * vz;
            float nz = ux * vy - uy * vx;

            for (int vertex : q) {
                int g = this.normalGroups[vertex] * 3;
                this.groupNormals[g] = this.groupNormals[g] + nx;
                this.groupNormals[g + 1] = this.groupNormals[g + 1] + ny;
                this.groupNormals[g + 2] = this.groupNormals[g + 2] + nz;
            }
        }

        for (int i = 0; i < this.normalGroups.length; i++) {
            int g = this.normalGroups[i] * 3;
            float x = this.groupNormals[g];
            float y = this.groupNormals[g + 1];
            float z = this.groupNormals[g + 2];
            float length = (float)Math.sqrt(x * x + y * y + z * z);
            if (length < 1.0E-10F) {
                length = 1.0F;
            }

            this.normals[i * 3] = x / length;
            this.normals[i * 3 + 1] = y / length;
            this.normals[i * 3 + 2] = z / length;
        }
    }

    private static float[] point(float[] p, int v) {
        return new float[]{p[v * 3], p[v * 3 + 1], p[v * 3 + 2]};
    }

    private static float[] sub(float[] a, float[] b) {
        return new float[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static float[] cross(float[] a, float[] b) {
        return new float[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static float[] flatten(List<float[]> values) {
        float[] out = new float[values.size() * 3];

        for (int i = 0; i < values.size(); i++) {
            System.arraycopy(values.get(i), 0, out, i * 3, 3);
        }

        return out;
    }
}
