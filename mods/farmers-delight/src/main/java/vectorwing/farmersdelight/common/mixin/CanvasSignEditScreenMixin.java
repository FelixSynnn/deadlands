package vectorwing.farmersdelight.common.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vectorwing.farmersdelight.client.gui.CanvasSignEditScreen;
import vectorwing.farmersdelight.client.gui.HangingCanvasSignEditScreen;
import vectorwing.farmersdelight.common.block.entity.CanvasSignBlockEntity;
import vectorwing.farmersdelight.common.block.entity.HangingCanvasSignBlockEntity;

@Mixin(LocalPlayer.class)
public class CanvasSignEditScreenMixin {
    @Shadow
    @Final
    protected Minecraft minecraft;

    @Inject(at = @At("HEAD"), method = "openTextEdit", cancellable = true)
    private void openCanvasSignEditScreen(SignBlockEntity sign, boolean isFront, CallbackInfo ci) {
        if (sign instanceof CanvasSignBlockEntity) {
            this.minecraft.setScreen(new CanvasSignEditScreen(sign, isFront, this.minecraft.isTextFilteringEnabled()));
            ci.cancel();
        }

        if (sign instanceof HangingCanvasSignBlockEntity) {
            this.minecraft.setScreen(new HangingCanvasSignEditScreen(sign, isFront, this.minecraft.isTextFilteringEnabled()));
            ci.cancel();
        }
    }
}
