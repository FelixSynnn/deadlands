package com.huaxiaosheng.spapacks.animation.external;

import java.util.Arrays;

public final class ExternalPose {
    public final float[] weight = new float[6];
    public final float[][] value = new float[6][6];
    public final float[][] vanilla = new float[6][6];
    public final float[][] bend = new float[6][2];
    public boolean exclusive;
    public final float[][] standing = new float[6][6];
    public final float[][] model = new float[6][6];

    public void vanillaOnly() {
        this.clear();

        for (int p = 0; p < 6; p++) {
            System.arraycopy(this.vanilla[p], 0, this.model[p], 0, 6);
        }
    }

    public void clear() {
        Arrays.fill(this.weight, 0.0F);
        this.exclusive = false;

        for (float[] b : this.bend) {
            b[0] = 0.0F;
            b[1] = 0.0F;
        }
    }

    public boolean active() {
        for (float w : this.weight) {
            if (w > 0.0F) {
                return true;
            }
        }

        return false;
    }
}
