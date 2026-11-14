package com.huaxiaosheng.spapacks.rig;

import java.util.List;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class Anchors {
    public static final List<String> IDS = List.of(
        "root", "torso_upper", "head", "right_hand", "left_hand", "right_foot", "left_foot", "right_item", "left_item"
    );
    public static final float[] ITEM_GRIP = new float[]{-0.3125F, -0.3125F, 0.0F};
    public static final float[] ITEM_TIP = new float[]{0.46875F, 0.46875F, 0.0F};
    public static final float ITEM_SCALE = 0.796875F;
    public static final float GRIP_Y = 0.5350421F;
    public static final float GRIP_Z = -0.04205085F;
    private static final Matrix4f TO_MODEL = new Matrix4f().translation(0.0F, 1.501F, 0.0F).scale(1.0666667F, -1.0666667F, -1.0666667F);
    private static final Matrix4f FROM_MODEL = new Matrix4f(TO_MODEL).invert();
    private static float[][] rest;

    public static float[] tip() {
        return new float[]{ITEM_TIP[0] * 0.796875F, ITEM_TIP[1] * 0.796875F, ITEM_TIP[2] * 0.796875F};
    }

    public static float[] grip() {
        return new float[]{ITEM_GRIP[0] * 0.796875F, ITEM_GRIP[1] * 0.796875F, ITEM_GRIP[2] * 0.796875F};
    }

    public static Matrix4f holdItem(Matrix4f hand, boolean left, Quaternionf turn, Vector3f offset) {
        if (offset != null && offset.lengthSquared() > 1.0E-12F) {
            hand.translate(offset);
        }

        if (Math.abs(turn.w) < 0.99999F) {
            float gripX = left ? 0.0625F : -0.0625F;
            hand.translate(gripX, 0.5350421F, -0.04205085F).rotate(turn).translate(-gripX, -0.5350421F, 0.04205085F);
        }

        return hand;
    }

    private Anchors() {
    }

    public static Matrix4f toModel() {
        return new Matrix4f(TO_MODEL);
    }

    public static boolean model(String id, float[][] palette, Quaternionf[] items, boolean slim, Matrix4f out) {
        return model(id, palette, items, null, slim, out);
    }

    public static boolean model(String id, float[][] palette, Quaternionf[] items, Vector3f[] offsets, boolean slim, Matrix4f out) {
        int a = IDS.indexOf(id);
        if (a < 0) {
            return false;
        } else if (a == 0) {
            out.set(TO_MODEL);
            return true;
        } else if (id.endsWith("_item")) {
            item(id.startsWith("left") ? 1 : 0, palette, items, offsets, slim, out);
            return true;
        } else {
            float[] r = rest()[a];
            Palette.toMatrix(palette[SkinnedMesh.ALEX.boneIndex(bone(id))], out).translate(r[0], r[1], r[2]).mulLocal(TO_MODEL);
            return true;
        }
    }

    static String bone(String id) {
        return switch (id) {
            case "torso_upper" -> "torso_upper";
            case "head" -> "head";
            case "right_hand", "right_item" -> "right_arm_lower_def";
            case "left_hand", "left_item" -> "left_arm_lower_def";
            case "right_foot" -> "right_leg_lower_def";
            case "left_foot" -> "left_leg_lower_def";
            default -> "body";
        };
    }

    public static Matrix4f item(int hand, float[][] palette, Quaternionf[] items, boolean slim, Matrix4f out) {
        return item(hand, palette, items, null, slim, out);
    }

    public static Matrix4f item(int hand, float[][] palette, Quaternionf[] items, Vector3f[] offsets, boolean slim, Matrix4f out) {
        boolean left = hand == 1;
        Palette.toMatrix(palette[SkinnedMesh.ALEX.boneIndex(left ? "left_arm_lower_def" : "right_arm_lower_def")], out).mul(FROM_MODEL).mulLocal(TO_MODEL);
        float px = (left ? 5.0F : -5.0F) / 16.0F;
        if (slim) {
            px += left ? -0.03125F : 0.03125F;
        }

        out.translate(px, 0.125F, 0.0F);
        holdItem(out, left, items[hand], offsets == null ? null : offsets[hand]);
        out.rotateX((float)Math.toRadians(-90.0)).rotateY((float) Math.PI).translate((left ? -1.0F : 1.0F) / 16.0F, 0.125F, -0.625F);
        float ry = left ? 90.0F : -90.0F;
        float rz = left ? -55.0F : 55.0F;
        if (left) {
            ry = -ry;
            rz = -rz;
        }

        out.translate(0.0F, 0.25F, 0.03125F);
        out.rotate(new Quaternionf().rotationXYZ(0.0F, (float)Math.toRadians(ry), (float)Math.toRadians(rz)));
        return out.scale(0.85F);
    }

    private static synchronized float[][] rest() {
        if (rest == null) {
            float[][] r = new float[IDS.size()][];
            SkinnedMesh mesh = SkinnedMesh.ALEX;

            for (int a = 1; a < IDS.size(); a++) {
                String id = IDS.get(a);
                if (!id.endsWith("_item")) {
                    int bone = mesh.boneIndex(bone(id));
                    boolean bottom = id.endsWith("_hand") || id.endsWith("_foot");
                    float[] min = new float[]{Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
                    float[] max = new float[]{-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};

                    for (SkinnedMesh.Vertex v : mesh.vertices()) {
                        for (int k = 0; k < v.bones().length; k++) {
                            if (v.bones()[k] == bone && v.weights()[k] >= 0.5F) {
                                for (int c = 0; c < 3; c++) {
                                    min[c] = Math.min(min[c], v.p()[c]);
                                    max[c] = Math.max(max[c], v.p()[c]);
                                }
                            }
                        }
                    }

                    r[a] = min[0] > max[0]
                        ? new float[3]
                        : new float[]{(min[0] + max[0]) / 2.0F, bottom ? min[1] : (min[1] + max[1]) / 2.0F, (min[2] + max[2]) / 2.0F};
                }
            }

            rest = r;
        }

        return rest;
    }
}
