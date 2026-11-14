package com.huaxiaosheng.spapacks.anim.signal;

import com.huaxiaosheng.spapacks.util.MathUtil;

final class LanternSwing {
    private static final float RESTORING_FORCE = 0.035F;
    private static final float DAMPING = 0.08F;
    private static final float ACCEL_GAIN = 0.35F;
    private static final float SPEED_GAIN = 0.008F;
    private static final float VERTICAL_GAIN = 0.1F;
    private static final float TURN_GAIN = 0.006F;
    private static final float MAX_DRIVE = 0.09F;
    private static final float MAX_ANGULAR_SPEED = 0.12F;
    private static final float MAX_ANGLE = (float)Math.toRadians(50.0);
    private static final double MAX_STEP_SQ = 16.0;
    private final float[] pitch = new float[2];
    private final float[] roll = new float[2];
    private boolean started;
    private double prevX;
    private double prevY;
    private double prevZ;
    private double prevVx;
    private double prevVy;
    private double prevVz;
    private float prevYaw;
    float pitchPrev;
    float pitchNow;
    float rollPrev;
    float rollNow;

    void tick(double x, double y, double z, float bodyYaw) {
        if (!this.started) {
            this.start(x, y, z, bodyYaw);
        } else {
            double vx = x - this.prevX;
            double vy = y - this.prevY;
            double vz = z - this.prevZ;
            if (vx * vx + vy * vy + vz * vz > 16.0) {
                this.start(x, y, z, bodyYaw);
            } else {
                double ax = vx - this.prevVx;
                double ay = vy - this.prevVy;
                double az = vz - this.prevVz;
                double yaw = Math.toRadians(bodyYaw);
                double sin = Math.sin(yaw);
                double cos = Math.cos(yaw);
                double forwardSpeed = vx * -sin + vz * cos;
                double leftSpeed = vx * cos + vz * sin;
                double forwardAccel = ax * -sin + az * cos;
                double leftAccel = ax * cos + az * sin;
                float yawStep = MathUtil.wrapDegrees(bodyYaw - this.prevYaw);
                float pitchDrive = (float)(forwardAccel * 0.35F + forwardSpeed * 0.008F - ay * 0.1F);
                float rollDrive = (float)(-leftAccel * 0.35F - leftSpeed * 0.008F) - yawStep * 0.006F;
                this.pitchPrev = this.pitchNow;
                this.rollPrev = this.rollNow;
                this.pitchNow = swing(this.pitch, MathUtil.clamp(pitchDrive, -0.09F, 0.09F));
                this.rollNow = swing(this.roll, MathUtil.clamp(rollDrive, -0.09F, 0.09F));
                this.prevX = x;
                this.prevY = y;
                this.prevZ = z;
                this.prevVx = vx;
                this.prevVy = vy;
                this.prevVz = vz;
                this.prevYaw = bodyYaw;
            }
        }
    }

    void reset() {
        this.started = false;
        this.pitch[0] = this.pitch[1] = this.roll[0] = this.roll[1] = 0.0F;
        this.pitchPrev = this.pitchNow = this.rollPrev = this.rollNow = 0.0F;
    }

    private void start(double x, double y, double z, float yaw) {
        this.reset();
        this.started = true;
        this.prevX = x;
        this.prevY = y;
        this.prevZ = z;
        this.prevVx = this.prevVy = this.prevVz = 0.0;
        this.prevYaw = yaw;
    }

    private static float swing(float[] p, float drive) {
        float v = p[1] + drive - 0.035F * (float)Math.sin(p[0]) - 0.08F * p[1];
        v = MathUtil.clamp(v, -0.12F, 0.12F);
        float a = p[0] + v;
        if (a > MAX_ANGLE) {
            a = MAX_ANGLE;
            v = Math.min(v, 0.0F);
        } else if (a < -MAX_ANGLE) {
            a = -MAX_ANGLE;
            v = Math.max(v, 0.0F);
        }

        p[0] = a;
        p[1] = v;
        return a;
    }
}
