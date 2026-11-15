package com.huaxiaosheng.spapacks.anim.signal;

public interface WorldProbe {
    WorldProbe NONE = new WorldProbe() {
        @Override
        public boolean slimeBelow() {
            return false;
        }

        @Override
        public int clearBelow(int max) {
            return max;
        }
    };

    boolean slimeBelow();

    int clearBelow(int var1);
}
