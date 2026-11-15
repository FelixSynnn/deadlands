package com.huaxiaosheng.spapacks.anim.graph;

final class BlendMotion extends WeightedMotion {
    final Expr by;
    final double halflife;
    final double[] at;

    BlendMotion(int id, Expr by, double halflife, double[] at, Motion[] motions) {
        super(id, motions);
        this.by = by;
        this.halflife = halflife;
        this.at = at;
    }

    @Override
    int stateSize() {
        return 2;
    }

    @Override
    void enter(Eval e) {
        e.nodes[this.id][0] = this.by.eval(e.slots);
        e.nodes[this.id][1] = 0.0;
        this.enterChildren(e);
    }

    @Override
    void update(Eval e) {
        Eval.spring(e.nodes[this.id], 0, this.by.eval(e.slots), this.halflife, e.dt);
        this.updateChildren(e);
    }

    private float weight(Eval e, int i) {
        double x = e.nodes[this.id][0];
        int n = this.at.length;
        if (x <= this.at[0]) {
            return i == 0 ? 1.0F : 0.0F;
        }

        if (x >= this.at[n - 1]) {
            return i == n - 1 ? 1.0F : 0.0F;
        }

        for (int j = 0; j < n - 1; j++) {
            if (x <= this.at[j + 1]) {
                double t = (x - this.at[j]) / (this.at[j + 1] - this.at[j]);
                return i == j ? (float)(1.0 - t) : (i == j + 1 ? (float)t : 0.0F);
            }
        }

        return 0.0F;
    }

    @Override
    float childWeight(Eval e, float weight, int i) {
        return weight * this.weight(e, i);
    }

    @Override
    double onceSeconds(Eval e) {
        double s = 0.0;

        for (Motion m : this.motions) {
            s = Math.max(s, m.onceSeconds(e));
        }

        return s;
    }
}
