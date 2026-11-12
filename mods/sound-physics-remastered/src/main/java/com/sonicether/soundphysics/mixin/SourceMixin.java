package com.sonicether.soundphysics.mixin;

import com.mojang.blaze3d.audio.Channel;
import com.sonicether.soundphysics.Loggers;
import com.sonicether.soundphysics.SoundPhysics;
import com.sonicether.soundphysics.SoundPhysicsMod;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Channel.class)
public class SourceMixin {
    @Shadow
    @Final
    private int source;
    @Unique
    private Vec3 pos;

    @Inject(method = "setSelfPosition", at = @At("HEAD"))
    private void setSelfPosition(Vec3 poss, CallbackInfo ci) {
        this.pos = poss;
    }

    @Inject(method = "play", at = @At("HEAD"))
    private void play(CallbackInfo ci) {
        if (this.pos != null) {
            SoundPhysics.onPlaySound(this.pos.x, this.pos.y, this.pos.z, this.source);
            Loggers.logALError("Sound play injector");
        }
    }

    @ModifyVariable(method = "linearAttenuation", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private float injected(float attenuation) {
        return !SoundPhysicsMod.CONFIG.enabled.get() ? attenuation : attenuation / SoundPhysicsMod.CONFIG.attenuationFactor.get();
    }

    @Inject(method = "linearAttenuation", at = @At("RETURN"))
    private void linearAttenuation2(float attenuation, CallbackInfo ci) {
        SoundPhysics.applyReferenceDistanceWorkaround(this.source, attenuation);
    }
}
