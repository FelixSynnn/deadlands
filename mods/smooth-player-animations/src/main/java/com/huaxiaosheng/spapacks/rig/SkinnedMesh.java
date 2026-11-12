package com.huaxiaosheng.spapacks.rig;

import com.google.gson.Gson;
import java.io.Reader;
import java.util.Arrays;
import java.util.HashSet;

public final class SkinnedMesh {
    public static final SkinnedMesh ALEX = new SkinnedMesh("authored_mesh.json", Skeleton.ALEX);
    private final SkinnedMesh.Data data;

    private SkinnedMesh(String resource, Skeleton skeleton) {
        try (Reader reader = RigResources.open(resource)) {
            this.data = new Gson().fromJson(reader, SkinnedMesh.Data.class);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load Alex mesh " + resource, e);
        }

        if (this.data.format == 2 && this.data.faceCount > 0 && this.data.partStarts != null && this.data.partStarts.length == 7) {
            if (!Arrays.equals(this.data.bones, skeleton.bones)) {
                throw new IllegalArgumentException("Mesh bones must match the rig bind order");
            }

            for (SkinnedMesh.Vertex v : this.data.vertices) {
                if (v.p.length != 3 || v.bones.length == 0 || v.bones.length != v.weights.length) {
                    throw new IllegalArgumentException("Vertex shape");
                }

                float total = 0.0F;

                for (int j = 0; j < v.bones.length; j++) {
                    if (v.bones[j] < 0 || v.bones[j] >= this.data.bones.length || v.weights[j] < 0.0F) {
                        throw new IllegalArgumentException("Binding");
                    }

                    total += v.weights[j];
                }

                if (Math.abs(total - 1.0F) > 1.0E-5) {
                    throw new IllegalArgumentException("Weights");
                }
            }

            SkinnedMesh.Triangle[] var18 = this.data.triangles;
            int count = var18.length;
            int var22 = 0;

            while (var22 < count) {
                SkinnedMesh.Triangle t = var18[var22];
                if (t.v.length == 3 && t.uv.length == 3) {
                    if (t.face >= 0 && t.face < this.data.faceCount) {
                        for (int i = 0; i < 3; i++) {
                            if (t.v[i] < 0 || t.v[i] >= this.data.vertices.length || t.uv[i].length != 2) {
                                throw new IllegalArgumentException("Index/UV");
                            }
                        }

                        var22++;
                        continue;
                    }

                    throw new IllegalArgumentException("Face index");
                }

                throw new IllegalArgumentException("Triangle");
            }

            for (int face = 0; face < this.data.faceCount; face++) {
                count = 0;
                HashSet<Integer> corners = new HashSet<>();

                for (SkinnedMesh.Triangle t : this.data.triangles) {
                    if (t.face == face) {
                        count++;

                        for (int v : t.v) {
                            corners.add(v);
                        }
                    }
                }

                if (count != 2 || corners.size() != 4) {
                    throw new IllegalArgumentException("Expected authored quad");
                }
            }
        } else {
            throw new IllegalArgumentException("Invalid mesh format");
        }
    }

    public int[] partStarts() {
        return (int[])this.data.partStarts.clone();
    }

    public int vertexCount() {
        return this.data.vertices.length;
    }

    public int boneCount() {
        return this.data.bones.length;
    }

    public int faceCount() {
        return this.data.faceCount;
    }

    public String boneName(int index) {
        return this.data.bones[index];
    }

    public SkinnedMesh.Triangle[] triangles() {
        return this.data.triangles;
    }

    public SkinnedMesh.Vertex[] vertices() {
        return this.data.vertices;
    }

    public int boneIndex(String name) {
        for (int i = 0; i < this.data.bones.length; i++) {
            if (this.data.bones[i].equals(name)) {
                return i;
            }
        }

        throw new IllegalArgumentException("Missing bone: " + name);
    }

    private static final class Data {
        int format;
        int faceCount;
        int[] partStarts;
        String[] bones;
        SkinnedMesh.Vertex[] vertices;
        SkinnedMesh.Triangle[] triangles;
    }

    public record Triangle(int[] v, float[][] uv, int face) {
    }

    public record Vertex(float[] p, int[] bones, float[] weights) {
    }
}
