package com.huaxiaosheng.spapacks.animation.cem;

import com.huaxiaosheng.spapacks.util.FrameClock;
import com.huaxiaosheng.spapacks.util.MathUtil;

public final class CemPose {
    public static final int PARTS = 6;
    public static final int VALUES = 9;
    private static final double MAX_STEP = 0.25;
    private static final double DEFAULT_STEP = 0.05;
    private final CemAnimation animation;
    private final float[] v;
    private final int[][] part = new int[CemAnimation.PARTS.length][];
    private final CemPose.Builtins builtins;
    private final float[][] last = new float[6][9];
    private final int[] rootSlots = new int[9];
    private final float[] root = new float[]{0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F};
    private final FrameClock clock = new FrameClock();
    private int frameCounter;
    private float yaw;
    private float lastRawYaw = Float.NaN;
    private float[][] baseline;

    public CemPose(CemAnimation animation) {
        this.animation = animation;

        for (int p = 0; p < CemAnimation.PARTS.length; p++) {
            this.part[p] = animation.channels(p);
        }

        this.builtins = new CemPose.Builtins(animation);
        this.v = new float[animation.slots()];

        for (int c = 0; c < 9; c++) {
            int slot = animation.find("root." + CemAnimation.CHANNELS[c]);
            this.rootSlots[c] = slot >= 0 && animation.assigns(slot) ? slot : -1;
        }
    }

    public float[] root() {
        return this.root;
    }

    private float[][] baseline() {
        if (this.baseline == null) {
            float[] r = new float[this.v.length];
            CemPose.Builtins b = this.builtins;

            for (int i = 0; i < 40; i++) {
                put(r, b.limbSwing, 0.0F);
                put(r, b.limbSpeed, 0.0F);
                put(r, b.ls, (float) (Math.PI / 2));
                put(r, b.age, i);
                put(r, b.frameTime, 0.05F);
                put(r, b.frameCounter, i);
                put(r, b.paused, 0.0F);
                put(r, b.onGround, 1.0F);
                this.animation.eval(r);
            }

            int n = 24;
            float[][] sum = new float[6][3];

            for (int s = 0; s < n; s++) {
                float ls = (float)((Math.PI * 2) * s / n);
                put(r, b.ls, ls);
                put(r, b.limbSwing, (float)((ls - (Math.PI / 2)) / 0.58));
                this.animation.eval(r);

                for (int p = 0; p < 6; p++) {
                    for (int c = 0; c < 3; c++) {
                        sum[p][c] = sum[p][c] + r[this.part[p][3 + c]] / n;
                    }
                }
            }

            this.baseline = sum;
        }

        return this.baseline;
    }

    private static void put(float[] v, int slot, float value) {
        if (slot >= 0) {
            v[slot] = value;
        }
    }

    public void sample(CemInput in, double time, float[][] parts, float[][] rest) {
        boolean again = this.clock.same(time);
        if (this.animation.offsets() && rest != null && !again) {
            float[][] base = this.baseline();
            this.sample(in, time, parts);

            for (int p = 0; p < 6; p++) {
                for (int c = 0; c < 3; c++) {
                    if (this.animation.assigns(this.part[p][3 + c])) {
                        parts[p][3 + c] = rest[p][c] + this.v[this.part[p][3 + c]] - base[p][c];
                    }
                }
            }

            copy(parts, this.last);
        } else {
            this.sample(in, time, parts);
        }
    }

    public boolean plays(CemAnimation animation) {
        return this.animation == animation;
    }

    public void sample(CemInput in, double time, float[][] parts) {
        if (this.clock.same(time)) {
            copy(this.last, parts);
        } else {
            double step = this.clock.stepOr(time, 0.25, 0.05);
            if (Float.isNaN(this.lastRawYaw)) {
                this.yaw = in.yaw;
            } else {
                this.yaw = this.yaw + MathUtil.wrapDegrees(in.yaw - this.lastRawYaw);
            }

            this.lastRawYaw = in.yaw;

            for (int p = 0; p < 6; p++) {
                int[] s = this.part[p];

                for (int c = 0; c < 9; c++) {
                    this.v[s[c]] = parts[p][c];
                }
            }

            this.builtins.write(this.v, in, (float)step, ++this.frameCounter, this.yaw, this.animation.bakesLook());
            this.animation.eval(this.v);

            for (int p = 0; p < 6; p++) {
                int[] s = this.part[p];

                for (int c = 0; c < 9; c++) {
                    float x = this.v[s[c]];
                    parts[p][c] = Float.isFinite(x) ? x : parts[p][c];
                }
            }

            for (int c = 0; c < 9; c++) {
                int slot = this.rootSlots[c];
                float x = slot < 0 ? Float.NaN : this.v[slot];
                this.root[c] = Float.isFinite(x) ? x : (c >= 6 ? 1.0F : 0.0F);
            }

            copy(parts, this.last);
        }
    }

    private static void copy(float[][] from, float[][] to) {
        for (int p = 0; p < 6; p++) {
            System.arraycopy(from[p], 0, to[p], 0, 9);
        }
    }

    static final class Builtins {
        final int limbSwing;
        final int limbSpeed;
        final int ls;
        final int age;
        final int frameTime;
        final int frameCounter;
        final int paused;
        final int onGround;
        final int inWater;
        final int inLava;
        final int sprinting;
        final int sneaking;
        final int swimming;
        final int crawling;
        final int climbing;
        final int usingItem;
        final int blocking;
        final int gliding;
        final int flying;
        final int flyingNbt;
        final int riding;
        final int hurt;
        final int hurtTime;
        final int inGui;
        final int id;
        final int distance;
        final int jumping;
        final int posY;
        final int falling;
        final int fallSpeed;
        final int fluidDepth;
        final int moveForward;
        final int moveStrafing;
        final int headYaw;
        final int headPitch;
        final int rotX;
        final int rotY;
        final int rootRx;
        final int rootRy;
        final int rootRz;
        final int rightHanded;
        final int holdingRight;
        final int holdingLeft;

        Builtins(CemAnimation s) {
            this.limbSwing = s.find("limb_swing");
            this.limbSpeed = s.find("limb_speed");
            this.ls = s.find("var.ls");
            this.age = s.find("age");
            this.frameTime = s.find("frame_time");
            this.frameCounter = s.find("frame_counter");
            this.paused = s.find("is_paused");
            this.onGround = s.find("is_on_ground");
            this.inWater = s.find("is_in_water");
            this.inLava = s.find("is_in_lava");
            this.sprinting = s.find("is_sprinting");
            this.sneaking = s.find("is_sneaking");
            this.swimming = s.find("is_swimming");
            this.crawling = s.find("is_crawling");
            this.climbing = s.find("is_climbing");
            this.usingItem = s.find("is_using_item");
            this.blocking = s.find("is_blocking");
            this.gliding = s.find("is_gliding");
            this.flying = s.find("is_flying");
            this.flyingNbt = s.find("nbt:abilities.flying");
            this.riding = s.find("is_riding");
            this.hurt = s.find("is_hurt");
            this.hurtTime = s.find("hurt_time");
            this.inGui = s.find("is_in_gui");
            this.id = s.find("id");
            this.distance = s.find("distance");
            this.jumping = s.find("is_jumping");
            this.posY = s.find("pos_y");
            this.falling = s.find("is_falling");
            this.fallSpeed = s.find("vs_ltd");
            this.fluidDepth = s.find("fluid_depth_up");
            this.moveForward = s.find("move_forward");
            this.moveStrafing = s.find("move_strafing");
            this.headYaw = s.find("head_yaw");
            this.headPitch = s.find("head_pitch");
            this.rotX = s.find("rot_x");
            this.rotY = s.find("rot_y");
            this.rootRx = s.find("root.rx");
            this.rootRy = s.find("root.ry");
            this.rootRz = s.find("root.rz");
            this.rightHanded = s.find("is_right_handed");
            this.holdingRight = s.find("is_holding_item_right");
            this.holdingLeft = s.find("is_holding_item_left");
        }

        void write(float[] v, CemInput in, float frameTime, int counter, float continuousYaw, boolean bakesLook) {
            put(v, this.limbSwing, in.limbSwing);
            put(v, this.limbSpeed, Math.min(1.0F, in.limbSpeed));
            put(v, this.ls, in.limbSwing * 0.58F + (float) (Math.PI / 2));
            put(v, this.age, in.age);
            put(v, this.frameTime, frameTime);
            put(v, this.frameCounter, counter);
            put(v, this.paused, in.paused);
            put(v, this.onGround, in.onGround);
            put(v, this.inWater, in.inWater);
            put(v, this.inLava, in.inLava);
            put(v, this.sprinting, in.sprinting);
            put(v, this.sneaking, in.sneaking);
            put(v, this.swimming, in.swimming);
            put(v, this.crawling, in.crawling);
            put(v, this.climbing, in.climbing);
            put(v, this.usingItem, in.usingItem);
            put(v, this.blocking, in.blocking);
            put(v, this.gliding, in.gliding);
            put(v, this.flying, in.flying);
            put(v, this.flyingNbt, in.flying);
            put(v, this.riding, in.riding);
            put(v, this.hurt, in.hurtTime > 0.0F);
            put(v, this.hurtTime, in.hurtTime);
            put(v, this.inGui, in.inGui);
            put(v, this.id, in.id);
            put(v, this.distance, in.distance);
            put(v, this.jumping, in.jumping);
            put(v, this.posY, in.posY);
            put(v, this.falling, !in.onGround && in.velocityY < 0.0F);
            put(v, this.fallSpeed, Math.max(0.0F, -in.velocityY));
            put(v, this.fluidDepth, in.fluidDepth);
            put(v, this.moveForward, in.moveForward);
            put(v, this.moveStrafing, in.moveStrafing);
            float headYaw = in.inGui ? 0.0F : in.headYaw;
            float headPitch = in.inGui ? 0.0F : in.headPitch;
            put(v, this.headYaw, headYaw);
            put(v, this.headPitch, headPitch);
            put(v, this.rotX, (float)Math.toRadians(headPitch));
            put(v, this.rotY, (float)Math.toRadians(continuousYaw));
            put(v, this.rootRx, bakesLook ? 0.0F : (float)(-Math.toRadians(headPitch)));
            put(v, this.rootRy, bakesLook ? 0.0F : (float)Math.toRadians(headYaw));
            put(v, this.rootRz, 0.0F);
            put(v, this.rightHanded, in.rightHanded);
            put(v, this.holdingRight, in.holdingRight);
            put(v, this.holdingLeft, in.holdingLeft);
        }

        private static void put(float[] v, int slot, float value) {
            if (slot >= 0) {
                v[slot] = value;
            }
        }

        private static void put(float[] v, int slot, boolean value) {
            if (slot >= 0) {
                v[slot] = value ? 1.0F : 0.0F;
            }
        }
    }
}
