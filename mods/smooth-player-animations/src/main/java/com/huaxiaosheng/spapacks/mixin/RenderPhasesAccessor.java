package com.huaxiaosheng.spapacks.mixin;

import java.util.Map;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderSetup.class)
public interface RenderPhasesAccessor {
    @Accessor("textures")
    Map<String, ?> spaPacks$textures();
}
