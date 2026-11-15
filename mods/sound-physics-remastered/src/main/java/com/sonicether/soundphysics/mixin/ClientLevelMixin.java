package com.sonicether.soundphysics.mixin;

import com.sonicether.soundphysics.utils.LevelAccessUtils;
import com.sonicether.soundphysics.utils.SoundRateManager;
import com.sonicether.soundphysics.world.CachingClientLevel;
import com.sonicether.soundphysics.world.ClonedClientLevel;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin implements CachingClientLevel {
    @Unique
    private final AtomicReference<ClonedClientLevel> cachedClone = new AtomicReference<>();

    @Unique
    @Nullable
    @Override
    public ClonedClientLevel sound_physics_remastered$getCachedClone() {
        return this.cachedClone.get();
    }

    @Unique
    @Override
    public void sound_physics_remastered$setCachedClone(@Nullable ClonedClientLevel clonedClientLevel) {
        this.cachedClone.set(clonedClientLevel);
    }

    @Inject(method = "tick(Ljava/util/function/BooleanSupplier;)V", at = @At("TAIL"))
    private void tick(BooleanSupplier booleanSupplier, CallbackInfo ci) {
        LevelAccessUtils.tickLevelCache((ClientLevel)(Object)this);
        SoundRateManager.onClientTick((ClientLevel)(Object)this);
    }
}
