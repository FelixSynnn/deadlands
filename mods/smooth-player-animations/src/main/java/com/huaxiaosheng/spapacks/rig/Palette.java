package com.huaxiaosheng.spapacks.rig;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

public final class Palette {
    private Palette() {
    }

    public static Matrix4f toMatrix(float[] m, Matrix4f out) {
        return out.set(m[0], m[4], m[8], 0.0F, m[1], m[5], m[9], 0.0F, m[2], m[6], m[10], 0.0F, m[3], m[7], m[11], 1.0F);
    }

    public static void fromMatrix(Matrix4fc matrix, float[] out) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 4; col++) {
                out[row * 4 + col] = matrix.get(col, row);
            }
        }
    }

    public static float transform(float[] m, int row, float x, float y, float z) {
        int k = row * 4;
        return m[k] * x + m[k + 1] * y + m[k + 2] * z + m[k + 3];
    }
}
