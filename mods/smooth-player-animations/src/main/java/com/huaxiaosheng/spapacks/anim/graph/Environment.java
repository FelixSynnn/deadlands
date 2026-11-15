package com.huaxiaosheng.spapacks.anim.graph;

final class Environment {
    final String id;
    final int priority;
    final Expr when;
    final State[] states;
    final double transition;
    final Expr cem;
    final String[] backends;

    Environment(String id, int priority, Expr when, State[] states, double transition, Expr cem, String[] backends) {
        this.backends = backends;
        this.id = id;
        this.priority = priority;
        this.when = when;
        this.states = states;
        this.transition = transition;
        this.cem = cem;
    }

    static boolean playsOn(String[] backends, String backend) {
        if (backends.length == 0) {
            return true;
        }

        for (String b : backends) {
            if (b.equals(backend)) {
                return true;
            }
        }

        return false;
    }
}
