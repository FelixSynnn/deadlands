package com.huaxiaosheng.spapacks.anim.graph;

abstract class WeightedMotion extends Motion {
    final Motion[] motions;

    WeightedMotion(int id, Motion[] motions) {
        super(id);
        this.motions = motions;
    }

    abstract float childWeight(Eval var1, float var2, int var3);

    void enterChildren(Eval e) {
        for (Motion m : this.motions) {
            if (m != null) {
                m.enter(e);
            }
        }
    }

    void updateChildren(Eval e) {
        for (Motion m : this.motions) {
            if (m != null) {
                m.update(e);
            }
        }
    }

    @Override
    final void rate(Eval e, float weight, double[] acc) {
        for (int i = 0; i < this.motions.length; i++) {
            if (this.motions[i] != null) {
                this.motions[i].rate(e, this.childWeight(e, weight, i), acc);
            }
        }
    }

    @Override
    final void gather(Eval e, float weight) {
        for (int i = 0; i < this.motions.length; i++) {
            if (this.motions[i] != null) {
                this.motions[i].gather(e, this.childWeight(e, weight, i));
            }
        }
    }

    @Override
    final Motion[] children() {
        return this.motions;
    }
}
