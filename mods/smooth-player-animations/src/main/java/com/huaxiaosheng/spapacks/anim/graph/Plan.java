package com.huaxiaosheng.spapacks.anim.graph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class Plan {
    public final List<Sample> base = new ArrayList<>();
    public double transition;
    public final List<Plan.Layer> layers = new ArrayList<>();
    public float lookYaw;
    public float lookPitch;
    public float lookKeep = 1.0F;
    public float leanForward;
    public float leanBank;
    public float flyForward;
    public float flyBank;
    public final float[] flyArmBack = new float[2];
    public final float[] flyArmLeft = new float[2];
    public final float[] flyLegBack = new float[2];
    public final float[] flyLegLeft = new float[2];
    public String environment;
    public String state;
    public int motion = -1;
    public boolean cem;
    public boolean lanternRight;
    public boolean lanternLeft;
    public float lanternPitch;
    public float lanternRoll;
    private final List<Sample> samplePool = new ArrayList<>();
    private final List<Plan.Layer> layerPool = new ArrayList<>();
    private int samplesUsed;
    private int layersUsed;
    public boolean baseOff;
    public final float[] actionOff = new float[6];
    public final boolean[] kept = new boolean[6];

    public float vanillaPart(int p) {
        return Math.max(this.actionOff[p], this.baseOff && !this.kept[p] ? 1.0F : 0.0F);
    }

    public void giveBaseToVanilla() {
        this.baseOff = true;
        this.base.clear();
        this.leanForward = 0.0F;
        this.leanBank = 0.0F;
        this.flyForward = 0.0F;
        this.flyBank = 0.0F;
        Arrays.fill(this.flyArmBack, 0.0F);
        Arrays.fill(this.flyArmLeft, 0.0F);
        Arrays.fill(this.flyLegBack, 0.0F);
        Arrays.fill(this.flyLegLeft, 0.0F);
    }

    public void clearToggles() {
        this.baseOff = false;
        Arrays.fill(this.actionOff, 0.0F);
        Arrays.fill(this.kept, false);
    }

    void clear() {
        this.base.clear();
        this.layers.clear();
        this.clearToggles();
        this.transition = 0.0;
        this.samplesUsed = 0;
        this.layersUsed = 0;
    }

    Sample newSample() {
        if (this.samplesUsed == this.samplePool.size()) {
            this.samplePool.add(new Sample());
        }

        return this.samplePool.get(this.samplesUsed++);
    }

    Plan.Layer newLayer() {
        if (this.layersUsed == this.layerPool.size()) {
            this.layerPool.add(new Plan.Layer());
        }

        Plan.Layer l = this.layerPool.get(this.layersUsed++);
        this.layers.add(l);
        return l;
    }

    @Override
    public String toString() {
        return this.environment + "/" + this.state + " " + this.base + (this.transition > 0.0 ? " ⟿" + this.transition : "") + " " + this.layers;
    }

    public static final class Layer {
        public final Sample sample = new Sample();
        public String action;
        public String mask;
        public String override;
        public boolean additive;
        public float aim;
        public float lower;
        public boolean exclusive;
        public float weight;

        @Override
        public String toString() {
            return this.action + "[" + this.sample + (this.additive ? " +" : " =") + this.mask + " ×" + String.format(Locale.ROOT, "%.2f", this.weight) + "]";
        }
    }
}
