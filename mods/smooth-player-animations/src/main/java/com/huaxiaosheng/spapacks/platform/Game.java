package com.huaxiaosheng.spapacks.platform;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public final class Game {
    private static final Matrix3f NORMAL = new Matrix3f();
    private static final Matrix4f ROTATION = new Matrix4f();

    private Game() {
    }

    public static Identifier id(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }

    public static boolean debugScreen(Minecraft client) {
        return client.getDebugOverlay().showDebugScreen();
    }

    public static float partialTick() {
        return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
    }

    public static Vec3 cameraPosition(Minecraft client) {
        return client.gameRenderer.getMainCamera().position();
    }

    public static Matrix4f unturnView(Matrix4f m) {
        return m;
    }

    public static Screen screen(Minecraft client) {
        return client.screen;
    }

    public static void setScreen(Minecraft client, Screen screen) {
        client.setScreen(screen);
    }

    public static boolean swinging(LivingEntity entity) {
        return entity.swinging;
    }

    public static int swingTicks(LivingEntity entity) {
        return entity.swingTime;
    }

    public static InteractionHand swingHand(LivingEntity entity) {
        return entity.swingingArm;
    }

    public static void multiply(PoseStack matrices, Matrix4f matrix) {
        matrices.mulPose(matrix);
    }

    public static void rotate(PoseStack matrices, Quaternionf rotation) {
        matrices.mulPose(rotation);
    }

    public static void chat(LocalPlayer player, Component message) {
        player.displayClientMessage(message, false);
    }

    public static boolean movementKeys(LocalPlayer player) {
        return player.input.getMoveVector().lengthSquared() > 1.0E-4F;
    }

    public static boolean jumpKey(LocalPlayer player) {
        return player.input.keyPresses.jump();
    }

    public static int selectedSlot(Player player) {
        return player.getInventory().getSelectedSlot();
    }

    public static float x(PartPose pose) {
        return pose.x();
    }

    public static float y(PartPose pose) {
        return pose.y();
    }

    public static float z(PartPose pose) {
        return pose.z();
    }

    public static float yRot(PartPose pose) {
        return pose.yRot();
    }

    public static boolean slim(AbstractClientPlayer player) {
        return player.getSkin().model() == PlayerModelType.SLIM;
    }

    public static Identifier skinTexture(AbstractClientPlayer player) {
        return player.getSkin().body().texturePath();
    }

    public static boolean opaque(NativeImage image, int x, int y) {
        return image.getPixel(x, y) >>> 24 != 0;
    }
}
