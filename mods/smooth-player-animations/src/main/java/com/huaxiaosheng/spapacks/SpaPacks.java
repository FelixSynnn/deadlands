package com.huaxiaosheng.spapacks;

import com.huaxiaosheng.spapacks.client.PlayerAnimations;
import com.huaxiaosheng.spapacks.client.SafeMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SpaPacks {
    public static final String MOD_ID = "spa_packs";
    public static final Logger LOGGER = LoggerFactory.getLogger("SmoothPlayerAnimations");

    private SpaPacks() {
    }

    public static void init() {
        SafeMode.run("startup", PlayerAnimations::install);
    }
}
