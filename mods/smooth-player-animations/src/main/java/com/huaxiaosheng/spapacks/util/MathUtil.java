package com.huaxiaosheng.spapacks.util;

public final class MathUtil {
    private MathUtil() {
    }

    public static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    public static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    public static float clamp01(float v) {
        return Math.max(0.0F, Math.min(1.0F, v));
    }

    public static float smoothstep(float t) {
        return t * t * (3.0F - 2.0F * t);
    }

    public static float smoothstep01(float t) {
        return smoothstep(clamp01(t));
    }

    public static float wrapDegrees(float degrees) {
        degrees %= 360.0F;
        if (degrees >= 180.0F) {
            degrees -= 360.0F;
        }

        if (degrees < -180.0F) {
            degrees += 360.0F;
        }

        return degrees;
    }

    public static float wrapRadians(float radians) {
        float turn = (float) (Math.PI * 2);
        radians %= turn;
        if (radians >= Math.PI) {
            radians -= turn;
        }

        if (radians < -Math.PI) {
            radians += turn;
        }

        return radians;
    }

    public static float approach(float current, float target, float step) {
        return Math.max(current - step, Math.min(current + step, target));
    }
}
