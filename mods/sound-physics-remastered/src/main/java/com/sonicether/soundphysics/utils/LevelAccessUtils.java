package com.sonicether.soundphysics.utils;

import com.sonicether.soundphysics.Loggers;
import com.sonicether.soundphysics.SoundPhysicsMod;
import com.sonicether.soundphysics.profiling.TaskProfiler;
import com.sonicether.soundphysics.world.CachingClientLevel;
import com.sonicether.soundphysics.world.ClientLevelProxy;
import com.sonicether.soundphysics.world.ClonedClientLevel;
import com.sonicether.soundphysics.world.UnsafeClientLevel;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public class LevelAccessUtils {
    private static final TaskProfiler PROFILER = new TaskProfiler("Level Caching");
    private static final Minecraft MC = Minecraft.getInstance();

    public static void onLoadLevel(ClientLevel clientLevel) {
        Loggers.logDebug("Creating initial level cache");
        updateLevelCache(clientLevel, levelOriginFromPlayer(), clientLevel.getGameTime());
        SoundRateManager.onLoadLevel(clientLevel);
    }

    public static void onUnloadLevel(ClientLevel clientLevel) {
        Loggers.logDebug("Removing level cache due to level unload");
        ((CachingClientLevel)clientLevel).sound_physics_remastered$setCachedClone(null);
        SoundRateManager.onUnloadLevel(clientLevel);
    }

    public static void tickLevelCache(ClientLevel clientLevel) {
        if (SoundPhysicsMod.CONFIG.enabled.get()) {
            if (!SoundPhysicsMod.CONFIG.unsafeLevelAccess.get()) {
                long currentTick = clientLevel.getGameTime();
                BlockPos origin = levelOriginFromPlayer();
                CachingClientLevel cachingClientLevel = (CachingClientLevel)clientLevel;
                ClonedClientLevel clientLevelClone = cachingClientLevel.sound_physics_remastered$getCachedClone();
                if (clientLevelClone == null) {
                    Loggers.logDebug("Creating new level cache, no existing level clone found in client cache.");
                    updateLevelCache(clientLevel, origin, SoundPhysicsMod.CONFIG.levelCloneMaxRetainTicks.get().intValue());
                } else {
                    long ticksSinceLastClone = currentTick - clientLevelClone.getTick();
                    double distanceSinceLastClone = origin.distSqr(clientLevelClone.getOrigin());
                    if (ticksSinceLastClone >= SoundPhysicsMod.CONFIG.levelCloneMaxRetainTicks.get().intValue()
                        || distanceSinceLastClone >= SoundPhysicsMod.CONFIG.levelCloneMaxRetainBlockDistance.get().intValue()) {
                        Loggers.logDebug(
                            "Updating level cache, cache expired ({}/{} ticks) or player moved too far ({}/{} block(s)) from last clone origin.",
                            ticksSinceLastClone,
                            SoundPhysicsMod.CONFIG.levelCloneMaxRetainTicks.get(),
                            distanceSinceLastClone,
                            SoundPhysicsMod.CONFIG.levelCloneMaxRetainBlockDistance.get()
                        );
                        updateLevelCache(clientLevel, origin, currentTick);
                    }
                }
            }
        }
    }

    private static void updateLevelCache(ClientLevel clientLevel, BlockPos origin, long tick) {
        Loggers.logDebug("Updating level cache, creating new level clone with origin {} on tick {}.", origin.toShortString(), tick);
        TaskProfiler.TaskProfilerHandle profile = PROFILER.profile();
        CachingClientLevel cachingClientLevel = (CachingClientLevel)clientLevel;
        ClonedClientLevel clientLevelClone = new ClonedClientLevel(clientLevel, origin, tick, SoundPhysicsMod.CONFIG.levelCloneRange.get());
        cachingClientLevel.sound_physics_remastered$setCachedClone(clientLevelClone);
        profile.finish();
        Loggers.logProfiling("Updated client level clone in cache in {} ms", profile.getDuration());
        PROFILER.onTally(PROFILER::logResults);
    }

    @Nullable
    public static ClientLevelProxy getClientLevelProxy(Minecraft client) {
        ClientLevel clientLevel = client.level;
        if (clientLevel == null) {
            Loggers.warn("Can not return client level proxy, client level does not exist.");
            return null;
        } else if (SoundPhysicsMod.CONFIG.unsafeLevelAccess.get()) {
            return new UnsafeClientLevel(clientLevel);
        } else {
            CachingClientLevel cachingClientLevel = (CachingClientLevel)clientLevel;
            ClonedClientLevel clientLevelClone = cachingClientLevel.sound_physics_remastered$getCachedClone();
            if (clientLevelClone == null) {
                Loggers.warn("Can not return client level proxy, client level clone has not been cached. This might only occur once on load.");
                return null;
            } else {
                return clientLevelClone;
            }
        }
    }

    private static BlockPos levelOriginFromPlayer() {
        Vec3 playerPos = MC.player.position();
        return BlockPos.containing(playerPos);
    }
}
