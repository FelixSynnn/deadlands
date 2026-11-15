package com.huaxiaosheng.spapacks.anim.graph;

final class DirectionMotion extends WeightedMotion {
    final Expr x;
    final Expr y;
    final double rate;

    DirectionMotion(int id, Expr x, Expr y, double rate, Motion[] motions) {
        super(id, motions);
        this.x = x;
        this.y = y;
        this.rate = rate;
    }

    @Override
    int stateSize() {
        return 4;
    }

    private boolean target(Eval e, double[] want) {
        double side = this.x.eval(e.slots);
        double forward = this.y.eval(e.slots);
        if (e.facing != 0.0) {
            double c = Math.cos(e.facing);
            double s = Math.sin(e.facing);
            double f = forward * c - side * s;
            side = side * c + forward * s;
            forward = f;
        }

        double total = Math.abs(side) + Math.abs(forward);
        if (total < 0.3) {
            return false;
        }

        want[0] = Math.max(0.0, forward) / total;
        want[1] = Math.max(0.0, -forward) / total;
        want[2] = Math.max(0.0, side) / total;
        want[3] = Math.max(0.0, -side) / total;

        for (int i = 1; i < 4; i++) {
            if (this.motions[i] == null) {
                want[0] += want[i];
                want[i] = 0.0;
            }
        }

        return true;
    }

    @Override
    void enter(Eval e) {
        double[] s = e.nodes[this.id];
        if (!this.target(e, s)) {
            s[0] = 1.0;
            s[1] = s[2] = s[3] = 0.0;
        }

        this.enterChildren(e);
    }

    @Override
    void update(Eval e) {
        double[] s = e.nodes[this.id];
        double[] want = e.direction;
        if (this.target(e, want)) {
            double k = 1.0 - Math.exp(-this.rate * e.dt);

            for (int i = 0; i < 4; i++) {
                s[i] += (want[i] - s[i]) * k;
            }
        }

        this.updateChildren(e);
    }

    @Override
    float childWeight(Eval e, float weight, int i) {
        return (float)(weight * e.nodes[this.id][i]);
    }

    @Override
    double onceSeconds(Eval e) {
        return 0.0;
    }
}
