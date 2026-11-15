package com.sonicether.soundphysics.mixin;

import com.sonicether.soundphysics.SoundPhysicsMod;
import net.minecraft.sounds.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(SoundEvent.class)
public class SoundEventMixin {
    @ModifyConstant(method = "getRange", constant = @Constant(floatValue = 16.0F), expect = 2)
    private float allowance1(float value) {
        return !SoundPhysicsMod.CONFIG.enabled.get() ? value : value * SoundPhysicsMod.CONFIG.soundDistanceAllowance.get();
    }
}
