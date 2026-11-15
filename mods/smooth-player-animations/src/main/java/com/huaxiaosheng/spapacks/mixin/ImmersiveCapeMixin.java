package com.huaxiaosheng.spapacks.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "immersive_armors/client/render/entity/piece/CapePiece", remap = false)
abstract class ImmersiveCapeMixin {
}
