package team.creative.enhancedvisuals.mixin;

import java.util.Map;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.ChannelAccess.ChannelHandle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(SoundEngine.class)
public interface SoundEngineAccessor {
    @Accessor("instanceToChannel")
    Map<SoundInstance, ChannelHandle> getInstanceToChannel();

    @Invoker("calculateVolume")
    float invokeCalculateVolume(SoundInstance var1);
}
