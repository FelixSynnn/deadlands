package com.huaxiaosheng.spapacks.rig;

final class JointDrivers {
    private static final int[] LIMBS = new int[]{30, 42, 3, 15};
    private static final double[] HALF_WIDTH = new double[]{0.09375, 0.09375, 0.125, 0.125};
    private static final double HALF_DEPTH = 0.125;
    private static final double[] CORNER_X = new double[]{1.0, -1.0, 1.0, -1.0};
    private static final double[] CORNER_Z = new double[]{1.0, 1.0, -1.0, -1.0};
    private static final double CORNER_LIMIT = 0.1235;
    private static final double SLACK = 5.0E-4;
    private static final double MIN_SPREAD = 0.1464466094;
    private static final float SNAP = 1.0E-4F;

    private JointDrivers() {
    }

    private static double curve(double value) {
        float x = (float)value;
        if (x >= 0.0F && x <= 1.0E-4F) {
            return 0.0;
        } else {
            return x <= 1.0F && 1.0F - x <= 1.0E-4F ? 1.0 : x;
        }
    }

    static void evaluate(float[][] q, float[][] p) {
        for (int l = 0; l < LIMBS.length; l++) {
            limb(q, p, LIMBS[l], HALF_WIDTH[l]);
        }
    }

    private static void limb(float[][] q, float[][] p, int b, double halfWidth) {
        float x = q[b][0];
        float y = q[b][1];
        float z = q[b][2];
        float w = q[b][3];
        float[] at = p[b];
        double spread = Math.max(1.0 - x * x - z * z, 0.1464466094);
        float xyMinusWz = x * y - w * z;
        float xyPlusWz = x * y + w * z;
        float yzPlusWx = y * z + w * x;
        float yzMinusWx = y * z - w * x;
        float xzPlusWy = x * z + w * y;
        float xzMinusWy = x * z - w * y;
        float xxPlusZz = x * x + z * z;
        float yyPlusZz = y * y + z * z;
        float xxPlusYy = x * x + y * y;
        double push = curve(Math.max((halfWidth * Math.abs(xyMinusWz) + 0.125 * Math.abs(yzPlusWx)) / spread - 0.1235, 0.0));
        p[b + 1][0] = (float)curve(push * 2.0 * xyPlusWz);
        p[b + 1][1] = (float)curve(push * (2.0 - 2.0 * xxPlusZz));
        p[b + 1][2] = (float)curve(push * 2.0 * yzMinusWx);

        for (int c = 0; c < 4; c++) {
            double cx = CORNER_X[c] * halfWidth;
            double cz = CORNER_Z[c] * 0.125;
            double along = curve(-(cx * xyMinusWz + cz * yzPlusWx) / spread + push);
            p[b + 3 + c][1] = (float)curve(Math.min(Math.max(along, -0.1235), 0.1235));
            double offset = curve(Math.min(Math.max(-(cx * xyPlusWz + cz * yzMinusWx) / spread - push, -0.1235), 0.1235));
            double reach = push + offset + 5.0E-4;
            float[] corner = p[b + 7 + c];
            corner[0] = (float)curve(reach * 2.0 * xyMinusWz + (1.0 - 2.0 * yyPlusZz) * cx + 2.0 * xzPlusWy * cz - cx + at[0]);
            corner[1] = (float)curve(push + reach * (1.0 - 2.0 * xxPlusZz) + 2.0 * xyPlusWz * cx + 2.0 * yzMinusWx * cz - 5.0E-4 + at[1]);
            corner[2] = (float)curve(reach * 2.0 * yzPlusWx + 2.0 * xzMinusWy * cx + (1.0 - 2.0 * xxPlusYy) * cz - cz + at[2]);
        }
    }
}
