package com.huaxiaosheng.spapacks.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "software/bernie/geckolib/renderer/GeoArmorRenderer", remap = false)
abstract class GeoArmorRendererMixin {
}
