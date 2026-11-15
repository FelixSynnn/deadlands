package com.sonicether.soundphysics.config.blocksound;

import com.sonicether.soundphysics.config.SoundTypes;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.SoundType;

public class BlockSoundTypeDefinition extends BlockDefinition {
    private final SoundType soundType;

    public BlockSoundTypeDefinition(SoundType soundType) {
        this.soundType = soundType;
    }

    @Override
    public String getConfigString() {
        return SoundTypes.getName(this.soundType);
    }

    @Nullable
    @Override
    public String getConfigComment() {
        return this.getName().getString();
    }

    @Override
    public Component getName() {
        return SoundTypes.getNameComponent(this.soundType).append(Component.literal(" (Sound Type)"));
    }

    public SoundType getSoundType() {
        return this.soundType;
    }

    @Nullable
    public static BlockSoundTypeDefinition fromConfigString(String configString) {
        SoundType soundType = SoundTypes.getSoundType(configString);
        return soundType == null ? null : new BlockSoundTypeDefinition(soundType);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o != null && this.getClass() == o.getClass()) {
            BlockSoundTypeDefinition that = (BlockSoundTypeDefinition)o;
            return Objects.equals(this.soundType, that.soundType);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return this.soundType != null ? this.soundType.hashCode() : 0;
    }
}
