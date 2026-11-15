package com.huaxiaosheng.spapacks.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.huaxiaosheng.spapacks.animation.PartRetarget;
import com.huaxiaosheng.spapacks.platform.Game;
import com.huaxiaosheng.spapacks.platform.Platform;
import com.huaxiaosheng.spapacks.platform.Vertices;
import com.huaxiaosheng.spapacks.rig.BackField;
import com.huaxiaosheng.spapacks.rig.CapeSim;
import com.huaxiaosheng.spapacks.rig.RigSurface;
import com.huaxiaosheng.spapacks.rig.RigidPalette;
import com.huaxiaosheng.spapacks.rig.SkinnedMesh;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class CapeCloth {
    private static final float BACK = 2.0F;
    private static final float SKIN = 0.25F;
    private static final float CLEAR = 0.1F;
    private static final float[] SHIFT = new float[]{0.0F, -0.85F, 1.1F};
    private static final float[] NO_SHIFT = new float[]{0.0F, 0.0F, 0.0F};
    private static final int DRAWN_ROWS = 4;
    private static final int FORGET = 10;
    private static final double CARRIED = 0.4;
    private static final double FOLLOW = 0.06;
    private static final float POSE_FELT = 1.0F;
    private static final float BODY_REACH_CEM = 0.4F;
    private static final float BODY_HOLD_CEM = 0.2F;
    private static final Vector3f DOWN = new Vector3f(0.0F, 1.0F, 0.0F);
    private static final double TRACE_RANGE = 32.0;
    private static final Matrix4f WORLD = new Matrix4f();
    private static final Matrix4f UNDO = new Matrix4f();
    private static final Matrix4f CHEST = new Matrix4f();
    private static final Matrix4f INVERSE = new Matrix4f();
    private static final Matrix4f CHANGE = new Matrix4f();
    private static final Matrix4f THEN = new Matrix4f();
    private static final Matrix4f POSE = new Matrix4f();
    private static final Matrix4f BLEND = new Matrix4f();
    private static final Vector3f GRAVITY = new Vector3f();
    private static final boolean YIELD = Platform.isModLoaded("waveycapes");
    private static volatile CapeCloth.Settings settings = CapeCloth.Settings.DEFAULT;
    private static final Map<AbstractClientPlayer, CapeCloth.Sim> SIMS = new WeakHashMap<>();
    private static final float PLATE = 1.0F;
    private static final float LEGGINGS = 0.5F;
    private static final float BRIDGE = 0.08F;
    private static final boolean[] LIES_ON = new boolean[]{false, true, false, false, true, true};
    private static final float[] EVERYWHERE = new float[]{
        -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE
    };
    private static final RigidPalette RIGID = new RigidPalette(SkinnedMesh.ALEX);
    private static final Map<ModelPart, CapeCloth.Part> PARTS = new WeakHashMap<>();
    private static final Map<ModelPart, Boolean> BODIES = new WeakHashMap<>();
    private static final PoseStack SCRATCH = new PoseStack();

    private CapeCloth() {
    }

    static void settings(CapeCloth.Settings next) {
        settings = next == null ? CapeCloth.Settings.DEFAULT : next;
    }

    private static boolean on() {
        ModConfig config = ModConfig.get();
        return !YIELD && ModConfig.on() && config.capePhysics;
    }

    private static float armor(AbstractClientPlayer player, EquipmentSlot slot, float thickness) {
        return player.getItemBySlot(slot).isEmpty() ? 0.0F : thickness;
    }

    public static void layer(Object state, boolean crouching, PoseStack matrices) {
        RigPlayerModel.Frame frame = RigPlayerModel.frameFor(state);
        if (frame != null) {
            frame.capeLaid = true;
            frame.capeCrouching = crouching;
            frame.capeShifted = false;
            chest(frame.capeWorld.set(matrices.last().pose()), crouching);
        }
    }

    public static void shifted(Object state) {
        RigPlayerModel.Frame frame = RigPlayerModel.frameFor(state);
        if (frame != null && frame.capeLaid) {
            frame.capeShifted = true;
        }
    }

    static boolean owns(ModelPart part) {
        return !BODIES.isEmpty() && BODIES.containsKey(part) && on();
    }

    public static void posed(ModelPart cape, ModelPart body, Object state) {
        CapeCloth.Part part = PARTS.get(cape);
        if (part == null) {
            part = new CapeCloth.Part(body);
            PARTS.put(cape, part);
            BODIES.put(body, Boolean.TRUE);
            RigRoots.register(cape, part);
        }

        part.state = state;
    }

    private static boolean drawPart(CapeCloth.Part part, PoseStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
        Object state = part.state;
        RigPlayerModel.Frame frame = state == null ? null : RigPlayerModel.frameOf(state);
        if (on() && frame != null && frame.capeLaid && posed(frame) && frame.player != null) {
            SCRATCH.setIdentity();
            part.body.translateAndRotate(SCRATCH);
            Matrix4f undo = UNDO.set(SCRATCH.last().pose()).invert();
            if (frame.capeShifted) {
                undo.translate(-SHIFT[0] / 16.0F, -SHIFT[1] / 16.0F, -SHIFT[2] / 16.0F);
            }

            chest(undo, frame.capeCrouching);
            matrices.pushPose();

            try {
                Game.multiply(matrices, undo);
                draw(frame.player, frame, frame.capeShifted ? SHIFT : NO_SHIFT, frame.capeWorld, matrices, vertices, light, overlay, color);
            } finally {
                matrices.popPose();
            }

            return true;
        } else {
            return false;
        }
    }

    private static boolean posed(RigPlayerModel.Frame frame) {
        return frame.custom || frame.vanillaPosed;
    }

    private static Matrix4f chest(Matrix4f back, boolean crouching) {
        return crouching ? back.translate(0.0F, 0.2F, 0.0F).rotateX(0.5F) : back;
    }

    private static void draw(
        AbstractClientPlayer player,
        RigPlayerModel.Frame frame,
        float[] shift,
        Matrix4f world,
        PoseStack matrices,
        VertexConsumer vertices,
        int light,
        int overlay,
        int color
    ) {
        CapeCloth.Settings s = settings;
        CapeCloth.Sim sim = SIMS.get(player);
        if (sim == null || sim.settings != s) {
            sim = new CapeCloth.Sim(s);
            SIMS.put(player, sim);
        }

        sim.anchor[0] = shift[0];
        sim.anchor[1] = shift[1];
        sim.anchor[2] = 2.0F + s.thickness() / 2.0F + 0.1F + Math.max(0.25F + shift[2], armor(player, EquipmentSlot.CHEST, 1.0F));
        Matrix4f chest = CHEST.set(world).scale(0.0625F);
        boolean world3d = inWorld(player, chest);
        boolean near = chest.m30() * chest.m30() + chest.m31() * chest.m31() + chest.m32() * chest.m32() < 1024.0;
        BackField body = !s.collide() || !near && world3d ? null : field(sim, player, frame);
        if (!ShadowPass.now() && world3d) {
            sim.cape.bodyHold(frame.custom ? 0.0F : 0.2F);
            sim.cape.bodyReach(frame.custom ? 0.0F : 0.4F);
            advance(sim, player, chest, body);
        } else if (!sim.cape.placed()) {
            sim.cape.hang(sim.anchor, DOWN);
        }

        sim.cape.shown(1.0F, sim.anchor, sim.points);
        if (body != null) {
            sim.cape.lay(sim.points, body);
            if (!ShadowPass.now() && world3d) {
                sim.cape.keep(sim.points);
            }
        }

        matrices.pushPose();

        try {
            matrices.scale(0.0625F, 0.0625F, 0.0625F);
            emit(sim, matrices.last(), vertices, light, overlay, color);
        } finally {
            matrices.popPose();
        }
    }

    private static boolean inWorld(AbstractClientPlayer player, Matrix4f chest) {
        Vec3 camera = Game.cameraPosition(Minecraft.getInstance());
        float pt = Game.partialTick();
        double dx = chest.m30() - (Mth.lerp(pt, player.xo, player.getX()) - camera.x);
        double dy = chest.m31() - (Mth.lerp(pt, player.yo, player.getY()) - camera.y);
        double dz = chest.m32() - (Mth.lerp(pt, player.zo, player.getZ()) - camera.z);
        double reach = 3.0 + player.getBbHeight() * 2.0;
        return dx * dx + dy * dy + dz * dz < reach * reach;
    }

    private static Matrix4f entity(Matrix4f out, double x, double y, double z, float yaw) {
        return out.translation((float)x, (float)y, (float)z)
            .rotateY((float)Math.toRadians(180.0F - yaw))
            .scale(-1.0F, -1.0F, 1.0F)
            .scale(0.9375F)
            .translate(0.0F, -1.501F, 0.0F);
    }

    private static void advance(CapeCloth.Sim sim, AbstractClientPlayer player, Matrix4f chest, BackField body) {
        CapeCloth.Settings s = sim.settings;
        float pt = Game.partialTick();
        double time = player.tickCount + pt;
        double x = Mth.lerp(pt, player.xo, player.getX());
        double y = Mth.lerp(pt, player.yo, player.getY());
        double z = Mth.lerp(pt, player.zo, player.getZ());
        float yaw = Mth.rotLerp(pt, player.yBodyRotO, player.yBodyRot);
        boolean water = player.isInWater();
        Vector3f gravity = INVERSE.set(chest).invert().transformDirection(0.0F, -s.gravity() * (water ? s.waterGravity() : 1.0F), 0.0F, GRAVITY);
        if (sim.cape.placed() && !(time < sim.time - 1.0) && !(time - sim.time > 10.0)) {
            if (!(time > sim.time)) {
                return;
            }

            float dt = (float)((time - sim.time) * 0.05F);
            double dy = y - sim.lastY;
            double carried = player.onGround() && sim.onGround ? Double.POSITIVE_INFINITY : 0.4 * dt / 0.05F;
            dy -= Mth.clamp(dy, -carried, carried);
            double ease = 1.0 - Math.exp(-dt / 0.06);
            sim.speedX = sim.speedX + ((x - sim.lastX) / dt - sim.speedX) * ease;
            sim.speedY = sim.speedY + (dy / dt - sim.speedY) * ease;
            sim.speedZ = sim.speedZ + ((z - sim.lastZ) / dt - sim.speedZ) * ease;
            sim.turn = sim.turn + (float)((Mth.wrapDegrees(yaw - sim.lastYaw) / dt - sim.turn) * ease);
            Vec3 camera = Game.cameraPosition(Minecraft.getInstance());
            Matrix4f pose = entity(POSE, x - camera.x, y - camera.y, z - camera.z, yaw).invert().mul(chest);
            Matrix4f now = entity(CHANGE, 0.0, 0.0, 0.0, yaw).mul(pose);
            float drop = PlayerAnimations.hipDrop(player);
            sim.lastPose.translateLocal(0.0F, -(drop - sim.lastDrop) / 0.9375F, 0.0F);
            sim.lastDrop = drop;
            Matrix4f then = entity(THEN, -sim.speedX * dt, -sim.speedY * dt, -sim.speedZ * dt, yaw - sim.turn * dt)
                .mul(CapeSim.blend(pose, sim.lastPose, 1.0F, BLEND));
            sim.cape.step(now.invert().mul(then), dt, sim.anchor, gravity, water ? s.waterAir() : 1.0F, body);
            sim.lastPose.set(pose);
        } else {
            sim.cape.hang(sim.anchor, gravity);
            sim.speedX = (player.getX() - player.xo) / 0.05F;
            sim.speedY = 0.0;
            sim.speedZ = (player.getZ() - player.zo) / 0.05F;
            sim.turn = 0.0F;
            Vec3 camera = Game.cameraPosition(Minecraft.getInstance());
            entity(sim.lastPose, x - camera.x, y - camera.y, z - camera.z, yaw).invert().mul(chest);
        }

        sim.time = time;
        sim.lastX = x;
        sim.lastY = y;
        sim.onGround = player.onGround();
        sim.lastZ = z;
        sim.lastYaw = yaw;
    }

    private static BackField field(CapeCloth.Sim sim, AbstractClientPlayer player, RigPlayerModel.Frame frame) {
        float chestplate = armor(player, EquipmentSlot.CHEST, 1.0F);
        float leggings = armor(player, EquipmentSlot.LEGS, 0.5F);
        float legs = Math.max(leggings, armor(player, EquipmentSlot.FEET, 1.0F));
        float[] grow = sim.grow;
        grow[0] = 0.0F;
        grow[1] = Math.max(chestplate, leggings);
        grow[2] = 0.0F;
        grow[3] = 0.0F;
        grow[4] = legs;
        grow[5] = legs;

        for (int i = 0; i < 6; i++) {
            if (!LIES_ON[i]) {
                grow[i] = Float.NaN;
            }
        }

        if (!frame.custom) {
            RIGID.pose(frame.cemParts, PartRetarget.PIVOT, frame.palette, frame.back);
        }

        sim.body.sample(frame.palette);
        BackField field = sim.field;
        field.clear();
        field.margin(sim.settings.thickness() / 2.0F + 0.1F);
        field.add(SkinnedMesh.ALEX, sim.body, BackField.toChest(frame.back, sim.toChest), grow, EVERYWHERE);
        float least = field.least(sim.anchor[0], sim.anchor[1] + 1.0F, sim.anchor[1] + 1.0F, sim.settings.cape().width() / 2.0F);
        if (!Float.isNaN(least) && sim.anchor[2] < least) {
            sim.anchor[2] = least;
        }

        if (!frame.custom) {
            field.bridge(sim.anchor[0], sim.settings.cape().width() / 2.0F, sim.anchor[1], sim.anchor[2], sim.settings.cape().height(), 0.08F);
        }

        return field;
    }

    private static void emit(CapeCloth.Sim sim, Pose pose, VertexConsumer vertices, int light, int overlay, int color) {
        CapeSim.Params p = sim.cape.params();
        int rows = p.segments() * 4 + 1;
        float[] points = sim.drawn;
        float[] outer = sim.outer;
        float[] inner = sim.inner;
        float[] normals = sim.normals;
        curve(sim.points, p.segments() + 1, points);
        Vector3f across = sim.cape.across();
        float half = sim.settings.thickness() / 2.0F;
        float w = p.width() / 2.0F;
        float[] grid = sim.grid;

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < 2; c++) {
                int g = (r * 2 + c) * 3;
                int k = r * 3;
                float side = c == 0 ? -w : w;
                grid[g] = points[k] + across.x * side;
                grid[g + 1] = points[k + 1] + across.y * side;
                grid[g + 2] = points[k + 2] + across.z * side;
            }
        }

        for (int r = 0; r < rows; r++) {
            int l = r * 6;
            int up = Math.max(0, r - 1) * 6;
            int dn = Math.min(rows - 1, r + 1) * 6;
            float ax = grid[l + 3] - grid[l];
            float ay = grid[l + 4] - grid[l + 1];
            float az = grid[l + 5] - grid[l + 2];
            float bx = (grid[dn] + grid[dn + 3] - grid[up] - grid[up + 3]) / 2.0F;
            float by = (grid[dn + 1] + grid[dn + 4] - grid[up + 1] - grid[up + 4]) / 2.0F;
            float bz = (grid[dn + 2] + grid[dn + 5] - grid[up + 2] - grid[up + 5]) / 2.0F;
            float nx = ay * bz - az * by;
            float ny = az * bx - ax * bz;
            float nz = ax * by - ay * bx;
            float len = (float)Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len < 1.0E-6F) {
                nx = 0.0F;
                ny = 0.0F;
                nz = 1.0F;
            } else {
                nx /= len;
                ny /= len;
                nz /= len;
            }

            for (int c = 0; c < 2; c++) {
                int g = l + c * 3;
                normals[g] = nx;
                normals[g + 1] = ny;
                normals[g + 2] = nz;
                outer[g] = grid[g] + nx * half;
                outer[g + 1] = grid[g + 1] + ny * half;
                outer[g + 2] = grid[g + 2] + nz * half;
                inner[g] = grid[g] - nx * half;
                inner[g + 1] = grid[g + 1] - ny * half;
                inner[g + 2] = grid[g + 2] - nz * half;
            }
        }

        for (int r = 0; r + 1 < rows; r++) {
            float v0 = 1.0F + 16.0F * r / (rows - 1);
            float v1 = 1.0F + 16.0F * (r + 1) / (rows - 1);
            int a = r * 2;
            int b = r * 2 + 1;
            int d = r * 2 + 3;
            int e = r * 2 + 2;
            vertex(vertices, pose, outer, a, 11.0F, v0, normals, 1.0F, light, overlay, color);
            vertex(vertices, pose, outer, b, 1.0F, v0, normals, 1.0F, light, overlay, color);
            vertex(vertices, pose, outer, d, 1.0F, v1, normals, 1.0F, light, overlay, color);
            vertex(vertices, pose, outer, e, 11.0F, v1, normals, 1.0F, light, overlay, color);
            vertex(vertices, pose, inner, a, 12.0F, v0, normals, -1.0F, light, overlay, color);
            vertex(vertices, pose, inner, e, 12.0F, v1, normals, -1.0F, light, overlay, color);
            vertex(vertices, pose, inner, d, 22.0F, v1, normals, -1.0F, light, overlay, color);
            vertex(vertices, pose, inner, b, 22.0F, v0, normals, -1.0F, light, overlay, color);
        }

        if (!(half <= 0.0F)) {
            int last = (rows - 1) * 2;
            edge(vertices, pose, inner, 0, 11.0F, 0.0F, inner, 1, 1.0F, 0.0F, outer, 1, 1.0F, 1.0F, outer, 0, 11.0F, 1.0F, light, overlay, color);
            edge(
                vertices,
                pose,
                outer,
                last,
                21.0F,
                1.0F,
                outer,
                last + 1,
                11.0F,
                1.0F,
                inner,
                last + 1,
                11.0F,
                0.0F,
                inner,
                last,
                21.0F,
                0.0F,
                light,
                overlay,
                color
            );

            for (int r = 0; r + 1 < rows; r++) {
                float v0 = 1.0F + 16.0F * r / (rows - 1);
                float v1 = 1.0F + 16.0F * (r + 1) / (rows - 1);
                int right0 = r * 2 + 1;
                int right1 = r * 2 + 3;
                int left0 = r * 2;
                int left1 = r * 2 + 2;
                edge(vertices, pose, outer, right0, 1.0F, v0, inner, right0, 0.0F, v0, inner, right1, 0.0F, v1, outer, right1, 1.0F, v1, light, overlay, color);
                edge(vertices, pose, outer, left0, 11.0F, v0, outer, left1, 11.0F, v1, inner, left1, 12.0F, v1, inner, left0, 12.0F, v0, light, overlay, color);
            }
        }
    }

    private static void curve(float[] points, int count, float[] out) {
        for (int s = 0; s + 1 < count; s++) {
            int k0 = Math.max(0, s - 1) * 3;
            int k1 = s * 3;
            int k2 = (s + 1) * 3;
            int k3 = Math.min(count - 1, s + 2) * 3;

            for (int i = 0; i < 4; i++) {
                float t = i / 4.0F;
                float t2 = t * t;
                float t3 = t2 * t;
                float w0 = -0.5F * t3 + t2 - 0.5F * t;
                float w1 = 1.5F * t3 - 2.5F * t2 + 1.0F;
                float w2 = -1.5F * t3 + 2.0F * t2 + 0.5F * t;
                float w3 = 0.5F * t3 - 0.5F * t2;
                int o = (s * 4 + i) * 3;

                for (int a = 0; a < 3; a++) {
                    out[o + a] = points[k0 + a] * w0 + points[k1 + a] * w1 + points[k2 + a] * w2 + points[k3 + a] * w3;
                }
            }
        }

        int last = (count - 1) * 3;
        int o = (count - 1) * 4 * 3;
        out[o] = points[last];
        out[o + 1] = points[last + 1];
        out[o + 2] = points[last + 2];
    }

    private static void vertex(
        VertexConsumer vertices, Pose pose, float[] at, int i, float u, float v, float[] normals, float sign, int light, int overlay, int color
    ) {
        int k = i * 3;
        Vertices.put(
            vertices,
            pose,
            at[k],
            at[k + 1],
            at[k + 2],
            color,
            u / 64.0F,
            v / 32.0F,
            overlay,
            light,
            normals[k] * sign,
            normals[k + 1] * sign,
            normals[k + 2] * sign
        );
    }

    private static void edge(
        VertexConsumer vertices,
        Pose pose,
        float[] a,
        int ia,
        float ua,
        float va,
        float[] b,
        int ib,
        float ub,
        float vb,
        float[] c,
        int ic,
        float uc,
        float vc,
        float[] d,
        int id,
        float ud,
        float vd,
        int light,
        int overlay,
        int color
    ) {
        ia *= 3;
        ib *= 3;
        ic *= 3;
        id *= 3;
        float ex = c[ic] - a[ia];
        float ey = c[ic + 1] - a[ia + 1];
        float ez = c[ic + 2] - a[ia + 2];
        float fx = d[id] - b[ib];
        float fy = d[id + 1] - b[ib + 1];
        float fz = d[id + 2] - b[ib + 2];
        float nx = ey * fz - ez * fy;
        float ny = ez * fx - ex * fz;
        float nz = ex * fy - ey * fx;
        float len = (float)Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (!(len < 1.0E-6F)) {
            nx /= len;
            ny /= len;
            nz /= len;
            corner(vertices, pose, a, ia, ua, va, nx, ny, nz, light, overlay, color);
            corner(vertices, pose, b, ib, ub, vb, nx, ny, nz, light, overlay, color);
            corner(vertices, pose, c, ic, uc, vc, nx, ny, nz, light, overlay, color);
            corner(vertices, pose, d, id, ud, vd, nx, ny, nz, light, overlay, color);
        }
    }

    private static void corner(
        VertexConsumer vertices, Pose pose, float[] at, int k, float u, float v, float nx, float ny, float nz, int light, int overlay, int color
    ) {
        Vertices.put(vertices, pose, at[k], at[k + 1], at[k + 2], color, u / 64.0F, v / 32.0F, overlay, light, nx, ny, nz);
    }

    private static final class Part implements RigRoots.Drawn {
        final ModelPart body;
        Object state;

        Part(ModelPart body) {
            this.body = body;
        }

        @Override
        public boolean drawRig(PoseStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
            return CapeCloth.drawPart(this, matrices, vertices, light, overlay, color);
        }
    }

    public record Settings(CapeSim.Params cape, float thickness, float gravity, float waterGravity, float waterAir, boolean collide) {
        public static final CapeCloth.Settings DEFAULT = new CapeCloth.Settings(
            new CapeSim.Params(10.0F, 16.0F, 16, 1.0F, 2.2F, 6, 20.0F, 95.0F, 6.0F, 0.0F, 1.0F, 0.06F, 6), 1.0F, 24.0F, 0.3F, 4.0F, true
        );

        public static CapeCloth.Settings of(JsonObject json) {
            CapeCloth.Settings d = DEFAULT;
            CapeSim.Params p = d.cape();
            CapeSim.Params params = new CapeSim.Params(
                number(json, "width", p.width()),
                number(json, "height", p.height()),
                Math.round(number(json, "segments", p.segments())),
                number(json, "drag", p.drag()),
                number(json, "lift", p.lift()),
                Math.round(number(json, "iterations", p.iterations())),
                number(json, "max_bend", p.maxBend()),
                number(json, "max_lift", p.maxLift()),
                number(json, "sway", p.sway()),
                number(json, "damping", p.damping()),
                number(json, "stiffness", p.stiffness()),
                number(json, "smoothing", p.smoothing()),
                Math.round(number(json, "steps_per_second", p.substeps() / 0.05F) * 0.05F)
            );
            return new CapeCloth.Settings(
                params,
                Math.max(0.0F, number(json, "thickness", d.thickness())),
                number(json, "gravity", d.gravity()),
                Math.max(0.0F, number(json, "water_gravity", d.waterGravity())),
                Math.max(0.0F, number(json, "water_drag", d.waterAir())),
                bool(json, "collide", d.collide())
            );
        }

        private static float number(JsonObject json, String key, float fallback) {
            JsonElement e = json.get(key);
            if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
                float f = e.getAsFloat();
                return Float.isFinite(f) ? f : fallback;
            } else {
                return fallback;
            }
        }

        private static boolean bool(JsonObject json, String key, boolean fallback) {
            JsonElement e = json.get(key);
            return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() ? e.getAsBoolean() : fallback;
        }
    }

    private static final class Sim {
        final CapeCloth.Settings settings;
        final CapeSim cape;
        final BackField field = new BackField();
        final RigSurface body = new RigSurface(SkinnedMesh.ALEX, false, true, 0.0F);
        final float[] grow = new float[6];
        final Matrix4f toChest = new Matrix4f();
        double time;
        double lastX;
        double lastY;
        boolean onGround;
        float lastDrop;
        double lastZ;
        float lastYaw;
        final Matrix4f lastPose = new Matrix4f();
        double speedX;
        double speedY;
        double speedZ;
        float turn;
        final float[] anchor = new float[3];
        final float[] points;
        final float[] grid;
        final float[] outer;
        final float[] inner;
        final float[] normals;
        final float[] drawn;

        Sim(CapeCloth.Settings settings) {
            this.settings = settings;
            this.cape = new CapeSim(settings.cape());
            this.points = new float[(settings.cape().segments() + 1) * 3];
            int rows = settings.cape().segments() * 4 + 1;
            this.drawn = new float[rows * 3];
            this.grid = new float[rows * 2 * 3];
            this.outer = new float[rows * 2 * 3];
            this.inner = new float[this.outer.length];
            this.normals = new float[this.outer.length];
        }
    }
}
