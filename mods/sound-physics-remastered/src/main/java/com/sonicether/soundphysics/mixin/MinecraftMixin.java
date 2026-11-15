package com.sonicether.soundphysics.mixin;

import com.sonicether.soundphysics.utils.LevelAccessUtils;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Shadow
    @Nullable
    public ClientLevel level;
    @Shadow
    @Nullable
    public LocalPlayer player;

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void setLevel(ClientLevel clientLevel, CallbackInfo ci) {
        if (this.level != null) {
            LevelAccessUtils.onUnloadLevel(this.level);
        }

        if (this.player != null) {
            LevelAccessUtils.onLoadLevel(clientLevel);
        }
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V", at = @At("HEAD"))
    private void disconnect(Screen screen, boolean bl, CallbackInfo ci) {
        if (this.level != null) {
            LevelAccessUtils.onUnloadLevel(this.level);
        }
    }
}
