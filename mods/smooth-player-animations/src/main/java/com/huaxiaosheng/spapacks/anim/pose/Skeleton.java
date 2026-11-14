package com.huaxiaosheng.spapacks.anim.pose;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public final class Skeleton {
    private final String[] names;
    private final int[] mirror;
    private boolean scaled;
    private final Map<String, float[]> regions = new HashMap<>();
    private final Map<String, float[]> mirroredRegions = new HashMap<>();

    private Skeleton(String[] names, int[] mirror) {
        this.names = names;
        this.mirror = mirror;
    }

    public int bones() {
        return this.names.length;
    }

    public String name(int bone) {
        return this.names[bone];
    }

    public int index(String name) {
        for (int i = 0; i < this.names.length; i++) {
            if (this.names[i].equals(name)) {
                return i;
            }
        }

        return -1;
    }

    public boolean scaled() {
        return this.scaled;
    }

    public int mirror(int bone) {
        return this.mirror[bone];
    }

    public float[] region(String name) {
        return name == null ? null : this.regions.get(name);
    }

    public float[] region(String name, boolean mirrored) {
        return name == null ? null : (mirrored ? this.mirroredRegions : this.regions).get(name);
    }

    public float[][] newPose() {
        return Poses.create(this.names.length, this.scaled);
    }

    public static Skeleton.Builder builder(String... names) {
        return new Skeleton.Builder(names);
    }

    public static final class Builder {
        private final String[] names;
        private final int[] mirror;
        private final Map<String, float[]> regions = new HashMap<>();
        private boolean scaled;

        private Builder(String[] names) {
            this.names = (String[])names.clone();
            this.mirror = new int[names.length];
            int i = 0;

            while (i < names.length) {
                this.mirror[i] = i++;
            }
        }

        public Skeleton.Builder scaled() {
            this.scaled = true;
            return this;
        }

        public Skeleton.Builder pair(String a, String b) {
            int i = this.find(a);
            int j = this.find(b);
            this.mirror[i] = j;
            this.mirror[j] = i;
            return this;
        }

        public Skeleton.Builder region(String name, String... bones) {
            float[] w = new float[this.names.length];

            for (String spec : bones) {
                int colon = spec.indexOf(58);
                String bone = colon < 0 ? spec : spec.substring(0, colon);
                w[this.find(bone)] = colon < 0 ? 1.0F : Float.parseFloat(spec.substring(colon + 1));
            }

            this.regions.put(name, w);
            return this;
        }

        public Skeleton build() {
            Skeleton s = new Skeleton(this.names, this.mirror);
            s.scaled = this.scaled;
            s.regions.putAll(this.regions);
            float[] all = new float[this.names.length];
            Arrays.fill(all, 1.0F);
            s.regions.putIfAbsent("full", all);

            for (Entry<String, float[]> e : s.regions.entrySet()) {
                float[] w = e.getValue();
                float[] m = new float[w.length];

                for (int i = 0; i < w.length; i++) {
                    m[i] = w[this.mirror[i]];
                }

                s.mirroredRegions.put(e.getKey(), m);
            }

            return s;
        }

        private int find(String name) {
            for (int i = 0; i < this.names.length; i++) {
                if (this.names[i].equals(name)) {
                    return i;
                }
            }

            throw new IllegalArgumentException("No bone " + name);
        }
    }
}
