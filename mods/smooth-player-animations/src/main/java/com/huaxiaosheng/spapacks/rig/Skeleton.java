package com.huaxiaosheng.spapacks.rig;

import com.google.gson.Gson;
import java.io.Reader;
import java.util.Arrays;
import org.joml.Matrix4f;

public final class Skeleton {
    public static final Skeleton ALEX = new Skeleton("authored_bind.json");
    public static final Skeleton CHAIN = new Skeleton("torso_chain_bind.json");
    final String[] bones;
    final int[] parents;
    final int[] controls;
    final float[][] scales;
    final Matrix4f[] relative;
    final Matrix4f[] inverse;
    final int[] controlOf;

    private Skeleton(String resource) {
        Skeleton.Bind bind;
        try (Reader reader = RigResources.open(resource)) {
            bind = new Gson().fromJson(reader, Skeleton.Bind.class);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load rig bind " + resource, e);
        }

        int n = bind.bones.length;
        this.bones = bind.bones;
        this.parents = bind.parents;
        this.controls = bind.controls;
        this.scales = bind.scales;
        this.relative = new Matrix4f[n];
        this.inverse = new Matrix4f[n];
        Matrix4f[] rest = new Matrix4f[n];

        for (int i = 0; i < n; i++) {
            float[] m = bind.rest[i];
            rest[i] = new Matrix4f().set(m[0], m[4], m[8], m[12], m[1], m[5], m[9], m[13], m[2], m[6], m[10], m[14], m[3], m[7], m[11], m[15]);
            this.inverse[i] = new Matrix4f(rest[i]).invert();
        }

        for (int i = 0; i < n; i++) {
            int parent = this.parents[i];
            if (parent >= i) {
                throw new IllegalArgumentException("FK bind must be parent-first");
            }

            this.relative[i] = parent < 0 ? new Matrix4f(rest[i]) : new Matrix4f(this.inverse[parent]).mul(rest[i]);
        }

        this.controlOf = new int[n];
        Arrays.fill(this.controlOf, -1);
        int c = 0;

        while (c < this.controls.length) {
            this.controlOf[this.controls[c]] = c++;
        }
    }

    public int boneCount() {
        return this.bones.length;
    }

    public int boneIndex(String name) {
        for (int i = 0; i < this.bones.length; i++) {
            if (this.bones[i].equals(name)) {
                return i;
            }
        }

        throw new IllegalArgumentException("Missing bone: " + name);
    }

    private static final class Bind {
        String[] bones;
        int[] parents;
        int[] controls;
        float[][] rest;
        float[][] scales;
    }
}
