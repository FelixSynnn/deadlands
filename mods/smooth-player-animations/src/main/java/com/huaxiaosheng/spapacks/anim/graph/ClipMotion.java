package com.huaxiaosheng.spapacks.anim.graph;

import com.huaxiaosheng.spapacks.anim.signal.ClipSignal;
import com.huaxiaosheng.spapacks.anim.signal.Signal;

final class ClipMotion extends Motion {
    final String name;
    final ClipSignal signal;
    final ClipMotion.Time time;
    final double perBlock;
    final double perSecond;
    final double stride;
    final Expr phase;
    final Expr speed;
    final Expr mirror;

    ClipMotion(
        int id, String name, ClipSignal signal, ClipMotion.Time time, double perBlock, double perSecond, double stride, Expr phase, Expr speed, Expr mirror
    ) {
        super(id);
        this.signal = signal;
        this.stride = stride;
        this.name = name;
        this.time = time;
        this.perBlock = perBlock;
        this.perSecond = perSecond;
        this.phase = phase;
        this.speed = speed;
        this.mirror = mirror;
    }

    private String clip(Eval e) {
        return e.info.resolve(this.signal == null ? this.name : (e.signals == null ? null : e.signals.clip(this.signal)));
    }

    @Override
    void enter(Eval e) {
    }

    @Override
    void update(Eval e) {
    }

    @Override
    void rate(Eval e, float weight, double[] acc) {
        if (this.time == ClipMotion.Time.SYNC && !(weight <= 0.0F)) {
            float speed = this.speed.eval(e.slots);
            double rate;
            if (this.perBlock > 0.0) {
                rate = this.perBlock * e.slots[Signal.SPEED.ordinal()];
            } else if (this.perSecond > 0.0) {
                rate = this.perSecond * speed;
            } else {
                String clip = this.clip(e);
                double seconds = clip == null ? 0.0 : e.info.seconds(clip);
                if (seconds <= 0.0) {
                    return;
                }

                rate = speed / seconds;
                double stride = e.info.stride(clip);
                if (!(stride > 0.0)) {
                    stride = this.stride;
                }

                if (stride > 0.0) {
                    rate *= e.slots[Signal.SPEED.ordinal()] / stride;
                }
            }

            acc[0] += weight * rate;
            acc[1] += weight;
        }
    }

    @Override
    void gather(Eval e, float weight) {
        if (!(weight <= 1.0E-4F)) {
            String clip = this.clip(e);
            if (clip != null) {
                boolean mirror = this.mirror.test(e.slots);
                Sample s = e.plan.newSample();
                switch (this.time) {
                    case SYNC:
                        s.set(clip, e.phase, true, mirror, weight);
                        break;
                    case FREE:
                        s.set(clip, e.stateTime * this.speed.eval(e.slots), false, mirror, weight);
                        break;
                    case ONCE:
                        s.set(clip, Math.min(e.stateTime * this.speed.eval(e.slots), e.info.seconds(clip)), false, mirror, weight);
                        break;
                    case PHASE:
                        s.set(clip, this.phase.eval(e.slots), true, mirror, weight);
                }

                e.plan.base.add(s);
            }
        }
    }

    @Override
    boolean available(Eval e) {
        return this.clip(e) != null;
    }

    @Override
    double onceSeconds(Eval e) {
        if (this.time != ClipMotion.Time.ONCE) {
            return 0.0;
        }

        String clip = this.clip(e);
        float speed = this.speed.eval(e.slots);
        return clip != null && !(speed <= 0.0F) ? e.info.seconds(clip) / speed : 0.0;
    }

    enum Time {
        SYNC,
        FREE,
        ONCE,
        PHASE;
    }
}
