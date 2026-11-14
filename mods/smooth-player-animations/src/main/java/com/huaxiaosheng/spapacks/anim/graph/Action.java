package com.huaxiaosheng.spapacks.anim.graph;

import com.huaxiaosheng.spapacks.anim.signal.ClipSignal;
import com.huaxiaosheng.spapacks.anim.signal.Signals;

final class Action {
    final String id;
    final String slot;
    final int priority;
    final Expr when;
    final Expr trigger;
    final Expr yield;
    final String clip;
    final ClipSignal clipSignal;
    final Expr pick;
    final String[] choices;
    final String[] backends;
    final double blendOut;
    final String mask;
    final String override;
    final boolean additive;
    final Expr mirror;
    final boolean loop;
    final Expr speed;
    final double fadeIn;
    final double fadeOut;
    final double duration;
    final Expr aim;
    final Expr lower;
    final boolean chain;
    final Expr strength;
    final Expr rearm;
    final boolean exclusive;

    Action(
        String id,
        String slot,
        int priority,
        Expr when,
        Expr trigger,
        Expr yield,
        String clip,
        ClipSignal clipSignal,
        Expr pick,
        String[] choices,
        String[] backends,
        String mask,
        String override,
        boolean additive,
        Expr mirror,
        boolean loop,
        Expr speed,
        double fadeIn,
        double fadeOut,
        double blendOut,
        double duration,
        Expr aim,
        Expr lower,
        boolean chain,
        Expr strength,
        Expr rearm,
        boolean exclusive
    ) {
        this.rearm = rearm;
        this.exclusive = exclusive;
        this.lower = lower;
        this.chain = chain && choices != null && choices.length > 0;
        this.strength = strength;
        this.duration = duration;
        this.aim = aim;
        this.id = id;
        this.slot = slot;
        this.priority = priority;
        this.when = when;
        this.trigger = trigger;
        this.yield = yield;
        this.clip = clip;
        this.clipSignal = clipSignal;
        this.pick = pick;
        this.choices = choices;
        this.backends = backends;
        this.blendOut = blendOut;
        this.mask = mask;
        this.override = override;
        this.additive = additive;
        this.mirror = mirror;
        this.loop = loop;
        this.speed = speed;
        this.fadeIn = fadeIn;
        this.fadeOut = fadeOut;
    }

    boolean playsOn(String backend) {
        return Environment.playsOn(this.backends, backend);
    }

    String wanted(float[] slots, Signals signals) {
        if (this.clipSignal != null) {
            return signals.clip(this.clipSignal);
        } else if (this.pick != null) {
            int i = (int)Math.floor(this.pick.eval(slots));
            return this.choices[Math.floorMod(i, this.choices.length)];
        } else {
            return this.clip;
        }
    }
}
