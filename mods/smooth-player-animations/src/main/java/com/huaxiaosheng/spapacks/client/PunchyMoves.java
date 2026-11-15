package com.huaxiaosheng.spapacks.client;

import com.huaxiaosheng.spapacks.SpaPacks;
import com.huaxiaosheng.spapacks.platform.Platform;
import java.lang.reflect.Method;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;

public final class PunchyMoves {
    private static final boolean PRESENT = Platform.isModLoaded("punchy_moves");
    private static boolean told;
    private static boolean resolved;
    private static Method root;

    private PunchyMoves() {
    }

    static boolean drives() {
        if (PRESENT && PlayerAnimations.cemSide()) {
            if (!told) {
                told = true;
                SpaPacks.LOGGER.info("Punchy Moves found; with the CEM engine on its animations are the player's base");
            }

            return true;
        } else {
            return false;
        }
    }

    public static boolean stepsAside() {
        return PRESENT && PlayerAnimations.bentRigEnabled();
    }

    public static boolean backIsOurs() {
        return PRESENT && ModConfig.on();
    }

    static void restRoot(Object model) {
        if (PRESENT) {
            if (!resolved) {
                resolved = true;

                try {
                    root = Class.forName("punchymoves.client.render.PlayerModelRoot", false, PunchyMoves.class.getClassLoader())
                        .getMethod("root", PlayerModel.class);
                } catch (ReflectiveOperationException | LinkageError e) {
                    SpaPacks.LOGGER.warn("Punchy Moves found but its model root differs; it may still move the bent rig as a whole", e);
                }
            }

            if (root != null) {
                try {
                    if (root.invoke(null, model) instanceof ModelPart part) {
                        part.resetPose();
                    }
                } catch (ReflectiveOperationException | RuntimeException e) {
                    root = null;
                    SpaPacks.LOGGER.warn("Punchy Moves' model root could not be reset; it may still move the bent rig as a whole", e);
                }
            }
        }
    }
}
