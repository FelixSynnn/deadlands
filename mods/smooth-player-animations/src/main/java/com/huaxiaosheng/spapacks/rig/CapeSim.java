package com.huaxiaosheng.spapacks.rig;

import java.util.Arrays;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class CapeSim {
    public static final float TICK = 0.05F;
    private static final float DAMP = 0.8F;
    private static final float QUICK = 160.0F;
    private static final Vector3f ALONG = new Vector3f(0.0F, 1.0F, 0.0F);
    private static final Vector3f ACROSS = new Vector3f(1.0F, 0.0F, 0.0F);
    private static final float SIDE_SPRING = 30.0F;
    private static final float SIDE_SOFT = 1.5F;
    private static final float SIDE_DAMPING = 4.0F;
    private final CapeSim.Params params;
    private final int count;
    private final float[] now;
    private final float[] prev;
    private final float[] before;
    private final float[] moved;
    private final float[] swept;
    private final float[] normals;
    private final float[] need;
    private final float[] bound;
    private final float[] kinked;
    private final float[] held;
    private float bodyHold;
    private float holdDecay = -1.0F;
    private float bodyReach;
    private final float[] drawn;
    private final float[] anchor = new float[3];
    private boolean placed;
    private float lastStep = 0.05F;
    private final Vector3f v = new Vector3f();
    private final Vector3f u = new Vector3f();
    private final Vector3f w = new Vector3f();
    private final Quaternionf turn = new Quaternionf();
    private final Quaternionf turnPart = new Quaternionf();
    private final Vector3f move = new Vector3f();
    private final Matrix4f piece = new Matrix4f();

    public CapeSim(CapeSim.Params params) {
        this.params = params;
        this.count = params.segments() + 1;
        this.now = new float[this.count * 3];
        this.prev = new float[this.count * 3];
        this.before = new float[this.count * 3];
        this.moved = new float[this.count * 3];
        this.swept = new float[this.count * 3];
        this.normals = new float[this.count * 3];
        this.need = new float[this.count];
        this.bound = new float[this.count];
        this.kinked = new float[this.count * 3];
        this.drawn = new float[this.count * 3];
        this.held = new float[this.count];
    }

    public CapeSim.Params params() {
        return this.params;
    }

    public Vector3f across() {
        return ACROSS;
    }

    public float[] points() {
        return this.now;
    }

    public boolean placed() {
        return this.placed;
    }

    public void hang(float[] anchor, Vector3f down) {
        this.u.set(ALONG);
        if (down != null && down.lengthSquared() > 1.0E-12F) {
            this.u.set(down).normalize();
            if (this.u.z < 0.0F) {
                this.u.z = 0.0F;
                if (this.u.lengthSquared() < 1.0E-12F) {
                    this.u.set(ALONG);
                }

                this.u.normalize();
            }
        }

        System.arraycopy(anchor, 0, this.anchor, 0, 3);
        float l = this.params.segment();

        for (int i = 0; i < this.count; i++) {
            this.now[i * 3] = anchor[0] + this.u.x * l * i;
            this.now[i * 3 + 1] = anchor[1] + this.u.y * l * i;
            this.now[i * 3 + 2] = anchor[2] + this.u.z * l * i;
        }

        for (int k = 0; k < this.params.iterations(); k++) {
            this.shape();
        }

        System.arraycopy(this.now, 0, this.before, 0, this.now.length);
        System.arraycopy(this.now, 0, this.prev, 0, this.now.length);
        this.dirs(this.drawn);
        Arrays.fill(this.held, -Float.MAX_VALUE);
        this.computeNormals();
        this.lastStep = 0.05F / this.params.substeps();
        this.placed = true;
    }

    public void bodyReach(float slope) {
        this.bodyReach = Math.max(0.0F, slope);
    }

    public void bodyHold(float seconds) {
        this.bodyHold = Math.max(0.0F, seconds);
    }

    public void tick(Matrix4f change, float[] anchor, Vector3f gravity, float air, CapeSim.Collider body) {
        this.step(change, 0.05F, anchor, gravity, air, body);
    }

    public void step(Matrix4f change, float dt, float[] anchor, Vector3f gravity, float air, CapeSim.Collider body) {
        if (!this.placed) {
            this.hang(anchor, gravity);
        } else {
            Vector3f moveAll = change.getTranslation(this.move);
            if (!(moveAll.lengthSquared() < this.params.height() * this.params.height() * 16.0F)) {
                this.hang(anchor, gravity);
            } else if (dt > 0.0F) {
                System.arraycopy(this.now, 0, this.prev, 0, this.now.length);
                int steps = Math.max(1, (int)Math.ceil(dt / (0.05F / this.params.substeps()) - 0.001F));
                float h = dt / steps;
                float rescale = h / this.lastStep;
                this.lastStep = h;
                Quaternionf whole = change.getNormalizedRotation(this.turn);
                Matrix4f piece = this.piece
                    .translation(moveAll.x / steps, moveAll.y / steps, moveAll.z / steps)
                    .rotate(this.turnPart.identity().slerp(whole, 1.0F / steps));
                float[] from = new float[]{this.anchor[0], this.anchor[1], this.anchor[2]};
                float alongBase = this.params.drag() * air * h;
                float acrossBase = this.params.lift() * air * h;
                float quick = 160.0F * h;
                float settle = 1.0F - (float)Math.exp(-this.params.damping() * h);
                float sideSettle = 1.0F - (float)Math.exp(-4.0F * h);
                float sideSpring = 30.0F * h * h;
                float ax = gravity.x * h * h;
                float ay = gravity.y * h * h;
                float az = gravity.z * h * h;

                for (int s = 1; s <= steps; s++) {
                    System.arraycopy(this.now, 0, this.swept, 0, this.now.length);
                    this.carry(piece, this.now);
                    this.carry(piece, this.before);

                    for (int i = 3; i < this.drawn.length; i += 3) {
                        piece.transformDirection(this.drawn[i], this.drawn[i + 1], this.drawn[i + 2], this.v);
                        this.drawn[i] = this.v.x;
                        this.drawn[i + 1] = this.v.y;
                        this.drawn[i + 2] = this.v.z;
                    }

                    for (int i = 3; i < this.now.length; i += 3) {
                        float x = this.now[i];
                        float y = this.now[i + 1];
                        float z = this.now[i + 2];
                        float vx = (x - this.before[i]) * rescale;
                        float vy = (y - this.before[i + 1]) * rescale;
                        float vz = (z - this.before[i + 2]) * rescale;
                        vx -= (vx + x - this.swept[i]) * settle;
                        vy -= (vy + y - this.swept[i + 1]) * settle;
                        vz -= (vz + z - this.swept[i + 2]) * settle;
                        vx -= (vx + x - this.swept[i]) * sideSettle;
                        float out = (x - this.now[0]) / 1.5F;
                        vx -= (x - this.now[0]) * sideSpring * (1.0F + out * out);
                        float nx = this.normals[i];
                        float ny = this.normals[i + 1];
                        float nz = this.normals[i + 2];
                        float speed = (float)Math.sqrt(vx * vx + vy * vy + vz * vz);
                        float more = speed > quick ? speed / quick : 1.0F;
                        float alongDrag = Math.min(1.0F, alongBase * more);
                        float acrossDrag = Math.min(1.0F, acrossBase * more) - alongDrag;
                        float vn = (vx * nx + vy * ny + vz * nz) * acrossDrag;
                        this.now[i] = x + vx * (1.0F - alongDrag) - nx * vn + ax;
                        this.now[i + 1] = y + vy * (1.0F - alongDrag) - ny * vn + ay;
                        this.now[i + 2] = z + vz * (1.0F - alongDrag) - nz * vn + az;
                        this.before[i] = x;
                        this.before[i + 1] = y;
                        this.before[i + 2] = z;
                    }

                    float t = (float)s / steps;

                    for (int k = 0; k < 3; k++) {
                        this.now[k] = from[k] + (anchor[k] - from[k]) * t;
                        this.before[k] = this.now[k];
                    }

                    System.arraycopy(this.now, 0, this.moved, 0, this.now.length);

                    for (int k = 0; k < this.params.iterations(); k++) {
                        this.shape();
                    }

                    for (int i = 3; i < this.now.length; i++) {
                        this.before[i] = this.before[i] + (this.now[i] - this.moved[i]) * 0.8F;
                    }

                    if (body != null) {
                        System.arraycopy(this.now, 0, this.moved, 0, this.now.length);
                        this.holdDecay = this.bodyHold > 0.0F ? 1.0F - (float)Math.exp(-h / this.bodyHold) : -1.0F;
                        this.lay(this.now, body);
                        this.holdDecay = -1.0F;

                        for (int i = 3; i < this.now.length; i += 3) {
                            float px = this.now[i] - this.moved[i];
                            float py = this.now[i + 1] - this.moved[i + 1];
                            float pz = this.now[i + 2] - this.moved[i + 2];
                            this.before[i] = this.before[i] + px;
                            this.before[i + 1] = this.before[i + 1] + py;
                            this.before[i + 2] = this.before[i + 2] + pz;
                            float push = (float)Math.sqrt(px * px + py * py + pz * pz);
                            if (push > 1.0E-5F) {
                                piece.transformPosition(this.swept[i], this.swept[i + 1], this.swept[i + 2], this.v);
                                float in = (
                                        (this.now[i] - this.before[i] + this.v.x - this.swept[i]) * px
                                            + (this.now[i + 1] - this.before[i + 1] + this.v.y - this.swept[i + 1]) * py
                                            + (this.now[i + 2] - this.before[i + 2] + this.v.z - this.swept[i + 2]) * pz
                                    )
                                    / push;
                                if (in < 0.0F) {
                                    this.before[i] = this.before[i] + px / push * in;
                                    this.before[i + 1] = this.before[i + 1] + py / push * in;
                                    this.before[i + 2] = this.before[i + 2] + pz / push * in;
                                }
                            }
                        }
                    }

                    this.computeNormals();
                    rescale = 1.0F;
                }

                System.arraycopy(anchor, 0, this.anchor, 0, 3);
                float smoothing = this.params.smoothing();

                for (int i = 1; i < this.count; i++) {
                    float tau = smoothing * i / (this.count - 1);
                    float k = tau <= 0.0F ? 1.0F : 1.0F - (float)Math.exp(-dt / tau);
                    int p = i * 3;
                    int q = p - 3;
                    this.u.set(this.now[p] - this.now[q], this.now[p + 1] - this.now[q + 1], this.now[p + 2] - this.now[q + 2]);
                    if (!(this.u.lengthSquared() < 1.0E-12F)) {
                        this.u.normalize();
                        this.w.set(this.drawn[p], this.drawn[p + 1], this.drawn[p + 2]).lerp(this.u, k);
                        if (this.w.lengthSquared() < 1.0E-8F) {
                            this.w.set(this.u);
                        }

                        this.w.normalize();
                        this.drawn[p] = this.w.x;
                        this.drawn[p + 1] = this.w.y;
                        this.drawn[p + 2] = this.w.z;
                    }
                }

                for (float f : this.now) {
                    if (!Float.isFinite(f)) {
                        this.hang(anchor, gravity);
                        return;
                    }
                }
            }
        }
    }

    public void shown(float t, float[] anchor, float[] out) {
        out[0] = anchor[0];
        out[1] = anchor[1];
        out[2] = anchor[2];
        if (this.params.smoothing() > 0.0F) {
            float l = this.params.segment();

            for (int i = 3; i < this.now.length; i++) {
                out[i] = out[i - 3] + this.drawn[i] * l;
            }
        } else {
            t = Math.max(0.0F, Math.min(1.0F, t));

            for (int i = 3; i < this.now.length; i++) {
                out[i] = this.prev[i] + (this.now[i] - this.prev[i]) * t;
            }
        }

        this.down(out, null);
    }

    public void lay(float[] points, CapeSim.Collider collider) {
        float l = this.params.segment();
        float half = this.params.width() / 2.0F;
        float slope = l * (float)Math.sin(Math.toRadians(this.params.maxBend()));

        for (int pass = 0; pass < 2; pass++) {
            for (int i = 1; i < this.count; i++) {
                int p = i * 3;
                int q = p - 3;
                float least = collider.least(points[p], Math.min(points[q + 1], points[p + 1]), Math.max(points[q + 1], points[p + 1]), half);
                this.bound[i] = Float.isNaN(least) ? -Float.MAX_VALUE : least;
                if (this.bodyReach > 0.0F) {
                    this.bound[i] = Math.min(this.bound[i], points[2] + this.bodyReach * i * l);
                }

                if (this.bodyHold > 0.0F) {
                    float target = Math.max(this.bound[i], points[2]);
                    if (pass == 0 && this.holdDecay >= 0.0F) {
                        this.held[i] = Math.max(target, this.held[i] + (target - this.held[i]) * this.holdDecay);
                    }

                    this.bound[i] = Math.max(this.bound[i], this.held[i]);
                }
            }

            float top = points[2];
            float below = -Float.MAX_VALUE;

            for (int i = this.count - 1; i >= 1; i--) {
                if (below != -Float.MAX_VALUE) {
                    float line = top;

                    for (int j = i + 1; j < this.count; j++) {
                        if (this.bound[j] > top) {
                            line = Math.max(line, top + (this.bound[j] - top) * i / j);
                        }
                    }

                    below = Math.min(below - slope, line);
                }

                below = Math.max(this.bound[i], below);
                this.need[i] = below;
            }

            this.down(points, this.need);
        }
    }

    private void down(float[] points, float[] need) {
        float l = this.params.segment();
        float cos = (float)Math.cos(Math.toRadians(this.params.maxBend()));
        float liftCos = (float)Math.cos(Math.toRadians(this.params.maxLift()));
        this.w.set(ALONG);

        for (int i = 1; i < this.count; i++) {
            int p = i * 3;
            int q = p - 3;
            this.u.set(points[p] - points[q], points[p + 1] - points[q + 1], points[p + 2] - points[q + 2]);
            if (this.u.lengthSquared() < 1.0E-12F) {
                this.u.set(this.w);
            }

            this.u.normalize();
            if (i > 1) {
                this.bend(this.u, this.w, cos);
            }

            this.lift(this.u, liftCos);
            if (need != null && points[q + 2] + this.u.z * l < need[i]) {
                float back = Math.min(1.0F, (need[i] - points[q + 2]) / l);
                float rest = 1.0F - back * back;
                float side = Math.max(-1.0F, Math.min(1.0F, this.u.x));
                if (side * side > rest) {
                    side = Math.copySign((float)Math.sqrt(rest), side);
                }

                this.u.set(side, Math.copySign((float)Math.sqrt(Math.max(0.0F, rest - side * side)), this.u.y), back);
            }

            points[p] = points[q] + this.u.x * l;
            points[p + 1] = points[q + 1] + this.u.y * l;
            points[p + 2] = points[q + 2] + this.u.z * l;
            this.w.set(this.u);
        }
    }

    public static Matrix4f blend(Matrix4f from, Matrix4f to, float t, Matrix4f out) {
        Quaternionf a = from.getUnnormalizedRotation(new Quaternionf()).normalize();
        Quaternionf b = to.getUnnormalizedRotation(new Quaternionf()).normalize();
        Vector3f scale = to.getScale(new Vector3f());
        Vector3f place = from.getTranslation(new Vector3f()).lerp(to.getTranslation(new Vector3f()), t);
        return out.translationRotateScale(place, a.slerp(b, t), scale);
    }

    public void keep(float[] points) {
        for (int i = 1; i < this.count; i++) {
            int p = i * 3;
            int q = p - 3;
            this.v.set(points[p] - points[q], points[p + 1] - points[q + 1], points[p + 2] - points[q + 2]);
            if (this.v.lengthSquared() > 1.0E-12F) {
                this.v.normalize();
                this.drawn[p] = this.v.x;
                this.drawn[p + 1] = this.v.y;
                this.drawn[p + 2] = this.v.z;
            }
        }
    }

    private void dirs(float[] out) {
        for (int i = 1; i < this.count; i++) {
            int p = i * 3;
            int q = p - 3;
            this.v.set(this.now[p] - this.now[q], this.now[p + 1] - this.now[q + 1], this.now[p + 2] - this.now[q + 2]);
            if (this.v.lengthSquared() < 1.0E-12F) {
                this.v.set(ALONG);
            } else {
                this.v.normalize();
            }

            out[p] = this.v.x;
            out[p + 1] = this.v.y;
            out[p + 2] = this.v.z;
        }
    }

    private void carry(Matrix4f change, float[] set) {
        for (int i = 0; i < set.length; i += 3) {
            change.transformPosition(set[i], set[i + 1], set[i + 2], this.v);
            set[i] = this.v.x;
            set[i + 1] = this.v.y;
            set[i + 2] = this.v.z;
        }
    }

    private void shape() {
        float l = this.params.segment();
        float cos = (float)Math.cos(Math.toRadians(this.params.maxBend()));
        float liftCos = (float)Math.cos(Math.toRadians(this.params.maxLift()));
        float kink = this.params.stiffness();
        if (kink > 0.0F) {
            int n = this.count - 1;

            for (int i = 1; i <= n; i++) {
                for (int k = 0; k < 3; k++) {
                    float c = this.now[i * 3 + k];
                    float a1 = this.now[(i - 1) * 3 + k];
                    float a2 = i >= 2 ? this.now[(i - 2) * 3 + k] : 2.0F * a1 - c;
                    float b1 = i + 1 <= n ? this.now[(i + 1) * 3 + k] : 2.0F * c - a1;
                    float b2 = i + 2 <= n ? this.now[(i + 2) * 3 + k] : 2.0F * b1 - c;
                    this.kinked[i * 3 + k] = -(a2 - 4.0F * a1 + 6.0F * c - 4.0F * b1 + b2) / 16.0F * kink;
                }
            }

            for (int i = 3; i < this.now.length; i++) {
                this.now[i] = this.now[i] + this.kinked[i];
            }
        }

        for (int i = 1; i < this.count; i++) {
            this.relax(i, l);
        }

        for (int i = this.count - 1; i >= 1; i--) {
            this.relax(i, l);
        }

        for (int i = 1; i < this.count; i++) {
            int p = i * 3;
            int q = p - 3;
            float reach = this.params.sway() * ((float)i / (this.count - 1)) * ((float)i / (this.count - 1));
            float side = this.now[p] - this.now[0];
            if (Math.abs(side) > reach) {
                this.now[p] = this.now[p] - (side - Math.copySign(reach, side));
            }

            this.u.set(this.now[p] - this.now[q], this.now[p + 1] - this.now[q + 1], this.now[p + 2] - this.now[q + 2]);
            if (this.u.lengthSquared() < 1.0E-12F) {
                this.u.set(ALONG);
            }

            this.u.normalize();
            if (i > 1) {
                int r = q - 3;
                this.w.set(this.now[q] - this.now[r], this.now[q + 1] - this.now[r + 1], this.now[q + 2] - this.now[r + 2]).normalize();
                this.bend(this.u, this.w, cos);
            }

            this.lift(this.u, liftCos);
            float z = this.now[q + 2] + this.u.z * l;
            if (z < this.now[2]) {
                float back = Math.min(1.0F, (this.now[2] - this.now[q + 2]) / l);
                float rest = (float)Math.sqrt(Math.max(0.0F, 1.0F - back * back));
                float flat = (float)Math.sqrt(this.u.x * this.u.x + this.u.y * this.u.y);
                if (flat < 1.0E-6F) {
                    this.u.set(0.0F, rest, back);
                } else {
                    this.u.set(this.u.x / flat * rest, this.u.y / flat * rest, back);
                }
            }

            this.now[p] = this.now[q] + this.u.x * l;
            this.now[p + 1] = this.now[q + 1] + this.u.y * l;
            this.now[p + 2] = this.now[q + 2] + this.u.z * l;
        }
    }

    private void relax(int i, float l) {
        int p = i * 3;
        int q = p - 3;
        float dx = this.now[p] - this.now[q];
        float dy = this.now[p + 1] - this.now[q + 1];
        float dz = this.now[p + 2] - this.now[q + 2];
        float len = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!(len < 1.0E-6F)) {
            float k = (len - l) / len;
            float top = i == 1 ? 0.0F : 0.5F;
            this.now[q] = this.now[q] + dx * k * top;
            this.now[q + 1] = this.now[q + 1] + dy * k * top;
            this.now[q + 2] = this.now[q + 2] + dz * k * top;
            this.now[p] = this.now[p] - dx * k * (1.0F - top);
            this.now[p + 1] = this.now[p + 1] - dy * k * (1.0F - top);
            this.now[p + 2] = this.now[p + 2] - dz * k * (1.0F - top);
        }
    }

    private void lift(Vector3f u, float liftCos) {
        float hang = u.dot(ALONG);
        if (!(hang >= liftCos)) {
            this.v.set(u).sub(ALONG.x * hang, ALONG.y * hang, ALONG.z * hang);
            if (this.v.z < 0.0F) {
                this.v.z = -this.v.z;
            }

            if (this.v.lengthSquared() < 1.0E-12F) {
                this.v.set(0.0F, 0.0F, 1.0F);
            }

            this.v.normalize();
            float sin = (float)Math.sqrt(Math.max(0.0F, 1.0F - liftCos * liftCos));
            u.set(ALONG).mul(liftCos).add(this.v.x * sin, this.v.y * sin, this.v.z * sin);
        }
    }

    private void bend(Vector3f u, Vector3f w, float cos) {
        float dot = u.dot(w);
        if (!(dot >= cos)) {
            this.v.set(u).sub(w.x * dot, w.y * dot, w.z * dot);
            if (this.v.lengthSquared() < 1.0E-12F) {
                this.v.set(ACROSS).cross(w);
            }

            this.v.normalize();
            float sin = (float)Math.sqrt(Math.max(0.0F, 1.0F - cos * cos));
            u.set(w).mul(cos).add(this.v.x * sin, this.v.y * sin, this.v.z * sin);
        }
    }

    private void computeNormals() {
        for (int i = 0; i < this.count; i++) {
            int a = Math.max(0, i - 1) * 3;
            int b = Math.min(this.count - 1, i + 1) * 3;
            this.u.set(this.now[b] - this.now[a], this.now[b + 1] - this.now[a + 1], this.now[b + 2] - this.now[a + 2]);
            this.v.set(ACROSS).cross(this.u);
            float len = this.v.length();
            if (len > 1.0E-6F) {
                this.normals[i * 3] = this.v.x / len;
                this.normals[i * 3 + 1] = this.v.y / len;
                this.normals[i * 3 + 2] = this.v.z / len;
            } else {
                this.normals[i * 3] = 0.0F;
                this.normals[i * 3 + 1] = 0.0F;
                this.normals[i * 3 + 2] = 1.0F;
            }
        }
    }

    public interface Collider {
        float least(float var1, float var2, float var3, float var4);
    }

    public record Params(
        float width,
        float height,
        int segments,
        float drag,
        float lift,
        int iterations,
        float maxBend,
        float maxLift,
        float sway,
        float damping,
        float stiffness,
        float smoothing,
        int substeps
    ) {
        public Params {
            width = Math.max(0.01F, width);
            height = Math.max(0.01F, height);
            segments = Math.max(2, Math.min(64, segments));
            drag = Math.max(0.0F, drag);
            lift = Math.max(drag, lift);
            iterations = Math.max(1, Math.min(64, iterations));
            maxBend = Math.max(1.0F, Math.min(180.0F, maxBend));
            maxLift = Math.max(1.0F, Math.min(180.0F, maxLift));
            sway = Math.max(0.0F, sway);
            damping = Math.max(0.0F, damping);
            stiffness = Math.max(0.0F, Math.min(1.0F, stiffness));
            smoothing = Math.max(0.0F, Math.min(1.0F, smoothing));
            substeps = Math.max(1, Math.min(32, substeps));
        }

        public float segment() {
            return this.height / this.segments;
        }
    }
}
