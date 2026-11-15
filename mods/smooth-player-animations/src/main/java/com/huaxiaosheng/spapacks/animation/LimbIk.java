package com.huaxiaosheng.spapacks.animation;

import com.huaxiaosheng.spapacks.anim.pose.Poses;
import com.huaxiaosheng.spapacks.rig.LiveRig;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class LimbIk {
    private final LiveRig rig;
    private final float upperLength;
    private final float lowerLength;
    private final Vector3f root = new Vector3f();
    private final Vector3f joint = new Vector3f();
    private final Vector3f end = new Vector3f();
    private final Vector3f along = new Vector3f();
    private final Vector3f bend = new Vector3f();
    private final Vector3f want = new Vector3f();
    private final Vector3f now = new Vector3f();
    private final Vector3f origin = new Vector3f();
    private final Matrix4f matrix = new Matrix4f();
    private final Quaternionf frame = new Quaternionf();
    private final Quaternionf turn = new Quaternionf();
    private final Quaternionf local = new Quaternionf();
    private final Quaternionf inverse = new Quaternionf();
    private final Matrix3f localAxes = new Matrix3f();
    private final Matrix3f rigAxes = new Matrix3f();

    LimbIk(LiveRig rig, float upperLength, float lowerLength) {
        this.rig = rig;
        this.upperLength = upperLength;
        this.lowerLength = lowerLength;
    }

    Vector3f end(float[][] pose, int lower, Vector3f out) {
        return this.rig.controlPoint(pose, lower, this.lowerLength, out);
    }

    void reach(float[][] pose, int upper, int lower, Vector3f target, boolean keep, Vector3f hint) {
        this.rig.controlMatrix(pose, upper, this.matrix).getTranslation(this.root);
        this.rig.controlMatrix(pose, lower, this.matrix).getTranslation(this.joint);
        this.end(pose, lower, this.end);
        if (!(this.end.distanceSquared(target) < 1.0E-8F)) {
            this.along.set(target).sub(this.root);
            float d = this.along.length();
            if (!(d < 1.0E-5F)) {
                this.along.div(d);
                float most = this.upperLength + this.lowerLength - 1.0E-4F;
                float least = Math.abs(this.upperLength - this.lowerLength) + 1.0E-4F;
                d = Math.max(least, Math.min(most, d));
                if (keep) {
                    this.bend.set(this.joint).sub(this.root);
                    this.bend.fma(-this.bend.dot(this.along), this.along);
                } else {
                    this.bend.zero();
                }

                if (this.bend.lengthSquared() < 1.0E-8F) {
                    this.bend.set(hint);
                    this.bend.fma(-this.bend.dot(this.along), this.along);
                }

                if (!(this.bend.lengthSquared() < 1.0E-8F)) {
                    this.bend.normalize();
                    float u = this.upperLength;
                    float a = (u * u - this.lowerLength * this.lowerLength + d * d) / (2.0F * d);
                    float h = (float)Math.sqrt(Math.max(0.0F, u * u - a * a));
                    this.want.set(this.root).fma(a, this.along).fma(h, this.bend).sub(this.root);
                    this.now.set(this.joint).sub(this.root);
                    this.turn(pose, upper, this.now, this.want);
                    this.rig.controlMatrix(pose, lower, this.matrix).getTranslation(this.joint);
                    this.end(pose, lower, this.end);
                    this.now.set(this.end).sub(this.joint);
                    this.want.set(target).sub(this.joint);
                    this.turn(pose, lower, this.now, this.want);
                }
            }
        }
    }

    void hinge(float[][] pose, int upper, int lower, Vector3f target, float sign, Vector3f hint) {
        this.rig.controlMatrix(pose, upper, this.matrix).getTranslation(this.root);
        this.along.set(target).sub(this.root);
        float u = this.upperLength;
        float l = this.lowerLength;
        float d = Math.max(Math.abs(u - l) + 1.0E-4F, Math.min(u + l - 1.0E-4F, this.along.length()));
        if (!(this.along.lengthSquared() < 1.0E-10F)) {
            this.along.normalize();
            float bendAngle = (float)Math.acos(Math.max(-1.0F, Math.min(1.0F, (d * d - u * u - l * l) / (2.0F * u * l))));
            this.end.set(0.0F, u + l * (float)Math.cos(bendAngle), sign * l * (float)Math.sin(bendAngle)).normalize();
            this.joint.set(0.0F, u, 0.0F);
            this.joint.fma(-this.joint.dot(this.end), this.end);
            if (this.joint.lengthSquared() < 1.0E-10F) {
                this.joint.set(0.0F, 0.0F, -sign);
            }

            this.joint.normalize();
            this.bend.set(hint);
            this.bend.fma(-this.bend.dot(this.along), this.along);
            if (!(this.bend.lengthSquared() < 1.0E-8F)) {
                this.bend.normalize();
                this.localAxes.set(this.end, this.joint, this.end.cross(this.joint, this.now));
                this.rigAxes.set(this.along, this.bend, this.along.cross(this.bend, this.want));
                this.rigAxes.mul(this.localAxes.transpose()).getNormalizedRotation(this.turn);
                this.rig.controlFrame(pose, upper, this.frame, this.origin);
                Poses.setRotation(pose[upper], this.inverse.set(this.frame).conjugate().mul(this.turn, this.local).normalize());
                this.turn.rotateX(sign * bendAngle);
                this.rig.controlFrame(pose, lower, this.frame, this.origin);
                Poses.setRotation(pose[lower], this.inverse.set(this.frame).conjugate().mul(this.turn, this.local).normalize());
            }
        }
    }

    void turn(float[][] pose, int control, Vector3f from, Vector3f to) {
        if (!(from.lengthSquared() < 1.0E-10F) && !(to.lengthSquared() < 1.0E-10F)) {
            this.turn.rotationTo(from.normalize(), to.normalize());
            this.rig.controlFrame(pose, control, this.frame, this.origin);
            float[] v = pose[control];
            Poses.rotation(v, this.local);
            this.turn.premul(this.inverse.set(this.frame).conjugate()).mul(this.frame).mul(this.local).normalize();
            Poses.setRotation(v, this.turn);
        }
    }
}
