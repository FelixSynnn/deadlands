package com.sonicether.soundphysics.utils;

import java.util.regex.Pattern;
import net.minecraft.sounds.SoundEvent;

public class SoundUtils {
    private static final Pattern STEP_PATTERN = Pattern.compile(".*step.*");

    public static double calculateEntitySoundYOffset(float standingEyeHeight, SoundEvent sound) {
        return STEP_PATTERN.matcher(sound.location().getPath()).matches() ? 0.0 : standingEyeHeight;
    }
}
