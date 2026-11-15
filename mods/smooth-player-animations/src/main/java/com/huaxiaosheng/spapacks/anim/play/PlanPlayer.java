package com.huaxiaosheng.spapacks.anim.play;

import com.huaxiaosheng.spapacks.anim.graph.AnimGraph;
import com.huaxiaosheng.spapacks.anim.graph.ClipInfo;
import com.huaxiaosheng.spapacks.anim.graph.Plan;
import com.huaxiaosheng.spapacks.anim.graph.Sample;
import com.huaxiaosheng.spapacks.anim.pose.Inertializer;
import com.huaxiaosheng.spapacks.anim.pose.Poses;
import com.huaxiaosheng.spapacks.anim.pose.Skeleton;
import java.util.List;

public final class PlanPlayer implements ClipInfo {
    private final Backend backend;
    private final AnimGraph graph;
    private final Skeleton skeleton;
    private final Inertializer inertializer;
    private final float[][] base;
    private final float[][] tmp;
    private final float[][] shown;
    private final float[][] last;
    private final float[][] beforeLast;
    private final float[][] below;
    private final float[][] locomotion;
    private double hipTurn;
    private final float[][] snapshot;
    private int snapshotMotion = Integer.MIN_VALUE;
    private final float[] from = new float[7];
    private float[] mask;
    private final float[] to = new float[7];
    private double lastDt;
    private double transition;
    private boolean started;
    public static final String SNAPSHOT = "@snapshot";

    public PlanPlayer(Backend backend, AnimGraph graph) {
        this.backend = backend;
        this.graph = graph;
        this.skeleton = backend.skeleton();
        int n = this.skeleton.bones();
        this.inertializer = new Inertializer(n);
        this.base = this.skeleton.newPose();
        this.tmp = this.skeleton.newPose();
        this.shown = this.skeleton.newPose();
        this.last = this.skeleton.newPose();
        this.beforeLast = this.skeleton.newPose();
        this.below = this.skeleton.newPose();
        this.locomotion = this.skeleton.newPose();
        this.snapshot = this.skeleton.newPose();
    }

    private void sample(Sample s, float[][] out) {
        if ("@snapshot".equals(s.clip)) {
            Poses.copy(this.snapshot, out);
        } else {
            this.backend.sample(s.clip, s.time, s.phased, s.mirror, out);
        }
    }

    public Backend backend() {
        return this.backend;
    }

    @Override
    public String resolve(String name) {
        if (name == null) {
            return null;
        } else {
            return name.startsWith("@") ? name : this.graph.resolve(this.backend.id(), name, this.backend::has);
        }
    }

    @Override
    public double seconds(String resolved) {
        return this.backend.seconds(resolved);
    }

    @Override
    public boolean loops(String resolved) {
        return this.backend.loops(resolved);
    }

    @Override
    public boolean holds(String resolved) {
        return this.backend.holds(resolved);
    }

    @Override
    public String backendId() {
        return this.backend.id();
    }

    @Override
    public double stride(String resolved) {
        return this.backend.stride(resolved);
    }

    public AnimGraph graph() {
        return this.graph;
    }

    public float[][] play(Plan plan, double dt) {
        return this.play(plan, dt, false);
    }

    public float[][] play(Plan plan, double dt, boolean restIsBase) {
        if (plan.motion != this.snapshotMotion) {
            this.snapshotMotion = plan.motion;
            if (this.started) {
                Poses.copy(this.last, this.snapshot);
            } else {
                this.backend.rest(this.snapshot);
            }
        }

        this.backend.rest(this.base);
        float total = 0.0F;

        for (Sample s : restIsBase ? List.<Sample>of() : plan.base) {
            if (total <= 0.0F) {
                this.sample(s, this.base);
                total = s.weight;
            } else {
                this.backend.rest(this.tmp);
                this.sample(s, this.tmp);
                total += s.weight;
                Poses.blend(this.base, this.tmp, s.weight / total, this.base);
            }
        }

        if (plan.transition > 0.0 && this.started) {
            this.inertializer.begin(this.last, this.beforeLast, this.lastDt);
            this.transition = plan.transition;
        }

        this.inertializer.sample(dt, this.transition, this.base, this.shown);
        Poses.copy(this.last, this.beforeLast);
        Poses.copy(this.shown, this.last);
        this.lastDt = dt;
        this.started = true;
        Poses.copy(this.shown, this.locomotion);

        for (Plan.Layer l : plan.layers) {
            if (l.weight > 0.0F) {
                this.layer(l);
                if (l.aim > 0.0F) {
                    this.backend
                        .aim(this.shown, l.weight * l.aim, l.sample.mirror, this.skeleton.region(l.mask, l.sample.mirror), plan.lookYaw, plan.lookPitch);
                }
            }
        }

        this.hipTurn = plan.layers.isEmpty() ? 0.0 : this.backend.hipTurn(this.locomotion, this.shown);
        return this.shown;
    }

    public double hipTurn() {
        return this.hipTurn;
    }

    private void layer(Plan.Layer l) {
        Sample s = l.sample;
        float[] mask = this.skeleton.region(l.mask, s.mirror);
        if (mask != null) {
            float[] lower = l.lower > 0.0F ? this.skeleton.region("lower", false) : null;
            if (lower != null) {
                if (this.mask == null || this.mask.length != mask.length) {
                    this.mask = new float[mask.length];
                }

                for (int b = 0; b < mask.length; b++) {
                    this.mask[b] = Math.max(mask[b], lower[b] * l.lower);
                }

                mask = this.mask;
            }

            Poses.copy(this.shown, this.below);
            Poses.reset(this.tmp);
            this.backend.sample(s.clip, s.time, s.phased, s.mirror, this.tmp);
            if (!l.additive && !this.backend.additive(s.clip)) {
                float[] override = l.override == null ? null : this.skeleton.region(l.override, s.mirror);

                for (int b = 0; b < this.shown.length; b++) {
                    float w = l.weight * mask[b];
                    int keyed = w <= 0.0F ? 0 : this.backend.keyed(s.clip, b, s.mirror);
                    if (keyed != 0) {
                        float[] o = this.shown[b];
                        float[] d = this.tmp[b];
                        boolean overrides = override != null && override[b] > 0.0F;
                        if ((keyed & 1) != 0) {
                            boolean replace = overrides && this.backend.overrideTranslation();

                            for (int k = 0; k < 3; k++) {
                                o[k] += (replace ? d[k] - o[k] : d[k]) * w;
                            }
                        }

                        if ((keyed & 2) != 0) {
                            if (overrides) {
                                System.arraycopy(o, 0, this.from, 0, 7);
                                System.arraycopy(o, 0, this.to, 0, 3);
                                System.arraycopy(d, 3, this.to, 3, 4);
                                Poses.blendBone(this.from, this.to, w, this.from);
                                System.arraycopy(this.from, 3, o, 3, 4);
                            } else {
                                Poses.addRotation(o, d, w, o);
                            }
                        }

                        if ((keyed & 4) != 0 && o.length >= 10 && d.length >= 10) {
                            for (int k = 7; k < 10; k++) {
                                o[k] = overrides ? o[k] + (d[k] - o[k]) * w : o[k] + (d[k] - 1.0F) * w;
                            }
                        }
                    }
                }

                this.backend.followHips(this.shown, this.below, this.tmp, s.clip, s.mirror, l.weight, l.lower);
            } else {
                Poses.add(this.shown, this.tmp, l.weight, mask, this.shown);
            }
        }
    }
}
