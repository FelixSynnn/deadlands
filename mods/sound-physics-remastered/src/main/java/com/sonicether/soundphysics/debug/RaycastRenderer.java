package com.sonicether.soundphysics.debug;

import com.sonicether.soundphysics.SoundPhysicsMod;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer.SimpleDebugRenderer;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.phys.Vec3;

public class RaycastRenderer implements SimpleDebugRenderer {
    private static final List<RaycastRenderer.Ray> rays = Collections.synchronizedList(new ArrayList<>());
    private static final Minecraft mc = Minecraft.getInstance();

    @Override
    public void emitGizmos(double d, double e, double f, DebugValueAccess debugValueAccess, Frustum frustum, float g) {
        if (mc.level != null) {
            if (!SoundPhysicsMod.CONFIG.renderSoundBounces.get() && !SoundPhysicsMod.CONFIG.renderOcclusion.get()) {
                synchronized (rays) {
                    rays.clear();
                }
            } else {
                long gameTime = mc.level.getGameTime();
                synchronized (rays) {
                    rays.removeIf(rayx -> gameTime - rayx.tickCreated > rayx.lifespan || gameTime - rayx.tickCreated < 0L);

                    for (RaycastRenderer.Ray ray : rays) {
                        GizmoProperties line = Gizmos.line(ray.start, ray.end, ray.color);
                        line.persistForMillis(ray.lifespan * 50);
                        line.fadeOut();
                        if (ray.throughWalls) {
                            line.setAlwaysOnTop();
                        }
                    }
                }
            }
        }
    }

    public static int color(ChatFormatting color) {
        Integer colorValue = color.getColor();
        return colorValue == null ? -16777216 : colorValue | 0xFF000000;
    }

    public static void addSoundBounceRay(Vec3 start, Vec3 end, int color) {
        if (SoundPhysicsMod.CONFIG.renderSoundBounces.get()) {
            addRay(start, end, color, false);
        }
    }

    public static void addOcclusionRay(Vec3 start, Vec3 end, int color) {
        if (SoundPhysicsMod.CONFIG.renderOcclusion.get()) {
            addRay(start, end, color, true);
        }
    }

    private static void addRay(Vec3 start, Vec3 end, int color, boolean throughWalls) {
        if (!(mc.player.position().distanceTo(start) > 32.0) || !(mc.player.position().distanceTo(end) > 32.0)) {
            synchronized (rays) {
                rays.add(new RaycastRenderer.Ray(start, end, color, throughWalls));
            }
        }
    }

    private static class Ray {
        private final Vec3 start;
        private final Vec3 end;
        private final int color;
        private final long tickCreated;
        private final int lifespan;
        private final boolean throughWalls;

        public Ray(Vec3 start, Vec3 end, int color, boolean throughWalls) {
            this.start = start;
            this.end = end;
            this.color = color;
            this.throughWalls = throughWalls;
            this.tickCreated = RaycastRenderer.mc.level.getGameTime();
            this.lifespan = 40;
        }
    }
}
