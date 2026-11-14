package com.huaxiaosheng.spapacks.client;

import com.huaxiaosheng.spapacks.animation.cem.CemInput;
import com.huaxiaosheng.spapacks.platform.Game;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;

final class CemBinding {
    private CemBinding() {
    }

    static void capture(AbstractClientPlayer p, float tickDelta, float headYaw, float headPitch, CemBinding.TickYaw yaw, CemInput in) {
        Minecraft client = Minecraft.getInstance();
        boolean local = p == client.player;
        in.limbSwing = p.walkAnimation.position(tickDelta);
        in.limbSpeed = Math.min(1.0F, p.walkAnimation.speed(tickDelta));
        in.age = p.tickCount + tickDelta;
        in.headYaw = Mth.wrapDegrees(headYaw);
        in.headPitch = headPitch;
        in.yaw = yaw.sample(p, tickDelta);
        in.posY = (float)Mth.lerp(tickDelta, p.yo, p.getY());
        in.velocityY = (float)(p.getY() - p.yo);
        Vec3 motion = local ? p.getDeltaMovement() : new Vec3(p.getX() - p.xo, 0.0, p.getZ() - p.zo);
        double speed = Math.hypot(motion.x, motion.z);
        if (speed < 1.0E-7) {
            in.moveForward = 0.0F;
            in.moveStrafing = 0.0F;
        } else {
            double look = Math.toRadians(90.0 - in.yaw);
            in.moveForward = (float)(-(motion.x * Math.cos(look) - motion.z * Math.sin(look)) / speed);
            in.moveStrafing = (float)(-(motion.x * Math.sin(look) + motion.z * Math.cos(look)) / speed);
        }

        in.hurtTime = p.hurtTime;
        in.id = p.getUUID().hashCode() & 65535;
        Entity camera = client.getCameraEntity();
        in.distance = camera == null ? 0.0F : (float)camera.position().distanceTo(p.position());
        in.fluidDepth = p.isUnderWater() ? 2.0F : (p.isInWater() ? 1.0F : 0.0F);
        in.onGround = p.onGround();
        in.inWater = p.isInWater();
        in.inLava = p.isInLava();
        in.sprinting = p.isSprinting();
        in.sneaking = p.isCrouching();
        in.swimming = p.isSwimming();
        in.crawling = p.isVisuallyCrawling();
        in.climbing = p.onClimbable();
        in.gliding = p.isFallFlying();
        in.flying = p.getAbilities().flying;
        in.riding = p.isPassenger();
        in.jumping = local ? client.options.keyJump.isDown() : !p.onGround() && in.velocityY > 0.0F;
        in.usingItem = p.isUsingItem();
        in.blocking = p.isBlocking();
        in.rightHanded = p.getMainArm() == HumanoidArm.RIGHT;
        boolean main = !p.getMainHandItem().isEmpty();
        boolean off = !p.getOffhandItem().isEmpty();
        in.holdingRight = in.rightHanded ? main : off;
        in.holdingLeft = in.rightHanded ? off : main;
        in.inGui = Game.screen(client) instanceof InventoryScreen || Game.screen(client) instanceof CreativeModeInventoryScreen;
        in.paused = client.isPaused();
    }

    static final class TickYaw {
        private int tick = Integer.MIN_VALUE;
        private float previous;
        private float current;

        float sample(AbstractClientPlayer p, float tickDelta) {
            if (p.tickCount != this.tick) {
                this.previous = p.tickCount == this.tick + 1 ? this.current : p.getYRot();
                this.current = p.getYRot();
                this.tick = p.tickCount;
            }

            return this.previous + Mth.wrapDegrees(this.current - this.previous) * tickDelta;
        }
    }
}
