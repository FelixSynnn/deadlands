package com.huaxiaosheng.spapacks.anim.pose;

import java.util.Arrays;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

public final class Poses {
    public static final int SCALED = 10;

    private Poses() {
    }

    public static float[][] create(int bones) {
        return create(bones, false);
    }

    public static float[][] create(int bones, boolean scaled) {
        float[][] pose = new float[bones][scaled ? 10 : 7];
        reset(pose);
        return pose;
    }

    public static void reset(float[][] pose) {
        for (float[] b : pose) {
            resetBone(b);
        }
    }

    public static void resetBone(float[] b) {
        Arrays.fill(b, 0.0F);
        b[6] = 1.0F;
        if (b.length >= 10) {
            b[7] = b[8] = b[9] = 1.0F;
        }
    }

    public static Quaternionf rotation(float[] b, Quaternionf out) {
        return out.set(b[3], b[4], b[5], b[6]);
    }

    public static void setRotation(float[] b, Quaternionfc q) {
        b[3] = q.x();
        b[4] = q.y();
        b[5] = q.z();
        b[6] = q.w();
    }

    public static Vector3f eulerZYX(float[] b, Quaternionf q, Matrix3f m, Vector3f out) {
        return m.set(q.set(b[3], b[4], b[5], b[6]).normalize()).getEulerAnglesZYX(out);
    }

    public static void copy(float[][] from, float[][] to) {
        for (int i = 0; i < from.length; i++) {
            System.arraycopy(from[i], 0, to[i], 0, Math.min(from[i].length, to[i].length));
        }
    }

    public static void blend(float[][] a, float[][] b, float t, float[][] out) {
        for (int i = 0; i < a.length; i++) {
            blendBone(a[i], b[i], t, out[i]);
        }
    }

    public static void blend(float[][] a, float[][] b, float t, float[] mask, float[][] out) {
        for (int i = 0; i < a.length; i++) {
            float w = mask == null ? t : t * mask[i];
            if (w <= 0.0F) {
                if (out != a) {
                    System.arraycopy(a[i], 0, out[i], 0, Math.min(a[i].length, out[i].length));
                }
            } else {
                blendBone(a[i], b[i], w, out[i]);
            }
        }
    }

    public static void blendBone(float[] a, float[] b, float t, float[] out) {
        if (t >= 1.0F) {
            System.arraycopy(b, 0, out, 0, Math.min(b.length, out.length));
        } else {
            if (out.length >= 10 && a.length >= 10 && b.length >= 10) {
                for (int k = 7; k < 10; k++) {
                    out[k] = a[k] + (b[k] - a[k]) * t;
                }
            }

            float ax = a[3];
            float ay = a[4];
            float az = a[5];
            float aw = a[6];
            float bx = b[3];
            float by = b[4];
            float bz = b[5];
            float bw = b[6];
            float dot = ax * bx + ay * by + az * bz + aw * bw;
            if (dot < 0.0F) {
                bx = -bx;
                by = -by;
                bz = -bz;
                bw = -bw;
                dot = -dot;
            }

            double u = 1.0 - t;
            double v = t;
            if (dot < 0.9995F) {
                double angle = Math.acos(Math.min(1.0F, dot));
                double s = Math.sin(angle);
                u = Math.sin((1.0 - t) * angle) / s;
                v = Math.sin(t * angle) / s;
            }

            for (int k = 0; k < 3; k++) {
                out[k] = a[k] + (b[k] - a[k]) * t;
            }

            double x = ax * u + bx * v;
            double y = ay * u + by * v;
            double z = az * u + bz * v;
            double w = aw * u + bw * v;
            double n = 1.0 / Math.sqrt(x * x + y * y + z * z + w * w);
            out[3] = (float)(x * n);
            out[4] = (float)(y * n);
            out[5] = (float)(z * n);
            out[6] = (float)(w * n);
        }
    }

    public static void add(float[][] base, float[][] delta, float t, float[] mask, float[][] out) {
        for (int i = 0; i < base.length; i++) {
            float w = mask == null ? t : t * mask[i];
            float[] b = base[i];
            float[] d = delta[i];
            float[] o = out[i];
            if (w <= 0.0F) {
                if (o != b) {
                    System.arraycopy(b, 0, o, 0, Math.min(b.length, o.length));
                }
            } else {
                if (o.length >= 10 && b.length >= 10 && d.length >= 10) {
                    for (int k = 7; k < 10; k++) {
                        o[k] = b[k] + (d[k] - 1.0F) * w;
                    }
                }

                o[0] = b[0] + d[0] * w;
                o[1] = b[1] + d[1] * w;
                o[2] = b[2] + d[2] * w;
                addRotation(b, d, w, o);
            }
        }
    }

    public static void addRotation(float[] base, float[] delta, float w, float[] out) {
        float dx = delta[3];
        float dy = delta[4];
        float dz = delta[5];
        float dw = delta[6];
        if (w < 1.0F) {
            if (dw < 0.0F) {
                dx = -dx;
                dy = -dy;
                dz = -dz;
                dw = -dw;
            }

            double angle = Math.acos(Math.min(1.0F, dw));
            double s = Math.sin(angle);
            if (s > 1.0E-6) {
                double k = Math.sin(w * angle) / s;
                dx = (float)(dx * k);
                dy = (float)(dy * k);
                dz = (float)(dz * k);
                dw = (float)Math.cos(w * angle);
            } else {
                dx = 0.0F;
                dy = 0.0F;
                dz = 0.0F;
                dw = 1.0F;
            }
        }

        float bx = base[3];
        float by = base[4];
        float bz = base[5];
        float bw = base[6];
        out[3] = bw * dx + bx * dw + by * dz - bz * dy;
        out[4] = bw * dy - bx * dz + by * dw + bz * dx;
        out[5] = bw * dz + bx * dy - by * dx + bz * dw;
        out[6] = bw * dw - bx * dx - by * dy - bz * dz;
    }

    public static void mirror(Skeleton skeleton, float[][] source, float[][] out, float[][] scratch) {
        float[][] src = source;
        if (source == out) {
            copy(source, scratch);
            src = scratch;
        }

        for (int b = 0; b < src.length; b++) {
            float[] s = src[skeleton.mirror(b)];
            float[] o = out[b];
            o[0] = -s[0];
            o[1] = s[1];
            o[2] = s[2];
            o[3] = s[3];
            o[4] = -s[4];
            o[5] = -s[5];
            o[6] = s[6];
            if (o.length >= 10 && s.length >= 10) {
                o[7] = s[7];
                o[8] = s[8];
                o[9] = s[9];
            }
        }
    }
}
