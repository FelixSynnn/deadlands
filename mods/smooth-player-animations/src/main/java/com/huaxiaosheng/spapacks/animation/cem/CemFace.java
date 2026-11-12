package com.huaxiaosheng.spapacks.animation.cem;

import com.huaxiaosheng.spapacks.util.FrameClock;

public final class CemFace {
    private static final double MAX_STEP = 0.25;
    private static final double DEFAULT_STEP = 0.05;
    private final CemAnimation animation;
    private final float[] v;
    private final CemPose.Builtins builtins;
    private final CemInput pinned = new CemInput();
    private final FrameClock clock = new FrameClock();
    private int frameCounter;

    public CemFace(CemAnimation animation) {
        this.animation = animation;
        this.v = new float[animation.slots()];
        this.builtins = new CemPose.Builtins(animation);
    }

    public boolean plays(CemAnimation animation) {
        return this.animation == animation;
    }

    public boolean sample(CemInput in, double time) {
        if (this.clock.same(time)) {
            return false;
        }

        double step = this.clock.stepOr(time, 0.25, 0.05);
        this.pinned.copyFrom(in);
        this.pinned.headPitch = 0.0F;
        this.pinned.distance = 0.0F;
        this.builtins.write(this.v, this.pinned, (float)step, ++this.frameCounter, in.yaw, this.animation.bakesLook());
        this.animation.eval(this.v);
        return true;
    }

    public float value(int slot, float rest) {
        return slot >= 0 ? this.v[slot] : rest;
    }
}
