package com.huaxiaosheng.spapacks.platform;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3f;

public final class Vertices {
    private Vertices() {
    }

    public static void put(VertexConsumer c, float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz) {
        c.addVertex(x, y, z, color, u, v, overlay, light, nx, ny, nz);
    }

    public static void put(
        VertexConsumer c, Pose pose, float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz
    ) {
        c.addVertex(pose.pose(), x, y, z).setColor(color).setUv(u, v).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
    }

    public static void render(ModelPart part, PoseStack matrices, VertexConsumer c, int light, int overlay, int color) {
        part.render(matrices, c, light, overlay, color);
    }

    public static int argb(float red, float green, float blue, float alpha) {
        return Math.round(alpha * 255.0F) << 24 | Math.round(red * 255.0F) << 16 | Math.round(green * 255.0F) << 8 | Math.round(blue * 255.0F);
    }

    public static Vector3f transformNormal(Pose pose, float x, float y, float z, Vector3f out) {
        return pose.transformNormal(x, y, z, out);
    }

    public static Vector3f transformNormal(Pose pose, Vector3f normal, Vector3f out) {
        return transformNormal(pose, normal.x, normal.y, normal.z, out);
    }

    public static void begin(VertexConsumer c, float x, float y, float z) {
        c.addVertex(x, y, z);
    }

    public static void color(VertexConsumer c, int red, int green, int blue, int alpha) {
        c.setColor(red, green, blue, alpha);
    }

    public static void uv(VertexConsumer c, float u, float v) {
        c.setUv(u, v);
    }

    public static void overlay(VertexConsumer c, int u, int v) {
        c.setUv1(u, v);
    }

    public static void light(VertexConsumer c, int u, int v) {
        c.setUv2(u, v);
    }

    public static void normal(VertexConsumer c, float x, float y, float z) {
        c.setNormal(x, y, z);
    }

    public static void end(VertexConsumer c) {
    }

    public abstract static class Sink implements VertexConsumer {
        protected void end() {
        }
    }
}
