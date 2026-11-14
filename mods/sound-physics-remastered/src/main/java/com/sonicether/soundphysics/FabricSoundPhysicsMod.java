package com.sonicether.soundphysics;

import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public class FabricSoundPhysicsMod extends SoundPhysicsMod implements ModInitializer, ClientModInitializer {
    public void onInitialize() {
        this.init();
    }

    public void onInitializeClient() {
        this.initClient();
    }

    @Override
    public Path getConfigFolder() {
        return FabricLoader.getInstance().getConfigDir();
    }
}
