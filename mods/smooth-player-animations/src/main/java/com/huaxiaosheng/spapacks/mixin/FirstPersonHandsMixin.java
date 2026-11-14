package com.huaxiaosheng.spapacks.mixin;

import com.huaxiaosheng.spapacks.client.RigBend;
import com.huaxiaosheng.spapacks.client.SafeMode;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
abstract class FirstPersonHandsMixin {
    @Inject(method = "renderItemInHand", at = @At("HEAD"))
    private void spaPacks$noBending(CallbackInfo ci) {
        SafeMode.run("first-person hands", RigBend::end);
    }
}
