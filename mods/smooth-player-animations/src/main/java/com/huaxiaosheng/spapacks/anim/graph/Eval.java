package com.huaxiaosheng.spapacks.anim.graph;

import com.huaxiaosheng.spapacks.anim.signal.Signals;

final class Eval {
    float[] slots;
    double dt;
    double time;
    double phase;
    double stateTime;
    ClipInfo info;
    Plan plan;
    Signals signals;
    double[][] nodes;
    double transition;
    double facing;
    final double[] direction = new double[4];

    void requestTransition(double seconds) {
        this.transition = Math.max(this.transition, seconds);
    }

    static void spring(double[] s, int i, double target, double halflife, double dt) {
        if (halflife <= 0.0) {
            s[i] = target;
            s[i + 1] = 0.0;
        } else {
            double y = 2.77258872224 / (halflife + 1.0E-5) / 2.0;
            double j0 = s[i] - target;
            double j1 = s[i + 1] + j0 * y;
            double eydt = Math.exp(-y * dt);
            s[i] = eydt * (j0 + j1 * dt) + target;
            s[i + 1] = eydt * (s[i + 1] - j1 * y * dt);
        }
    }
}
