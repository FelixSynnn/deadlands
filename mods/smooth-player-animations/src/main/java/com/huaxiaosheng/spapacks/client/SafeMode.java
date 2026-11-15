package com.huaxiaosheng.spapacks.client;

import com.huaxiaosheng.spapacks.SpaPacks;
import com.huaxiaosheng.spapacks.platform.Game;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class SafeMode {
    private static volatile boolean off;
    private static String where;
    private static boolean told;

    private SafeMode() {
    }

    public static boolean off() {
        return off;
    }

    public static void fail(String where, Throwable error) {
        if (error instanceof OutOfMemoryError oom) {
            throw oom;
        } else if (!off) {
            off = true;
            SafeMode.where = where;
            SpaPacks.LOGGER
                .error(
                    "SmoothPlayerAnimations failed in {} and turned itself off until the game restarts; players are drawn as vanilla. Please report this log: https://github.com/jayku5249-dot/SmoothPlayerAnimations/issues",
                    where,
                    error
                );

            try {
                RigBend.end();
            } catch (Throwable var3) {
            }
        }
    }

    public static void run(String where, Runnable body) {
        try {
            body.run();
        } catch (Throwable t) {
            fail(where, t);
        }
    }

    public static void tick(Minecraft client) {
        if (off && !told && client.player != null) {
            told = true;
            Game.chat(client.player, Component.translatable("spa_packs.safe_mode", where).withStyle(ChatFormatting.RED));
        }
    }
}
