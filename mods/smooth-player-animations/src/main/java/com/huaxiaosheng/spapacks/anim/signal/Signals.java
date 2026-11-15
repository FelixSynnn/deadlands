package com.huaxiaosheng.spapacks.anim.signal;

import java.util.Arrays;

public final class Signals {
    private final float[] values = new float[Signal.COUNT];
    private final String[] clips = new String[ClipSignal.COUNT];
    private ExternalVars external;

    public float get(Signal s) {
        return this.values[s.ordinal()];
    }

    public boolean is(Signal s) {
        return this.values[s.ordinal()] != 0.0F;
    }

    public void set(Signal s, float value) {
        this.values[s.ordinal()] = value;
    }

    public void set(Signal s, double value) {
        this.values[s.ordinal()] = (float)value;
    }

    public void set(Signal s, boolean value) {
        this.values[s.ordinal()] = value ? 1.0F : 0.0F;
    }

    public String clip(ClipSignal c) {
        return this.clips[c.ordinal()];
    }

    public void clip(ClipSignal c, String name) {
        this.clips[c.ordinal()] = name;
    }

    public ExternalVars external() {
        return this.external;
    }

    public void external(ExternalVars external) {
        this.external = external;
    }

    public void copyTo(float[] slots) {
        System.arraycopy(this.values, 0, slots, 0, Signal.COUNT);
    }

    public void clear() {
        Arrays.fill(this.values, 0.0F);
        Arrays.fill(this.clips, null);
    }
}
