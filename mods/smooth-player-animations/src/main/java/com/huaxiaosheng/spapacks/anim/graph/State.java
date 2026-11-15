package com.huaxiaosheng.spapacks.anim.graph;

final class State {
    final String id;
    final int priority;
    final Expr when;
    final Motion motion;
    final double transition;
    final boolean untilEnd;
    final double endLead;
    final String group;
    final double debounce;
    final String[] backends;
    final Expr cem;

    State(
        String id,
        int priority,
        Expr when,
        Motion motion,
        double transition,
        boolean untilEnd,
        double endLead,
        String group,
        double debounce,
        String[] backends,
        Expr cem
    ) {
        this.backends = backends;
        this.cem = cem;
        this.id = id;
        this.priority = priority;
        this.when = when;
        this.motion = motion;
        this.transition = transition;
        this.untilEnd = untilEnd;
        this.endLead = endLead;
        this.group = group;
        this.debounce = debounce;
    }
}
