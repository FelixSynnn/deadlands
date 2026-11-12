package com.sonicether.soundphysics.config.blocksound;

import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public abstract class BlockDefinition implements Comparable<BlockDefinition> {
    public abstract String getConfigString();

    @Nullable
    public abstract String getConfigComment();

    public abstract Component getName();

    public int compareTo(@NotNull BlockDefinition o) {
        return this.getConfigString().compareTo(o.getConfigString());
    }
}
