package com.huaxiaosheng.spapacks.anim.graph;

import com.huaxiaosheng.spapacks.anim.signal.ExternalVars;
import com.huaxiaosheng.spapacks.anim.signal.Signal;
import com.huaxiaosheng.spapacks.anim.signal.Signals;
import com.huaxiaosheng.spapacks.util.MathUtil;
import java.util.Arrays;
import java.util.function.BiPredicate;

public final class GraphInstance {
    private static final double CHAIN_WINDOW = 1.0;
    private static final double REARM_AFTER = 0.075;
    private static final double LOWER_SMOOTH = 0.1;
    private final AnimGraph graph;
    private final float[] slots;
    private final Eval e = new Eval();
    private final Plan plan = new Plan();
    private final double[] acc = new double[2];
    private final double[] lean = new double[6];
    private final double[] fly = new double[6];
    private boolean leanStarted;
    private int env = -1;
    private double envTime;
    private State state;
    private double stateTime;
    private double phase;
    private State pending;
    private double pendingTime;
    private final boolean[][] consumed;
    private final GraphInstance.SlotRun[] runs;
    private final double[][] sinceTrigger;
    private BiPredicate<String, String> skip;

    public GraphInstance(AnimGraph graph) {
        this.graph = graph;
        this.slots = new float[graph.slotCount];
        System.arraycopy(graph.variables, 0, this.slots, Signal.COUNT, graph.variables.length);
        this.e.slots = this.slots;
        this.e.plan = this.plan;
        this.e.nodes = new double[graph.nodeCount][];

        for (int i = 0; i < graph.nodeCount; i++) {
            this.e.nodes[i] = new double[graph.nodeSizes[i]];
        }

        this.consumed = new boolean[graph.environments.length][];

        for (int i = 0; i < graph.environments.length; i++) {
            this.consumed[i] = new boolean[graph.environments[i].states.length];
        }

        this.runs = new GraphInstance.SlotRun[graph.slots.length];
        this.sinceTrigger = new double[graph.slots.length][];

        for (int i = 0; i < this.runs.length; i++) {
            this.runs[i] = new GraphInstance.SlotRun();
            this.sinceTrigger[i] = new double[graph.actions[i].length];
            Arrays.fill(this.sinceTrigger[i], Double.POSITIVE_INFINITY);
        }
    }

    public AnimGraph graph() {
        return this.graph;
    }

    public void setSkip(BiPredicate<String, String> skip) {
        this.skip = skip;
    }

    private boolean skipped(String environment, String state) {
        return this.skip != null && this.skip.test(environment, state);
    }

    public void setFacing(double radians) {
        this.e.facing = radians;
    }

    public Plan update(Signals signals, ClipInfo info) {
        signals.copyTo(this.slots);
        ExternalVars external = signals.external();

        for (int i = 0; i < this.graph.externalSlots.length; i++) {
            this.slots[this.graph.externalSlots[i]] = external == null ? 0.0F : external.get(this.graph.externalNames[i]);
        }

        double dt = this.slots[Signal.DT.ordinal()];
        this.slots[Signal.STATE_TIME.ordinal()] = (float)this.stateTime;
        this.slots[Signal.ENV_TIME.ordinal()] = (float)this.envTime;
        this.plan.clear();
        this.e.dt = dt;
        this.e.time = this.slots[Signal.TIME.ordinal()];
        this.e.info = info;
        this.e.signals = signals;
        this.e.transition = 0.0;
        this.chooseEnvironment();
        this.chooseState(dt);
        Environment env = this.graph.environments[this.env];
        this.plan.environment = env.id;
        this.plan.cem = (this.state != null && this.state.cem != null ? this.state.cem : env.cem).test(this.slots);
        this.plan.state = this.state == null ? null : this.state.id;
        this.plan.motion = this.state == null ? -1 : this.state.motion.target().id;
        if (this.state != null) {
            this.e.phase = this.phase;
            this.e.stateTime = this.stateTime;
            this.state.motion.update(this.e);
            this.acc[0] = 0.0;
            this.acc[1] = 0.0;
            this.state.motion.rate(this.e, 1.0F, this.acc);
            this.state.motion.gather(this.e, 1.0F);
            if (this.acc[1] > 1.0E-6) {
                this.phase = this.phase + dt * this.acc[0] / this.acc[1];
            }

            normalize(this.plan);
        }

        this.plan.transition = Math.max(this.plan.transition, this.e.transition);

        for (int i = 0; i < this.runs.length; i++) {
            this.runSlot(i, signals, info, dt);
        }

        this.procedural(dt);
        this.plan.lookYaw = this.slots[Signal.LOOK_YAW.ordinal()];
        this.plan.lookPitch = this.slots[Signal.LOOK_PITCH.ordinal()];
        this.plan.lookKeep = 1.0F;
        float takeover = 0.0F;

        for (Plan.Layer l : this.plan.layers) {
            if (l.exclusive) {
                takeover = Math.max(takeover, l.weight);
            }
        }

        if (takeover > 0.0F) {
            float keep = 1.0F - Math.min(1.0F, takeover);
            this.plan.lookKeep = keep;
            this.plan.lookYaw *= keep;
            this.plan.lookPitch *= keep;
            this.plan.leanForward *= keep;
            this.plan.leanBank *= keep;
            this.plan.flyForward *= keep;
            this.plan.flyBank *= keep;

            for (int s = 0; s < 2; s++) {
                this.plan.flyArmBack[s] = this.plan.flyArmBack[s] * keep;
                this.plan.flyArmLeft[s] = this.plan.flyArmLeft[s] * keep;
                this.plan.flyLegBack[s] = this.plan.flyLegBack[s] * keep;
                this.plan.flyLegLeft[s] = this.plan.flyLegLeft[s] * keep;
            }
        }

        this.stateTime += dt;
        this.envTime += dt;
        return this.plan;
    }

    private void procedural(double dt) {
        Procedural p = this.graph.procedural;
        float speed = this.slots[Signal.SPEED.ordinal()];
        if (!this.leanStarted) {
            this.lean[0] = speed;
            this.leanStarted = true;
        }

        Eval.spring(this.lean, 0, speed, p.leanHalflife, dt);
        boolean on = p.leanWhen.test(this.slots);
        double forward = on ? MathUtil.clamp(this.lean[1] * p.leanForward, -p.leanMaxForward, p.leanMaxForward) : 0.0;
        double bank = on ? MathUtil.clamp(speed * Math.toRadians(this.slots[Signal.TURN.ordinal()]) * p.leanBank, -p.leanMaxBank, p.leanMaxBank) : 0.0;
        Eval.spring(this.lean, 2, forward, p.leanHalflife, dt);
        Eval.spring(this.lean, 4, bank, p.leanHalflife, dt);
        this.plan.leanForward = (float)this.lean[2];
        this.plan.leanBank = (float)this.lean[4];
        this.flight(p.flight, dt);
        boolean lantern = p.lanternWhen.test(this.slots);
        this.plan.lanternRight = lantern && this.slots[Signal.LANTERN_RIGHT.ordinal()] != 0.0F;
        this.plan.lanternLeft = lantern && this.slots[Signal.LANTERN_LEFT.ordinal()] != 0.0F;
        this.plan.lanternPitch = this.slots[Signal.LANTERN_PITCH.ordinal()];
        this.plan.lanternRoll = this.slots[Signal.LANTERN_ROLL.ordinal()];
    }

    private void flight(Procedural.Flight f, double dt) {
        boolean on = f.when.test(this.slots);
        double ref = Math.max(0.1, f.reference);
        double forward = on ? MathUtil.clamp(this.slots[Signal.FORWARD.ordinal()] / ref, -1.5, 1.5) : 0.0;
        double side = on ? MathUtil.clamp(this.slots[Signal.SIDE.ordinal()] / ref, -1.5, 1.5) : 0.0;
        double turn = on ? MathUtil.clamp(this.slots[Signal.TURN.ordinal()] / Math.max(1.0, f.maxTurn), -1.0, 1.0) : 0.0;
        Eval.spring(this.fly, 0, forward, f.halflife, dt);
        Eval.spring(this.fly, 2, side, f.halflife, dt);
        Eval.spring(this.fly, 4, turn, f.halflife, dt);
        double fwd = this.fly[0];
        double sd = this.fly[2];
        double tn = this.fly[4];
        double brake = MathUtil.clamp(-this.fly[1] * Math.signum(fwd) / 2.0, 0.0, 1.0) * Math.min(1.0, Math.abs(fwd) * 4.0);
        Plan plan = this.plan;
        plan.flyForward = (float)(f.lean * fwd - f.brake * brake);
        plan.flyBank = (float)(f.bank * sd + f.turnBank * tn);

        for (int s = 0; s < 2; s++) {
            double toRight = s == 0 ? 1.0 : -1.0;
            plan.flyArmBack[s] = (float)(f.armsTrail * fwd - f.armsBrake * brake + f.armsTurnSwing * tn * toRight);
            plan.flyArmLeft[s] = (float)(f.armsTurnOut * Math.abs(tn) * toRight + f.armsSide * sd);
            plan.flyLegBack[s] = (float)(f.legsTrail * fwd);
            plan.flyLegLeft[s] = (float)(f.legsSide * sd + f.legsTurn * tn);
        }
    }

    private void chooseEnvironment() {
        Environment[] envs = this.graph.environments;
        int best = this.graph.defaultEnvironment;
        int bestPriority = Integer.MIN_VALUE;
        String backend = this.e.info.backendId();

        for (int i = 0; i < envs.length; i++) {
            if (envs[i].priority > bestPriority
                && Environment.playsOn(envs[i].backends, backend)
                && envs[i].when.test(this.slots)
                && !this.skipped(envs[i].id, null)) {
                best = i;
                bestPriority = envs[i].priority;
            }
        }

        if (best != this.env) {
            boolean first = this.env < 0;
            this.env = best;
            this.envTime = 0.0;
            this.slots[Signal.ENV_TIME.ordinal()] = 0.0F;
            Arrays.fill(this.consumed[best], false);
            State s = this.eligible(this.graph.environments[best]);
            boolean same = sameMotion(s, this.state);
            this.enter(s, !first && !same ? this.graph.environments[best].transition : 0.0, same);
        }
    }

    private State eligible(Environment env) {
        boolean[] used = this.consumed[this.env];

        for (int i = 0; i < env.states.length; i++) {
            State s = env.states[i];
            boolean holds = Environment.playsOn(s.backends, this.e.info.backendId())
                && s.when.test(this.slots)
                && s.motion.available(this.e)
                && !this.skipped(env.id, s.id);
            if (used[i] && !holds) {
                used[i] = false;
            }

            if (holds && !used[i]) {
                return s;
            }
        }

        return env.states.length == 0 ? null : env.states[env.states.length - 1];
    }

    private void chooseState(double dt) {
        Environment env = this.graph.environments[this.env];
        if (this.state != null && this.state.untilEnd) {
            this.e.stateTime = this.stateTime;
            double end = this.state.motion.onceSeconds(this.e);
            if (this.stateTime >= end - this.state.endLead) {
                this.consumed[this.env][this.indexOf(env, this.state)] = true;
            }
        }

        State best = this.eligible(env);
        if (best == this.state) {
            this.pending = null;
        } else {
            boolean playedOut = this.state != null && this.consumed[this.env][this.indexOf(env, this.state)];
            if (best != null && this.state != null && best.group != null && best.group.equals(this.state.group) && !playedOut) {
                if (this.pending != best) {
                    this.pending = best;
                    this.pendingTime = 0.0;
                }

                this.pendingTime += dt;
                if (this.pendingTime < best.debounce) {
                    return;
                }
            }

            double transition = best != null && !Double.isNaN(best.transition) ? best.transition : env.transition;
            boolean same = sameMotion(best, this.state);
            boolean keepPhase = same || best != null && this.state != null && best.group != null && best.group.equals(this.state.group);
            this.enter(best, same ? 0.0 : transition, keepPhase);
        }
    }

    private static boolean sameMotion(State a, State b) {
        return a != null && b != null && a != b && a.motion.target() == b.motion.target();
    }

    private void enter(State s, double transition, boolean keepPhase) {
        boolean continues = sameMotion(s, this.state);
        this.state = s;
        this.pending = null;
        if (!continues) {
            this.stateTime = 0.0;
            this.slots[Signal.STATE_TIME.ordinal()] = 0.0F;
        }

        if (!keepPhase) {
            this.phase = 0.0;
        }

        if (s != null && !continues) {
            this.e.stateTime = 0.0;
            this.e.phase = this.phase;
            s.motion.enter(this.e);
        }

        this.plan.transition = Math.max(this.plan.transition, transition);
    }

    private int indexOf(Environment env, State s) {
        for (int i = 0; i < env.states.length; i++) {
            if (env.states[i] == s) {
                return i;
            }
        }

        return 0;
    }

    private static void normalize(Plan plan) {
        float sum = 0.0F;

        for (Sample s : plan.base) {
            sum += s.weight;
        }

        if (sum > 1.0E-6F) {
            for (Sample s : plan.base) {
                s.weight /= sum;
            }
        }
    }

    private double length(Action a, String clip, ClipInfo info) {
        return a.duration > 0.0 ? a.duration : info.seconds(clip) / Math.max(0.001F, a.speed.eval(this.slots));
    }

    private void runSlot(int slot, Signals signals, ClipInfo info, double dt) {
        GraphInstance.SlotRun run = this.runs[slot];
        GraphInstance.Playing now = run.now;
        GraphInstance.Playing fading = run.fading;
        Action[] actions = this.graph.actions[slot];
        double[] since = this.sinceTrigger[slot];
        Action best = null;
        String bestClip = null;
        boolean restart = false;
        int bestStep = 0;

        for (int i = 0; i < actions.length; i++) {
            Action a = actions[i];
            boolean fired = a.trigger != null && a.trigger.test(this.slots);
            boolean chained = a.chain && a == now.action;
            boolean advance = false;
            if (a.chain && fired) {
                advance = chained && !run.held && now.clip != null && since[i] >= this.length(a, now.clip, info) - a.blendOut;
                fired = !chained && !run.held;
            }

            if (a.chain) {
                run.held = a.trigger != null && a.trigger.test(this.slots);
            }

            since[i] = fired ? 0.0 : since[i] + dt;
            if (a.yield.test(this.slots)) {
                since[i] = Double.POSITIVE_INFINITY;
            } else if (best == null && a.playsOn(info.backendId())) {
                int step = !a.chain ? 0 : (chained ? run.chainStep : (run.chainAction == a && this.e.time - run.chainEnd < 1.0 ? run.chainStep + 1 : 0));
                String clip = a.chain
                    ? info.resolve(a.choices[Math.floorMod(step, a.choices.length)])
                    : (a == now.action && !fired && a.pick != null ? now.clip : info.resolve(a.wanted(this.slots, signals)));
                if (clip != null) {
                    double length = this.length(a, clip, info);
                    boolean active = a.when != null && a.when.test(this.slots)
                        || a.trigger != null && (a.chain && !chained ? fired : since[i] < length - a.blendOut);
                    if (chained && !active && a.rearm != null && a.rearm.test(this.slots)) {
                        active = true;
                    }

                    if (advance) {
                        clip = info.resolve(a.choices[Math.floorMod(++step, a.choices.length)]);
                        since[i] = 0.0;
                        active = clip != null;
                        fired = true;
                    }

                    if (active) {
                        best = a;
                        bestClip = clip;
                        restart = fired;
                        bestStep = step;
                    }
                }
            }
        }

        if (now.action != null && now.action.chain && (best != now.action || restart)) {
            run.chainAction = now.action;
            run.chainEnd = this.e.time;
        }

        if (best == null) {
            this.retire(run, info);
        } else if (best != now.action || !bestClip.equals(now.clip) || restart) {
            boolean handoff = best.chain && (best == now.action || now.action == null && fading.action == best);
            float carried = best == now.action && !handoff ? now.weight : 0.0F;
            this.retire(run, info);
            run.handoff = handoff && fading.action != null;
            now.action = best;
            now.clip = bestClip;
            now.time = 0.0;
            now.mirror = best.mirror.test(this.slots);
            now.weight = carried;
            now.lower = carried > 0.0F ? now.lower : MathUtil.clamp01(best.lower.eval(this.slots));
            now.strength = Math.max(0.0F, best.strength.eval(this.slots));
            now.take = 0;
            now.armed = false;
            now.until = Double.NaN;
            if (best.chain) {
                run.chainAction = best;
                run.chainStep = bestStep;
            }
        }

        if (fading.action != null) {
            if (!run.handoff || now.action == null || now.weight >= 1.0F) {
                fading.weight = fading.weight - (float)(dt / Math.max(0.001, fading.action.fadeOut));
            }

            fading.time = fading.time + dt * fading.action.speed.eval(this.slots);
            if (fading.weight <= 0.0F) {
                fading.action = null;
            } else {
                this.layer(fading);
            }
        }

        if (now.action != null) {
            now.weight = Math.min(1.0F, now.weight + (float)(dt / Math.max(0.001, now.action.fadeIn)));
            float lower = MathUtil.clamp01(now.action.lower.eval(this.slots));
            now.lower = now.lower + (lower - now.lower) * (float)(1.0 - Math.exp(-dt / 0.1));
            now.until = this.rearmedTake(now, info);
            this.layer(now);
            now.time = now.time + dt * now.action.speed.eval(this.slots);
        }
    }

    private void retire(GraphInstance.SlotRun run, ClipInfo info) {
        GraphInstance.Playing now = run.now;
        GraphInstance.Playing fading = run.fading;
        double held = now.until;
        if (now.action != null && now.weight > 0.0F) {
            fading.takeOver(now);
            fading.until = !Double.isNaN(held) ? held : until(fading.action, fading.clip, fading.time, info);
        }

        now.action = null;
        now.clip = null;
        now.until = Double.NaN;
    }

    private double rearmedTake(GraphInstance.Playing p, ClipInfo info) {
        Action a = p.action;
        if (a.rearm != null && a.loop && !info.loops(p.clip) && !info.holds(p.clip)) {
            double length = info.seconds(p.clip);
            if (length <= 0.0) {
                return Double.NaN;
            }

            double end = (p.take + 1) * length;
            if (p.time > 0.075 && a.rearm.test(this.slots)) {
                if (p.time >= end) {
                    p.time = 0.0;
                    p.take = 0;
                    end = length;
                } else {
                    p.armed = true;
                }
            }

            if (p.time >= end && p.armed) {
                p.take++;
                p.armed = false;
                end += length;
            }

            return end;
        } else {
            return Double.NaN;
        }
    }

    private static double until(Action a, String clip, double time, ClipInfo info) {
        if (a != null && a.loop && clip != null && !info.loops(clip) && !info.holds(clip)) {
            double length = info.seconds(clip);
            return length > 0.0 ? Math.ceil(time / length - 1.0E-6) * length : Double.NaN;
        } else {
            return Double.NaN;
        }
    }

    private void layer(GraphInstance.Playing p) {
        Action a = p.action;
        Plan.Layer l = this.plan.newLayer();
        double length = this.e.info.seconds(p.clip);
        double t = !a.loop ? Math.min(p.time, length) : (p.time >= p.until ? length : (length > 0.0 && !this.e.info.holds(p.clip) ? p.time % length : p.time));
        l.sample.set(p.clip, t, false, p.mirror, 1.0F);
        l.action = a.id;
        l.mask = a.mask;
        l.override = a.override;
        l.additive = a.additive;
        l.aim = Math.max(0.0F, Math.min(1.0F, a.aim.eval(this.slots)));
        l.lower = p.lower;
        l.exclusive = a.exclusive;
        l.weight = MathUtil.smoothstep01(p.weight) * p.strength;
    }

    private static final class Playing {
        Action action;
        String clip;
        double time;
        boolean mirror;
        float weight;
        float lower;
        float strength = 1.0F;
        double until = Double.NaN;
        int take;
        boolean armed;

        void takeOver(GraphInstance.Playing from) {
            this.action = from.action;
            this.clip = from.clip;
            this.time = from.time;
            this.mirror = from.mirror;
            this.weight = from.weight;
            this.lower = from.lower;
            this.strength = from.strength;
        }
    }

    private static final class SlotRun {
        final GraphInstance.Playing now = new GraphInstance.Playing();
        final GraphInstance.Playing fading = new GraphInstance.Playing();
        boolean handoff;
        int chainStep;
        boolean held;
        Action chainAction;
        double chainEnd = Double.NEGATIVE_INFINITY;
    }
}
