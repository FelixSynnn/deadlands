package com.huaxiaosheng.spapacks.rig;

import com.huaxiaosheng.spapacks.anim.pose.Poses;
import com.huaxiaosheng.spapacks.animation.Pose;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class LiveRig {
    private final Skeleton skeleton;
    private final Matrix4f[] world;
    private final float[][] q;
    private final float[][] p;
    private final Matrix4f basis = new Matrix4f();
    private final Matrix4f skin = new Matrix4f();
    private final Matrix4f sourceControl = new Matrix4f();
    private final Matrix4f targetParent = new Matrix4f();
    private final Quaternionf quat = new Quaternionf();
    private final Quaternionf controlRotation = new Quaternionf();
    private final Vector3f controlPosition = new Vector3f();
    private final Matrix4f[] rebaseWorld;
    private final Matrix4f rebaseLocal = new Matrix4f();
    private final Matrix4f toModel = new Matrix4f()
        .set(-0.9375F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.9375F, 0.0F, 0.0F, 0.9375F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F);
    private final Matrix4f fromModel = new Matrix4f(this.toModel).invert();

    public LiveRig() {
        this(Skeleton.ALEX);
    }

    public LiveRig(Skeleton skeleton) {
        this.skeleton = skeleton;
        int n = skeleton.boneCount();
        this.world = new Matrix4f[n];

        for (int i = 0; i < n; i++) {
            this.world[i] = new Matrix4f();
        }

        this.q = new float[n][4];
        this.p = new float[n][3];
        this.rebaseWorld = new Matrix4f[skeleton.controls.length];

        for (int i = 0; i < this.rebaseWorld.length; i++) {
            this.rebaseWorld[i] = new Matrix4f();
        }
    }

    public void preserveControlRotation(float[][] original, float[][] target, int control, float amount) {
        this.preserveControlRotation(original, target, control, amount, 0.0);
    }

    public void preserveControlRotation(float[][] original, float[][] target, int control, float amount, double facingYaw) {
        if (!(amount <= 0.0F)) {
            int bone = this.skeleton.controls[control];
            this.controlWorld(original, bone, this.sourceControl);
            this.sourceControl.rotateLocal(this.quat.rotationZ((float)(-facingYaw)));
            this.parentFrame(target, bone, this.targetParent).invert().mul(this.sourceControl);
            this.targetParent.getUnnormalizedRotation(this.quat).normalize();
            float[] v = target[control];
            Poses.rotation(v, this.controlRotation).slerp(this.quat, amount).normalize();
            Poses.setRotation(v, this.controlRotation);
        }
    }

    public void preserveControlHeight(float[][] original, float[][] target, int control) {
        int bone = this.skeleton.controls[control];
        this.controlWorld(original, bone, this.sourceControl);
        this.controlWorld(target, bone, this.targetParent);
        this.controlPosition.set(this.targetParent.m30(), this.targetParent.m31(), this.sourceControl.m32());
        this.parentFrame(target, bone, this.targetParent).invert();
        this.targetParent.transformPosition(this.controlPosition);
        target[control][0] = this.controlPosition.x;
        target[control][1] = this.controlPosition.y;
        target[control][2] = this.controlPosition.z;
    }

    public Matrix4f controlMatrix(float[][] pose, int control, Matrix4f out) {
        this.controlWorld(pose, this.skeleton.controls[control], out);
        return out;
    }

    public void placeControl(float[][] pose, int control, Vector3f point) {
        int bone = this.skeleton.controls[control];
        this.parentFrame(pose, bone, this.targetParent).invert();
        this.targetParent.transformPosition(this.controlPosition.set(point));
        pose[control][0] = this.controlPosition.x;
        pose[control][1] = this.controlPosition.y;
        pose[control][2] = this.controlPosition.z;
    }

    public void rebasePose(float[][] pose, LiveRig target, float[][] output) {
        Skeleton to = target.skeleton;
        if (this.skeleton.controls.length != to.controls.length) {
            throw new IllegalArgumentException("Control count mismatch");
        }

        for (int i = 0; i < this.skeleton.controls.length; i++) {
            int bone = this.skeleton.controls[i];
            if (bone != to.controls[i]) {
                throw new IllegalArgumentException("Control order mismatch");
            }

            if (this.skeleton.parents[bone] != to.parents[bone]) {
                this.controlWorld(pose, bone, this.rebaseWorld[i]);
            }
        }

        if (pose != output) {
            Pose.copy(pose, output);
        }

        for (int bonex = 0; bonex < this.skeleton.bones.length; bonex++) {
            if (this.skeleton.parents[bonex] != to.parents[bonex]) {
                int control = this.skeleton.controlOf[bonex];
                if (control < 0) {
                    throw new IllegalArgumentException("Reparenting a driver is unsupported");
                }

                target.parentFrame(output, bonex, this.rebaseLocal).invert().mul(this.rebaseWorld[control]);
                this.rebaseLocal.getTranslation(this.controlPosition);
                this.rebaseLocal.getUnnormalizedRotation(this.controlRotation).normalize();
                float[] v = output[control];
                v[0] = this.controlPosition.x;
                v[1] = this.controlPosition.y;
                v[2] = this.controlPosition.z;
                Poses.setRotation(v, this.controlRotation);
            }
        }
    }

    public double controlHeading(float[][] pose, int control) {
        this.controlWorld(pose, this.skeleton.controls[control], this.sourceControl);
        return Math.hypot(this.sourceControl.m00(), this.sourceControl.m01()) < 1.0E-4 ? 0.0 : -Math.atan2(this.sourceControl.m01(), this.sourceControl.m00());
    }

    public void controlFrame(float[][] pose, int control, Quaternionf rotation, Vector3f origin) {
        int bone = this.skeleton.controls[control];
        this.parentFrame(pose, bone, this.sourceControl);
        this.sourceControl.getUnnormalizedRotation(rotation).normalize();
        this.sourceControl.getTranslation(origin);
    }

    public Vector3f controlPoint(float[][] pose, int control, float along, Vector3f out) {
        this.controlWorld(pose, this.skeleton.controls[control], this.sourceControl);
        return this.sourceControl.transformPosition(out.set(0.0F, along, 0.0F));
    }

    private Matrix4f parentFrame(float[][] pose, int bone, Matrix4f out) {
        int parent = this.skeleton.parents[bone];
        if (parent < 0) {
            out.identity();
        } else {
            this.controlWorld(pose, parent, out);
        }

        return out.mul(this.skeleton.relative[bone]);
    }

    private void controlWorld(float[][] pose, int bone, Matrix4f out) {
        int parent = this.skeleton.parents[bone];
        if (parent < 0) {
            out.identity();
        } else {
            this.controlWorld(pose, parent, out);
        }

        int control = this.skeleton.controlOf[bone];
        if (control < 0) {
            throw new IllegalArgumentException("Driver ancestor cannot be evaluated as a control");
        }

        float[] v = pose[control];
        Poses.rotation(v, this.quat).normalize();
        float[] s = this.skeleton.scales[bone];
        out.mul(this.skeleton.relative[bone]).translate(v[0], v[1], v[2]).rotate(this.quat).scale(s[0], s[1], s[2]);
    }

    public void solve(float[][] pose, float[][] palette) {
        for (int i = 0; i < this.q.length; i++) {
            this.q[i][0] = this.q[i][1] = this.q[i][2] = 0.0F;
            this.q[i][3] = 1.0F;
            this.p[i][0] = this.p[i][1] = this.p[i][2] = 0.0F;
        }

        for (int i = 0; i < this.skeleton.controls.length; i++) {
            int b = this.skeleton.controls[i];
            System.arraycopy(pose[i], 0, this.p[b], 0, 3);
            System.arraycopy(pose[i], 3, this.q[b], 0, 4);
        }

        JointDrivers.evaluate(this.q, this.p);

        for (int i = 0; i < this.q.length; i++) {
            this.quat.set(this.q[i][0], this.q[i][1], this.q[i][2], this.q[i][3]).normalize();
            this.basis.translationRotate(this.p[i][0], this.p[i][1], this.p[i][2], this.quat);
            float[] s = this.skeleton.scales[i];
            this.basis.scale(s[0], s[1], s[2]);
            int parent = this.skeleton.parents[i];
            if (parent < 0) {
                this.world[i].set(this.skeleton.relative[i]).mul(this.basis);
            } else {
                this.world[i].set(this.world[parent]).mul(this.skeleton.relative[i]).mul(this.basis);
            }

            this.skin.set(this.toModel).mul(this.world[i]).mul(this.skeleton.inverse[i]).mul(this.fromModel);
            Palette.fromMatrix(this.skin, palette[i]);
        }
    }
}
