package com.sonicether.soundphysics.config.blocksound;

import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public class BlockTagDefinition extends BlockDefinition {
    private final TagKey<Block> blockTag;

    public BlockTagDefinition(TagKey<Block> blockTag) {
        this.blockTag = blockTag;
    }

    @Override
    public String getConfigString() {
        return "#%s".formatted(this.blockTag.location());
    }

    @Nullable
    @Override
    public String getConfigComment() {
        return this.getName().getString();
    }

    @Override
    public Component getName() {
        return Component.literal(this.getConfigString()).append(Component.literal(" (Block Tag)"));
    }

    public TagKey<Block> getBlockTag() {
        return this.blockTag;
    }

    @Nullable
    public static BlockTagDefinition fromConfigString(String configString) {
        if (!configString.startsWith("#")) {
            return null;
        }

        String id = configString.substring(1).trim();
        Identifier resourceLocation = Identifier.tryParse(id);
        return resourceLocation == null ? null : new BlockTagDefinition(TagKey.create(Registries.BLOCK, resourceLocation));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o != null && this.getClass() == o.getClass()) {
            BlockTagDefinition that = (BlockTagDefinition)o;
            return Objects.equals(this.blockTag, that.blockTag);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return this.blockTag != null ? this.blockTag.hashCode() : 0;
    }
}
