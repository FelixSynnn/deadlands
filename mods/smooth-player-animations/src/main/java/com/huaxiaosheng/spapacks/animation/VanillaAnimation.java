package com.huaxiaosheng.spapacks.animation;

import com.huaxiaosheng.spapacks.anim.graph.AnimGraph;
import com.huaxiaosheng.spapacks.anim.graph.ClipInfo;
import com.huaxiaosheng.spapacks.anim.graph.Plan;
import com.huaxiaosheng.spapacks.anim.play.PlanPlayer;
import com.huaxiaosheng.spapacks.anim.play.VanillaBackend;
import com.huaxiaosheng.spapacks.anim.pose.Poses;
import com.huaxiaosheng.spapacks.animation.cem.CemAnimation;
import com.huaxiaosheng.spapacks.animation.cem.CemInput;
import com.huaxiaosheng.spapacks.animation.cem.CemPose;
import com.huaxiaosheng.spapacks.animation.external.ExternalPose;
import com.huaxiaosheng.spapacks.util.FrameClock;
import com.huaxiaosheng.spapacks.util.MathUtil;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class VanillaAnimation {
    private static final double FADE = 0.15;
    private static final double MAX_STEP = 0.25;
    private final VanillaBackend backend = new VanillaBackend();
    private PlanPlayer player;
    private CemPose cem;
    private final float[][] pack = new float[6][9];
    private final float[][] incoming = new float[6][9];
    private final float[] euler = new float[3];
    private final float[][] own = new float[6][6];
    private final Matrix3f matrix = new Matrix3f();
    private final Quaternionf q = new Quaternionf();
    private final Vector3f angles = new Vector3f();
    private final FrameClock clock = new FrameClock();
    private float exclusive;
    private final float[] vanillaParts = new float[6];
    private float packRootWeight;
    private final float[] packRootScratch = new float[9];
    private final float[][] ours = new float[6][9];
    private final float[] attachedValue = new float[6];
    private final Quaternionf bodyVanilla = new Quaternionf();
    private final Quaternionf bodyDrawn = new Quaternionf();
    private final Quaternionf bodyOurs = new Quaternionf();
    private final Quaternionf bodyFinal = new Quaternionf();
    private final Quaternionf partTurn = new Quaternionf();
    private final Vector3f offset = new Vector3f();
    private final Quaternionf mixFrom = new Quaternionf();
    private final Quaternionf mixTo = new Quaternionf();
    private final float[] turned = new float[3];
    private static final float[] IDENTITY = Poses.create(1, true)[0];
    private static final float CHAIN_Y = 0.1829407F;
    private static final float CHAIN_Z = 0.0996111F;
    private static final float REST_PITCH = (float)Math.toRadians(70.0);
    private static final float REST_CHAIN_Y = 0.15617323F;
    private static final float REST_CHAIN_Z = -0.13783903F;

    public ClipInfo clips(AnimGraph graph) {
        if (this.player == null || this.player.graph() != graph) {
            this.player = new PlanPlayer(this.backend, graph);
        }

        return this.player;
    }

    public void sample(
        double time,
        Plan plan,
        AnimGraph graph,
        CemAnimation animation,
        CemInput input,
        float[][] vanilla,
        float[][] defaults,
        ExternalPose external,
        float[][] parts,
        float[] root,
        float[][] items
    ) {
        this.sample(time, plan, graph, animation, input, vanilla, defaults, external, parts, root, items, this.packRootScratch);
    }

    public void sample(
        double time,
        Plan plan,
        AnimGraph graph,
        CemAnimation animation,
        CemInput input,
        float[][] vanilla,
        float[][] defaults,
        ExternalPose external,
        float[][] parts,
        float[] root,
        float[][] items,
        float[] packRoot
    ) {
        double step = this.clock.step(time, 0.25);

        for (int p = 0; p < 6; p++) {
            System.arraycopy(parts[p], 0, this.incoming[p], 0, 9);
        }

        float[][] own = this.own;

        for (int p = 0; p < 6; p++) {
            float[] from = vanilla != null ? vanilla[p] : null;
            float[] in = parts[p];
            float[] o = own[p];
            o[0] = from != null ? from[0] : in[3];
            o[1] = from != null ? from[1] : in[4];
            o[2] = from != null ? from[2] : in[5];
            o[3] = MathUtil.wrapRadians(from != null ? from[3] : in[0]);
            o[4] = MathUtil.wrapRadians(from != null ? from[4] : in[1]);
            o[5] = MathUtil.wrapRadians(from != null ? from[5] : in[2]);
        }

        this.backend.vanilla(own, defaults);
        if (animation != null) {
            if (this.cem == null || !this.cem.plays(animation)) {
                this.cem = new CemPose(animation);
            }

            for (int p = 0; p < 6; p++) {
                float[] o = this.pack[p];
                o[0] = own[p][3];
                o[1] = own[p][4];
                o[2] = own[p][5];
                o[3] = own[p][0];
                o[4] = own[p][1];
                o[5] = own[p][2];
                o[6] = o[7] = o[8] = 1.0F;
            }

            this.cem.sample(input, time, this.pack, defaults);
            this.backend.cem(this.pack, defaults);
        } else {
            this.cem = null;
        }

        boolean cemBase = animation != null && (plan == null || plan.cem || plan.baseOff);
        this.backend.cemBase(cemBase);
        float[][] pose;
        if (plan != null && graph != null) {
            this.clips(graph);
            pose = this.player.play(plan, step, cemBase || plan.cem);
            this.procedural(plan, pose);
        } else {
            pose = VanillaBackend.SKELETON.newPose();
            this.backend.rest(pose);
        }

        for (int p = 0; p < 6; p++) {
            float[] b = pose[VanillaBackend.PART_BONE[p]];
            float[] o = parts[p];
            this.euler(b, this.euler);
            o[0] = this.euler[0];
            o[1] = this.euler[1];
            o[2] = this.euler[2];
            o[3] = defaults[p][0] + b[0];
            o[4] = defaults[p][1] - b[1];
            o[5] = defaults[p][2] + b[2];
            o[6] = b[7];
            o[7] = b[8];
            o[8] = b[9];
        }

        System.arraycopy(pose[0], 0, root, 0, 10);
        System.arraycopy(pose[7], 0, items[0], 0, 10);
        System.arraycopy(pose[8], 0, items[1], 0, 10);
        boolean whole = external != null && external.exclusive;
        float rate = (float)(step / 0.15);
        this.exclusive = MathUtil.approach(this.exclusive, whole ? 1.0F : 0.0F, rate);
        if (this.exclusive > 0.0F) {
            Poses.blendBone(root, IDENTITY, this.exclusive, root);

            for (float[] item : items) {
                Poses.blendBone(item, IDENTITY, this.exclusive, item);
            }
        }

        this.packRootWeight = MathUtil.approach(this.packRootWeight, cemBase && this.cem != null ? 1.0F : 0.0F, rate);
        float shown = this.packRootWeight * (1.0F - this.exclusive);
        float[] from = this.cem != null ? this.cem.root() : null;

        for (int c = 0; c < 9; c++) {
            float rest = c >= 6 ? 1.0F : 0.0F;
            packRoot[c] = from == null ? rest : rest + (from[c] - rest) * shown;
        }

        for (int p = 0; p < 6; p++) {
            System.arraycopy(parts[p], 0, this.ours[p], 0, 9);
        }

        for (int p = 0; p < 6; p++) {
            float[] o = parts[p];
            float owned = external == null ? 0.0F : external.weight[p];
            float off = plan == null ? 0.0F : (cemBase ? plan.actionOff[p] : plan.vanillaPart(p));
            this.vanillaParts[p] = MathUtil.approach(this.vanillaParts[p], off, rate);
            float w = Math.max(Math.max(owned, this.exclusive), this.vanillaParts[p]);
            if (!(w <= 0.0F)) {
                float[] theirs = owned > 0.0F ? external.value[p] : null;
                if (theirs != null && p != 0) {
                    theirs = this.attached(p, external, parts[0], Math.max(this.exclusive, this.vanillaParts[p]));
                }

                float[] in = this.incoming[p];
                float rx = theirs != null ? theirs[3] : in[0];
                float ry = theirs != null ? theirs[4] : in[1];
                float rz = theirs != null ? theirs[5] : in[2];
                float tx = theirs != null ? theirs[0] : in[3];
                float ty = theirs != null ? theirs[1] : in[4];
                float tz = theirs != null ? theirs[2] : in[5];
                this.turnTo(o, rx, ry, rz, w);
                o[3] += (tx - o[3]) * w;
                o[4] += (ty - o[4]) * w;
                o[5] += (tz - o[5]) * w;
                o[6] += (1.0F - o[6]) * w;
                o[7] += (1.0F - o[7]) * w;
                o[8] += (1.0F - o[8]) * w;
            }
        }
    }

    private float[] attached(int p, ExternalPose external, float[] body, float drawn) {
        float[] v = external.value[p];
        float[] n = external.vanilla[p];
        float[] ours = this.ours[p];
        float[] ourBody = this.ours[0];
        float[] bv = external.value[0];
        float[] bn = external.vanilla[0];
        float bw = external.weight[0];
        boolean turns = p != 4 && p != 5;
        float[] out = this.attachedValue;
        if (turns) {
            this.bodyVanilla.rotationZYX(bn[5], bn[4], bn[3]);
            this.partTurn.rotationZYX(bv[5], bv[4], bv[3]);
            this.bodyVanilla.slerp(this.partTurn, bw, this.bodyDrawn);
            this.bodyOurs.rotationZYX(ourBody[2], ourBody[1], ourBody[0]);
            this.bodyFinal.rotationZYX(body[2], body[1], body[0]);
        } else {
            this.bodyDrawn.identity();
            this.bodyOurs.identity();
            this.bodyFinal.identity();
        }

        this.q.set(this.bodyOurs).conjugate().transform(this.offset.set(ours[3] - ourBody[3], ours[4] - ourBody[4], ours[5] - ourBody[5]));
        this.bodyFinal.transform(this.offset);
        out[0] = body[3] + this.offset.x + (v[0] - n[0]) - (bv[0] - bn[0]) * bw;
        out[1] = body[4] + this.offset.y + (v[1] - n[1]) - (bv[1] - bn[1]) * bw;
        out[2] = body[5] + this.offset.z + (v[2] - n[2]) - (bv[2] - bn[2]) * bw;
        this.q.set(this.bodyDrawn).conjugate().premul(this.bodyFinal).mul(this.partTurn.rotationZYX(v[5], v[4], v[3]));
        this.matrix.set(this.q.normalize()).getEulerAnglesZYX(this.angles);
        out[3] = this.angles.x;
        out[4] = this.angles.y;
        out[5] = this.angles.z;
        if (drawn > 0.0F) {
            for (int i = 0; i < 3; i++) {
                out[i] += (v[i] - out[i]) * drawn;
            }

            float[] turned = this.turned;
            turned[0] = out[3];
            turned[1] = out[4];
            turned[2] = out[5];
            this.turnTo(turned, v[3], v[4], v[5], drawn);
            out[3] = turned[0];
            out[4] = turned[1];
            out[5] = turned[2];
        }

        return out;
    }

    private void turnTo(float[] angles, float x, float y, float z, float t) {
        if (t >= 1.0F) {
            angles[0] = x;
            angles[1] = y;
            angles[2] = z;
        } else {
            this.mixFrom.rotationZYX(angles[2], angles[1], angles[0]).slerp(this.mixTo.rotationZYX(z, y, x), t);
            this.matrix.set(this.mixFrom.normalize()).getEulerAnglesZYX(this.angles);
            angles[0] = this.angles.x;
            angles[1] = this.angles.y;
            angles[2] = this.angles.z;
        }
    }

    private void procedural(Plan plan, float[][] pose) {
        if (plan.leanBank != 0.0F && !plan.cem) {
            this.backend.addAngles(pose[0], 0.0F, 0.0F, (float)Math.toRadians(plan.leanBank));
        }

        float pitch = (float)Math.toRadians(plan.lanternPitch);
        float roll = (float)Math.toRadians(plan.lanternRoll);
        if (plan.lanternRight) {
            this.swing(pose[7], pitch, roll);
        }

        if (plan.lanternLeft) {
            this.swing(pose[8], pitch, -roll);
        }
    }

    private void swing(float[] item, float pitch, float roll) {
        if (pitch != 0.0F || roll != 0.0F) {
            this.backend.addAngles(item, pitch, 0.0F, roll);
            float a = -(REST_PITCH + pitch);
            float cos = (float)Math.cos(a);
            float sin = (float)Math.sin(a);
            float y = cos * 0.1829407F - sin * 0.0996111F;
            float z = sin * 0.1829407F + cos * 0.0996111F;
            float rc = (float)Math.cos(-roll);
            float rs = (float)Math.sin(-roll);
            float movedX = rs * z;
            float movedZ = rc * z;
            float cx = -movedX;
            float cy = 0.15617323F - y;
            float cz = -0.13783903F - movedZ;
            item[0] -= 16.0F * cx;
            item[1] += 16.0F * cz;
            item[2] -= 16.0F * cy;
        }
    }

    private void euler(float[] b, float[] out) {
        Poses.eulerZYX(b, this.q, this.matrix, this.angles);
        out[0] = this.angles.x;
        out[1] = this.angles.y;
        out[2] = this.angles.z;
    }

    public void angles(float[] bone, float[] out) {
        this.euler(bone, out);
    }
}
