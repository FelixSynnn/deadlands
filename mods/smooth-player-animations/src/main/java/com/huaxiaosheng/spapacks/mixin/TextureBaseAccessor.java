package com.huaxiaosheng.spapacks.mixin;

import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net/minecraft/client/renderer/rendertype/RenderSetup$TextureBinding")
public interface TextureBaseAccessor {
    @Invoker("location")
    Identifier spaPacks$location();
}
