package com.sonicether.soundphysics.world;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;

public class ClonedLevelHeightAccessor implements LevelHeightAccessor {
    private final int height;
    private final int getMinY;

    public ClonedLevelHeightAccessor(Level level) {
        this.height = level.getHeight();
        this.getMinY = level.getMinY();
    }

    @Override
    public int getHeight() {
        return this.height;
    }

    @Override
    public int getMinY() {
        return this.getMinY;
    }

    public int getMinBuildHeight() {
        return this.getMinY;
    }
}
