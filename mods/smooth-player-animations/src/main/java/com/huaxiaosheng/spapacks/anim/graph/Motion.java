package com.huaxiaosheng.spapacks.anim.graph;

abstract class Motion {
    final int id;
    private static final Motion[] NONE = new Motion[0];

    Motion(int id) {
        this.id = id;
    }

    int stateSize() {
        return 0;
    }

    abstract void enter(Eval var1);

    abstract void update(Eval var1);

    abstract void rate(Eval var1, float var2, double[] var3);

    abstract void gather(Eval var1, float var2);

    abstract double onceSeconds(Eval var1);

    Motion[] children() {
        return NONE;
    }

    boolean available(Eval e) {
        for (Motion m : this.children()) {
            if (m != null && m.available(e)) {
                return true;
            }
        }

        return false;
    }

    Motion target() {
        return this;
    }
}
