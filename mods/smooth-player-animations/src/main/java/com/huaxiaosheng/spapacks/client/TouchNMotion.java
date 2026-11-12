package com.huaxiaosheng.spapacks.client;

import com.huaxiaosheng.spapacks.SpaPacks;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;

final class TouchNMotion {
    private static boolean resolved;
    private static Method applyAll;
    private static Method recordAnimated;

    private TouchNMotion() {
    }

    static boolean present() {
        if (!resolved) {
            resolved = true;

            try {
                ClassLoader loader = TouchNMotion.class.getClassLoader();
                Class<?> hook = Class.forName("strm.touchnmotion.TouchNMotionHook", false, loader);
                Method apply = hook.getDeclaredMethod("applyAll", UUID.class, Function.class, boolean.class, boolean.class);
                apply.setAccessible(true);
                applyAll = apply;

                try {
                    Class<?> feet = Class.forName("strm.touchnmotion.footgrounding.compat.FootGrounding", false, loader);
                    recordAnimated = feet.getMethod("recordAnimated", UUID.class, Function.class);
                } catch (ReflectiveOperationException | LinkageError absent) {
                    recordAnimated = null;
                }

                SpaPacks.LOGGER.info("Touch'n Motion found; it poses the player on our CEM engine's pose");
            } catch (ClassNotFoundException absent) {
                applyAll = null;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException incompatible) {
                applyAll = null;
                SpaPacks.LOGGER.warn("Touch'n Motion found but its hook differs; its poses are not shown on the CEM engine", incompatible);
            }
        }

        return applyAll != null;
    }

    static boolean pose(AbstractClientPlayer player, Function<String, ModelPart> parts) {
        if (!present()) {
            return false;
        }

        try {
            UUID id = player.getUUID();
            if (recordAnimated != null) {
                recordAnimated.invoke(null, id, parts);
            }

            applyAll.invoke(null, id, parts, true, true);
            return true;
        } catch (InvocationTargetException e) {
            applyAll = null;
            SpaPacks.LOGGER.warn("Touch'n Motion failed posing the player; its poses are no longer shown on the CEM engine", e.getCause());
            return false;
        } catch (ReflectiveOperationException | RuntimeException e) {
            applyAll = null;
            SpaPacks.LOGGER.warn("Touch'n Motion could not be asked to pose the player; its poses are no longer shown on the CEM engine", e);
            return false;
        }
    }
}
