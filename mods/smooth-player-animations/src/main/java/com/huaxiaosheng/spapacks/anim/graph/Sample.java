package com.huaxiaosheng.spapacks.anim.graph;

import java.util.Locale;

public final class Sample {
    public String clip;
    public double time;
    public boolean phased;
    public boolean mirror;
    public float weight;

    Sample set(String clip, double time, boolean phased, boolean mirror, float weight) {
        this.clip = clip;
        this.time = time;
        this.phased = phased;
        this.mirror = mirror;
        this.weight = weight;
        return this;
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%s@%.3f%s%s×%.2f", this.clip, this.time, this.phased ? "c" : "s", this.mirror ? "(m)" : "", this.weight);
    }
}
