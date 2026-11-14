package com.huaxiaosheng.spapacks.client;

import com.huaxiaosheng.spapacks.animation.HandReach;
import com.huaxiaosheng.spapacks.platform.Game;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

final class BlockReach {
    private static final double RANGE = 5.0;
    private static final double OFF_FACE = 0.06;
    private static final double IN_FROM_EDGE = 0.12;
    private static final int RUN = 8;
    private static final int STALE = 4;
    private boolean wasSwinging;
    private int lastTicks;
    private int sinceSwing = 8;
    private int start = -1;
    private int arm = -1;
    private boolean release;
    private int age;
    private BlockPos hinged;
    private double x;
    private double y;
    private double z;

    void tick(Minecraft client, AbstractClientPlayer player) {
        boolean swinging = Game.swinging(player);
        int ticks = Game.swingTicks(player);
        boolean began = swinging && (!this.wasSwinging || ticks < this.lastTicks);
        this.wasSwinging = swinging;
        this.lastTicks = ticks;
        this.age++;
        if (this.start >= 0 && this.age > 4) {
            this.start = -1;
        }

        if (this.arm >= 0 && this.hinged != null) {
            this.handle(player.level(), this.hinged, player.getEyePosition());
        }

        if (!began) {
            this.sinceSwing = Math.min(8, this.sinceSwing + 1);
        } else {
            boolean local = player == client.player;
            boolean run = this.sinceSwing < 8;
            this.sinceSwing = 0;
            if (ModConfig.get().handReach) {
                BlockHitResult hit = local ? (client.hitResult instanceof BlockHitResult b ? b : null) : picked(player);
                boolean use = hit != null
                    && hit.getType() == Type.BLOCK
                    && usable(player.level(), hit.getBlockPos())
                    && (local ? !client.options.keyAttack.isDown() : !run)
                    && !placing(player);
                if (!use) {
                    if (this.arm >= 0 && (local || run)) {
                        this.release = true;
                    }
                } else {
                    InteractionHand hand = Game.swingHand(player);
                    boolean right = hand != InteractionHand.OFF_HAND == (player.getMainArm() == HumanoidArm.RIGHT);
                    Direction face = hit.getDirection();
                    Vec3 at = hit.getLocation();
                    this.x = at.x + face.getStepX() * 0.06;
                    this.y = at.y + face.getStepY() * 0.06;
                    this.z = at.z + face.getStepZ() * 0.06;
                    BlockPos pos = hit.getBlockPos();
                    BlockState state = player.level().getBlockState(pos);
                    this.hinged = !(state.getBlock() instanceof DoorBlock) && !(state.getBlock() instanceof TrapDoorBlock) ? null : pos.immutable();
                    if (this.hinged != null) {
                        this.handle(player.level(), this.hinged, player.getEyePosition());
                    }

                    this.start = this.arm = right ? 0 : 1;
                    this.release = false;
                    this.age = 0;
                }
            }
        }
    }

    void frame(HandReach reach, boolean allowed, double px, double py, double pz) {
        if (this.arm >= 0) {
            if (!allowed || this.release) {
                reach.release(this.arm);
                this.release = false;
                this.start = -1;
            } else if (this.start >= 0) {
                reach.start(this.start);
                this.start = -1;
            }

            reach.target(this.arm, (float)(this.x - px), (float)(this.y - py), (float)(this.z - pz));
            if (!reach.reaching(this.arm) && this.start < 0) {
                this.arm = -1;
            }
        }
    }

    private void handle(Level level, BlockPos pos, Vec3 eye) {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        if (block instanceof DoorBlock && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
            pos = pos.below();
            state = level.getBlockState(pos);
            block = state.getBlock();
        }

        boolean door = block instanceof DoorBlock;
        if ((door || block instanceof TrapDoorBlock) && !state.getShape(level, pos).isEmpty()) {
            AABB box = state.getShape(level, pos).bounds();
            Direction facing = state.getValue(door ? DoorBlock.FACING : TrapDoorBlock.FACING);
            double hx;
            double hy;
            double hz;
            if (door) {
                Direction side = state.getValue(DoorBlock.HINGE) == DoorHingeSide.LEFT ? facing.getCounterClockWise() : facing.getClockWise();
                hx = 0.5 - 0.5 * facing.getStepX() + 0.5 * side.getStepX();
                hz = 0.5 - 0.5 * facing.getStepZ() + 0.5 * side.getStepZ();
                hy = Double.NaN;
            } else {
                hx = facing.getStepX() == 0 ? Double.NaN : 0.5 - 0.5 * facing.getStepX();
                hz = facing.getStepZ() == 0 ? Double.NaN : 0.5 - 0.5 * facing.getStepZ();
                hy = state.getValue(TrapDoorBlock.HALF) == Half.TOP ? 1.0 : 0.0;
            }

            double[] min = new double[]{box.minX, box.minY, box.minZ};
            double[] max = new double[]{box.maxX, box.maxY, box.maxZ};
            double[] hinge = new double[]{hx, hy, hz};
            double[] eyeAt = new double[]{eye.x - pos.getX(), eye.y - pos.getY(), eye.z - pos.getZ()};
            double[] p = new double[3];
            int thin = 0;

            for (int k = 1; k < 3; k++) {
                if (max[k] - min[k] < max[thin] - min[thin]) {
                    thin = k;
                }
            }

            for (int k = 0; k < 3; k++) {
                if (k == thin) {
                    p[k] = Math.abs(eyeAt[k] - min[k]) < Math.abs(eyeAt[k] - max[k]) ? min[k] - 0.06 : max[k] + 0.06;
                } else if (Double.isNaN(hinge[k])) {
                    p[k] = door ? Math.min(max[k], 0.95) : (min[k] + max[k]) * 0.5;
                } else {
                    boolean far = Math.abs(max[k] - hinge[k]) > Math.abs(min[k] - hinge[k]);
                    p[k] = far ? max[k] - 0.12 : min[k] + 0.12;
                }
            }

            this.x = pos.getX() + p[0];
            this.y = pos.getY() + p[1];
            this.z = pos.getZ() + p[2];
        }
    }

    private static BlockHitResult picked(AbstractClientPlayer player) {
        return player.pick(5.0, Game.partialTick(), false) instanceof BlockHitResult b ? b : null;
    }

    private static boolean placing(AbstractClientPlayer player) {
        return player.isShiftKeyDown() && (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty());
    }

    private static boolean usable(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        return block instanceof DoorBlock
            || block instanceof TrapDoorBlock
            || block instanceof FenceGateBlock
            || block instanceof LeverBlock
            || block instanceof ButtonBlock
            || block instanceof EnderChestBlock
            || block instanceof NoteBlock
            || block instanceof DiodeBlock
            || block instanceof BellBlock
            || state.getMenuProvider(level, pos) != null;
    }
}
