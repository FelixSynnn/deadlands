package vectorwing.farmersdelight.common.mixin.refabricated;

import net.minecraft.client.gui.screens.recipebook.GhostSlots;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GhostSlots.class)
public interface GhostSlotsInvoker {
    @Invoker("setInput")
    void fdrf$setInput(Slot var1, ContextMap var2, SlotDisplay var3);

    @Invoker("setResult")
    void fdrf$setResult(Slot var1, ContextMap var2, SlotDisplay var3);
}
