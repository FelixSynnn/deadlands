package com.huaxiaosheng.spapacks.anim.signal;

import java.util.Locale;

public enum ClipSignal {
    SWING,
    USE,
    RELEASE,
    HOLD_MAIN,
    HOLD_OFF,
    HOLD_BOTH,
    IDLE_INTRO,
    IDLE_LOOP,
    DRAW;

    public final String key = this.name().toLowerCase(Locale.ROOT);
    public static final int COUNT = values().length;

    public static ClipSignal byKey(String key) {
        for (ClipSignal c : values()) {
            if (c.key.equals(key)) {
                return c;
            }
        }

        return null;
    }
}
