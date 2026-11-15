package vectorwing.farmersdelight.refabricated.mlconfigs.fabric;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.jetbrains.annotations.ApiStatus.Internal;
import vectorwing.farmersdelight.refabricated.mlconfigs.ConfigBuilder;
import vectorwing.farmersdelight.refabricated.mlconfigs.ConfigType;
import vectorwing.farmersdelight.refabricated.mlconfigs.ModConfigHolder;
import vectorwing.farmersdelight.refabricated.mlconfigs.integration.cloth_config.ClothConfigCompat;
import vectorwing.farmersdelight.refabricated.mlconfigs.integration.yacl.YACLCompat;

public final class FabricConfigHolder extends ModConfigHolder {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final ConfigSubCategory mainEntry;
    private final File file;
    private boolean initialized = false;

    @Internal
    public static void loadAllConfigs() {
        for (ModConfigHolder spec : getTrackedSpecs()) {
            spec.forceLoad();
        }
    }

    public FabricConfigHolder(Identifier name, ConfigSubCategory mainEntry, ConfigType type, Runnable changeCallback) {
        super(name, "json", FabricLoader.getInstance().getConfigDir(), type, changeCallback);
        this.file = this.getFullPath().toFile();
        this.mainEntry = mainEntry;
        if (this.isSynced()) {
            ServerPlayConnectionEvents.JOIN.register(this::onPlayerLoggedIn);
        }
    }

    public ConfigSubCategory getMainEntry() {
        return this.mainEntry;
    }

    @Override
    public boolean isLoaded() {
        return this.initialized;
    }

    @Override
    public void forceLoad() {
        if (!this.isLoaded()) {
            try {
                JsonElement config = null;
                if (this.file.exists() && this.file.isFile()) {
                    try (
                        FileInputStream fileInputStream = new FileInputStream(this.file);
                        InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream, StandardCharsets.UTF_8);
                        BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                    ) {
                        config = GSON.fromJson(bufferedReader, JsonElement.class);
                    }
                }

                if (config instanceof JsonObject jo) {
                    this.mainEntry.getEntries().forEach(ex -> ex.loadFromJson(jo));
                }

                if (!this.initialized) {
                    this.initialized = true;
                    this.saveConfig();
                    ConfigBuilder.LOGGER.info("Loaded config {}", this.getFileName());
                }
            } catch (Exception e) {
                throw new ModConfigHolder.ConfigLoadingException(this, e);
            }
        }
    }

    public void saveConfig() {
        try (
            FileOutputStream stream = new FileOutputStream(this.file);
            Writer writer = new OutputStreamWriter(stream, StandardCharsets.UTF_8);
        ) {
            JsonObject jo = new JsonObject();
            jo.addProperty(
                "#README", "This config file does not support comments. To see them configure it in-game using YACL or Cloth Config (or just use Forge)"
            );
            this.mainEntry.getEntries().forEach(ex -> ex.saveToJson(jo));
            GSON.toJson(jo, writer);
        } catch (IOException e) {
            ConfigBuilder.LOGGER.error("Failed to save config {}:", this.getReadableName(), e);
        }

        this.onRefresh();
    }

    @Environment(EnvType.CLIENT)
    @Override
    public Screen makeScreen(Screen parent, Identifier background) {
        if (ConfigBuilder.YACL) {
            return YACLCompat.makeScreen(parent, this, background);
        } else {
            return ConfigBuilder.CLOTH_CONFIG ? ClothConfigCompat.makeScreen(parent, this, background) : null;
        }
    }

    @Override
    public boolean hasConfigScreen() {
        return ConfigBuilder.CLOTH_CONFIG || ConfigBuilder.YACL;
    }

    @Override
    public void loadFromBytes(InputStream stream) {
        InputStreamReader inputStreamReader = new InputStreamReader(stream, StandardCharsets.UTF_8);
        BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
        JsonElement config = GSON.fromJson(bufferedReader, JsonElement.class);
        if (config instanceof JsonObject jo) {
            this.mainEntry.getEntries().forEach(e -> e.loadFromJson(jo));
        }

        this.onRefresh();
    }

    private void onPlayerLoggedIn(ServerGamePacketListenerImpl listener, PacketSender sender, MinecraftServer minecraftServer) {
        this.syncConfigsToPlayer(listener.player);
    }
}
