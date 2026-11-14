package com.huaxiaosheng.spapacks.client;

import com.huaxiaosheng.spapacks.SpaPacks;
import java.lang.reflect.Method;

final class ShadowPass {
    private static boolean resolved;
    private static Object api;
    private static Method rendering;

    private ShadowPass() {
    }

    static boolean now() {
        if (!resolved) {
            resolved = true;

            try {
                Class<?> type = Class.forName("net.irisshaders.iris.api.v0.IrisApi", false, ShadowPass.class.getClassLoader());
                api = type.getMethod("getInstance").invoke(null);
                rendering = type.getMethod("isRenderingShadowPass");
            } catch (ClassNotFoundException absent) {
                rendering = null;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException incompatible) {
                rendering = null;
                SpaPacks.LOGGER.warn("Iris found but its API differs; the cape may move oddly with a shader pack's shadows", incompatible);
            }
        }

        if (rendering == null) {
            return false;
        }

        try {
            return (Boolean)rendering.invoke(api);
        } catch (ReflectiveOperationException | RuntimeException e) {
            rendering = null;
            SpaPacks.LOGGER.warn("Iris's shadow pass could not be asked about; the cape may move oddly with a shader pack's shadows", e);
            return false;
        }
    }
}
