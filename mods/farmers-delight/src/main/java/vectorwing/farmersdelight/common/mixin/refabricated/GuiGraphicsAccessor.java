package vectorwing.farmersdelight.common.mixin.refabricated;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.GuiGraphics.ScissorStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GuiGraphics.class)
public interface GuiGraphicsAccessor {
    @Accessor("scissorStack")
    ScissorStack fdrf$getScissorStack();
}
