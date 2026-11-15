package com.huaxiaosheng.spapacks.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "com/illusivesoulworks/elytraslot/client/ElytraSlotLayer", remap = false)
abstract class ElytraSlotMixin {
}
