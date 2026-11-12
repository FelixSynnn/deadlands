package com.huaxiaosheng.spapacks.util;

public final class FrameClock {
    private double last = Double.NaN;

    public double step(double time, double max) {
        double step = Double.isNaN(this.last) ? 0.0 : Math.max(0.0, Math.min(max, time - this.last));
        this.last = time;
        return step;
    }

    public double stepOr(double time, double max, double fallback) {
        double step = Double.isNaN(this.last) ? fallback : time - this.last;
        if (step <= 0.0 || step > max) {
            step = fallback;
        }

        this.last = time;
        return step;
    }

    public boolean same(double time) {
        return time == this.last;
    }

    public double last() {
        return this.last;
    }
}
