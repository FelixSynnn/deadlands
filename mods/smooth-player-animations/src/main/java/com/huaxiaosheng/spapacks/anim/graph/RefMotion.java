package com.huaxiaosheng.spapacks.anim.graph;

final class RefMotion extends Motion {
    final String ref;
    Motion target;

    RefMotion(int id, String ref) {
        super(id);
        this.ref = ref;
    }

    @Override
    Motion target() {
        return this.target.target();
    }

    @Override
    void enter(Eval e) {
        this.target.enter(e);
    }

    @Override
    void update(Eval e) {
        this.target.update(e);
    }

    @Override
    void rate(Eval e, float weight, double[] acc) {
        this.target.rate(e, weight, acc);
    }

    @Override
    void gather(Eval e, float weight) {
        this.target.gather(e, weight);
    }

    @Override
    double onceSeconds(Eval e) {
        return this.target.onceSeconds(e);
    }

    @Override
    boolean available(Eval e) {
        return this.target.available(e);
    }
}
