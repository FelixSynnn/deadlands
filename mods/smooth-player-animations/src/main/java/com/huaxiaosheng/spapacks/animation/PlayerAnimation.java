package com.huaxiaosheng.spapacks.animation;

import com.huaxiaosheng.spapacks.anim.graph.AnimGraph;
import com.huaxiaosheng.spapacks.anim.graph.ClipInfo;
import com.huaxiaosheng.spapacks.anim.graph.Plan;
import com.huaxiaosheng.spapacks.anim.play.BentBackend;
import com.huaxiaosheng.spapacks.anim.play.PlanPlayer;
import com.huaxiaosheng.spapacks.animation.external.ExternalBlend;
import com.huaxiaosheng.spapacks.animation.external.ExternalPose;
import com.huaxiaosheng.spapacks.rig.LiveRig;
import com.huaxiaosheng.spapacks.rig.Skeleton;
import com.huaxiaosheng.spapacks.util.FrameClock;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class PlayerAnimation {
    private final BentBackend backend = new BentBackend();
    private PlanPlayer player;
    private final Look look = new Look();
    private final LiveRig chain = new LiveRig(Skeleton.CHAIN);
    private final LiveRig bind = new LiveRig(Skeleton.ALEX);
    private final ExternalBlend external = new ExternalBlend();
    private final float[] vanillaParts = new float[6];
    private final float[][] controls = Pose.create();
    private final float[][] measured = Pose.create();
    private static final double MAX_STEP = 0.25;
    private final FrameClock clock = new FrameClock();
    private final FootIk feet = new FootIk(this.chain);
    private boolean grounded;
    private float bodyYaw;
    private FootIk.Ground ground;
    private final HandReach reach = new HandReach(this.chain);
    private PlayerAnimation.Extra extra;

    public void extra(PlayerAnimation.Extra extra) {
        this.extra = extra;
    }

    public LiveRig chain() {
        return this.chain;
    }

    public float hipDrop() {
        return this.ground == null ? 0.0F : this.feet.hipDrop();
    }

    public HandReach reach() {
        return this.reach;
    }

    public void feet(boolean grounded, float bodyYaw, FootIk.Ground ground) {
        this.grounded = grounded;
        this.bodyYaw = bodyYaw;
        this.ground = ground;
    }

    public double hipTurn() {
        return this.player == null ? 0.0 : this.player.hipTurn();
    }

    public ClipInfo clips(AnimGraph graph) {
        if (this.player == null || this.player.graph() != graph) {
            this.player = new PlanPlayer(this.backend, graph);
        }

        return this.player;
    }

    public void sample(double time, Plan plan, AnimGraph graph, float[][] vanilla, ExternalPose external, float[][] palette, Quaternionf[] items) {
        this.sample(time, plan, graph, vanilla, external, palette, items, null);
    }

    public void sample(
        double time, Plan plan, AnimGraph graph, float[][] vanilla, ExternalPose external, float[][] palette, Quaternionf[] items, Vector3f[] offsets
    ) {
        double dt = this.clock.step(time, 0.25);
        items[0].identity();
        items[1].identity();
        if (offsets != null) {
            offsets[0].zero();
            offsets[1].zero();
        }

        if (plan != null && graph != null) {
            this.clips(graph);
            this.backend.vanilla(vanilla);
            float[][] pose = this.player.play(plan, dt);

            for (int c = 0; c < 13; c++) {
                System.arraycopy(pose[c], 0, this.controls[c], 0, 7);
            }

            float[] r = pose[13];
            float[] l = pose[14];
            items[0].set(r[3], r[4], r[5], r[6]).normalize();
            items[1].set(l[3], l[4], l[5], l[6]).normalize();
            if (offsets != null) {
                offsets[0].set(r[0], r[1], r[2]);
                offsets[1].set(l[0], l[1], l[2]);
            }

            this.look.lean(this.controls, plan.leanForward + plan.flyForward, plan.leanBank + plan.flyBank);
            this.look.limbs(this.controls, plan.flyArmBack, plan.flyArmLeft, plan.flyLegBack, plan.flyLegLeft);
            this.look.apply(this.controls, plan.lookYaw, plan.lookPitch, graph.procedural());
            if (this.ground != null) {
                this.feet.apply(this.controls, dt, this.grounded, this.bodyYaw, this.ground);
            }

            this.reach.apply(this.controls, dt, this.bodyYaw);
            if (this.extra != null) {
                this.extra.apply(this.controls, dt);
            }
        } else {
            Pose.reset(this.controls);
        }

        this.chain.rebasePose(this.controls, this.bind, this.measured);
        boolean restBase = plan != null && plan.baseOff;

        for (int p = 0; p < this.vanillaParts.length; p++) {
            this.vanillaParts[p] = plan == null ? 0.0F : plan.vanillaPart(p);
        }

        this.bind.solve(this.external.apply(time, external, this.measured, this.vanillaParts, restBase), palette);
    }

    public interface Extra {
        void apply(float[][] var1, double var2);
    }
}
