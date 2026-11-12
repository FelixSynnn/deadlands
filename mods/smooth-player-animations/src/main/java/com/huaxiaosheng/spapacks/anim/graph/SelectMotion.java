package com.huaxiaosheng.spapacks.anim.graph;

final class SelectMotion extends Motion {
    final Expr[] when;
    final Motion[] motions;
    final boolean latch;
    final double transition;

    SelectMotion(int id, Expr[] when, Motion[] motions, boolean latch, double transition) {
        super(id);
        this.when = when;
        this.motions = motions;
        this.latch = latch;
        this.transition = transition;
    }

    @Override
    int stateSize() {
        return 1;
    }

    private int choose(Eval e) {
        for (int i = 0; i < this.motions.length; i++) {
            if (this.when[i] == null || this.when[i].test(e.slots)) {
                return i;
            }
        }

        return -1;
    }

    private Motion current(Eval e) {
        int i = (int)e.nodes[this.id][0];
        return i < 0 ? null : this.motions[i];
    }

    @Override
    void enter(Eval e) {
        int i = this.choose(e);
        e.nodes[this.id][0] = i;
        if (i >= 0) {
            this.motions[i].enter(e);
        }
    }

    @Override
    void update(Eval e) {
        if (!this.latch) {
            int i = this.choose(e);
            if (i != (int)e.nodes[this.id][0]) {
                e.nodes[this.id][0] = i;
                if (i >= 0) {
                    this.motions[i].enter(e);
                }

                e.requestTransition(this.transition);
            }
        }

        Motion m = this.current(e);
        if (m != null) {
            m.update(e);
        }
    }

    @Override
    void rate(Eval e, float weight, double[] acc) {
        Motion m = this.current(e);
        if (m != null) {
            m.rate(e, weight, acc);
        }
    }

    @Override
    void gather(Eval e, float weight) {
        Motion m = this.current(e);
        if (m != null) {
            m.gather(e, weight);
        }
    }

    @Override
    Motion[] children() {
        return this.motions;
    }

    @Override
    double onceSeconds(Eval e) {
        Motion m = this.current(e);
        return m == null ? 0.0 : m.onceSeconds(e);
    }
}
