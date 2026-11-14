package com.sonicether.soundphysics.mixin;

import com.sonicether.soundphysics.SoundPhysicsMod;
import com.sonicether.soundphysics.utils.SoundUtils;
import javax.annotation.Nullable;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Shadow
    public abstract float getEyeHeight();

    @ModifyArg(
        method = "playSound(Lnet/minecraft/sounds/SoundEvent;FF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;playSound(Lnet/minecraft/world/entity/Entity;DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V"
        ),
        index = 2
    )
    private double playSound(@Nullable Entity entity, double x, double y, double z, SoundEvent sound, SoundSource category, float volume, float pitch) {
        if (sound == null) {
            return y;
        } else {
            return !SoundPhysicsMod.CONFIG.enabled.get() ? y : y + SoundUtils.calculateEntitySoundYOffset(this.getEyeHeight(), sound);
        }
    }
}
