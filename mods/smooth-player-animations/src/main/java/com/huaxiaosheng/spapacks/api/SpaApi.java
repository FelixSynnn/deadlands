package com.huaxiaosheng.spapacks.api;

import com.huaxiaosheng.spapacks.anim.signal.ExternalVars;
import com.huaxiaosheng.spapacks.animation.Clip;
import com.huaxiaosheng.spapacks.animation.ClipLibrary;
import com.huaxiaosheng.spapacks.client.AnchorTracker;
import com.huaxiaosheng.spapacks.client.PlayerAnimations;
import com.huaxiaosheng.spapacks.rig.Anchors;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;

public final class SpaApi {
    public static final int VERSION = 2;
    public static final List<String> ANCHORS = Anchors.IDS;
    public static final float[] ITEM_TIP = Anchors.tip();
    public static final float[] ITEM_GRIP = Anchors.grip();

    private SpaApi() {
    }

    private static void onMainThread() {
        if (!Minecraft.getInstance().isSameThread()) {
            throw new IllegalStateException("SpaApi is client-side and main-thread only; called from " + Thread.currentThread().getName());
        }
    }

    public static void set(AbstractClientPlayer player, String name, float value) {
        onMainThread();
        PlayerAnimations.external(player).set(name, value);
    }

    public static float get(AbstractClientPlayer player, String name) {
        onMainThread();
        return PlayerAnimations.external(player).get(ExternalVars.check(name));
    }

    public static void fire(AbstractClientPlayer player, String name) {
        onMainThread();
        PlayerAnimations.external(player).fire(name);
    }

    public static void clear(AbstractClientPlayer player, String prefix) {
        onMainThread();
        PlayerAnimations.external(player).clear(prefix);
    }

    public static boolean bentRigEnabled() {
        return PlayerAnimations.bentRigEnabled();
    }

    public static boolean onBentRig(AbstractClientPlayer player) {
        return PlayerAnimations.supported(player);
    }

    public static double clipSeconds(String clip) {
        Clip c = ClipLibrary.get(clip);
        return c == null ? -1.0 : c.seconds();
    }

    public static boolean anchor(AbstractClientPlayer player, String anchor, double[] position, float[] rotation) {
        onMainThread();
        return AnchorTracker.anchor(player, anchor, position, rotation);
    }
}
