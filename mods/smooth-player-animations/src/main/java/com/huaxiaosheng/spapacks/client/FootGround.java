package com.huaxiaosheng.spapacks.client;

import com.huaxiaosheng.spapacks.animation.FootIk;
import com.huaxiaosheng.spapacks.platform.Game;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

final class FootGround implements FootIk.Ground {
    private final MutableBlockPos pos = new MutableBlockPos();
    private Level level;
    private double x;
    private double y;
    private double z;

    boolean at(LivingEntity player) {
        float pt = Game.partialTick();
        this.level = player.level();
        this.x = Mth.lerp(pt, player.xo, player.getX());
        this.y = Mth.lerp(pt, player.yo, player.getY());
        this.z = Mth.lerp(pt, player.zo, player.getZ());
        Pose pose = player.getPose();
        return player.onGround()
            && !player.isPassenger()
            && !player.isSwimming()
            && !player.isFallFlying()
            && !(player instanceof Player p && p.getAbilities().flying)
            && (pose == Pose.STANDING || pose == Pose.CROUCHING);
    }

    @Override
    public float below(double dx, double dz) {
        double wx = this.x + dx;
        double wz = this.z + dz;
        double top = this.y + 0.55F;
        double bottom = this.y - 0.55F;
        int bx = Mth.floor(wx);
        int bz = Mth.floor(wz);
        double lx = wx - bx;
        double lz = wz - bz;

        for (int by = Mth.floor(top); by >= Mth.floor(bottom) - 1; by--) {
            this.pos.set(bx, by, bz);
            VoxelShape shape = this.level.getBlockState(this.pos).getCollisionShape(this.level, this.pos);
            if (!shape.isEmpty()) {
                double best = Double.NaN;

                for (AABB box : shape.toAabbs()) {
                    if (lx >= box.minX && lx <= box.maxX && lz >= box.minZ && lz <= box.maxZ) {
                        double h = by + box.maxY;
                        if (h <= top + 1.0E-4 && (Double.isNaN(best) || h > best)) {
                            best = h;
                        }
                    }
                }

                if (!Double.isNaN(best)) {
                    return best < bottom ? Float.NaN : (float)(best - this.y);
                }
            }
        }

        return Float.NaN;
    }
}
