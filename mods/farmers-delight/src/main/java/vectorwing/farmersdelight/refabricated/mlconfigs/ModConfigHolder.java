package vectorwing.farmersdelight.refabricated.mlconfigs;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

public abstract class ModConfigHolder {
    private static final Map<Identifier, ModConfigHolder> CONFIG_STORAGE = new ConcurrentHashMap<>();
    private final Identifier configId;
    private final String fileName;
    private final Component readableName;
    private final Path filePath;
    private final ConfigType type;
    private final @Nullable Runnable changeCallback;

    public static void addTrackedSpec(ModConfigHolder spec) {
        ModConfigHolder old = CONFIG_STORAGE.put(spec.getId(), spec);
        if (old != null) {
            throw new IllegalStateException("Duplicate config type for with id " + spec.getId());
        }
    }

    public static Collection<ModConfigHolder> getTrackedSpecs() {
        return CONFIG_STORAGE.values();
    }

    public static @Nullable ModConfigHolder getConfigSpec(Identifier configId) {
        return CONFIG_STORAGE.get(configId);
    }

    protected ModConfigHolder(Identifier id, String fileExtension, Path configDirectory, ConfigType type, @Nullable Runnable changeCallback) {
        this.configId = id;
        this.fileName = id.getNamespace() + "-" + id.getPath() + "." + fileExtension;
        this.filePath = configDirectory.resolve(this.fileName);
        this.type = type;
        this.changeCallback = changeCallback;
        this.readableName = Component.literal(getReadableName(id.toDebugFileName() + "_configs"));
        addTrackedSpec(this);
    }

    public static String getReadableName(String name) {
        return Arrays.stream(name.replace(":", "_").split("_")).<CharSequence>map(StringUtils::capitalize).collect(Collectors.joining(" "));
    }

    public Component getReadableName() {
        return this.readableName;
    }

    protected void onRefresh() {
        if (this.changeCallback != null) {
            this.changeCallback.run();
        }
    }

    public boolean isLoaded() {
        return true;
    }

    public abstract void forceLoad();

    public ConfigType getConfigType() {
        return this.type;
    }

    public String getModId() {
        return this.configId.getNamespace();
    }

    public Identifier getId() {
        return this.configId;
    }

    public boolean isSynced() {
        return this.type.isSynced();
    }

    public String getFileName() {
        return this.fileName;
    }

    public Path getFullPath() {
        return this.filePath;
    }

    public abstract void loadFromBytes(InputStream var1);

    @Environment(EnvType.CLIENT)
    public @Nullable Screen makeScreen(Screen parent) {
        return this.makeScreen(parent, null);
    }

    @Environment(EnvType.CLIENT)
    public abstract @Nullable Screen makeScreen(Screen var1, @Nullable Identifier var2);

    public abstract boolean hasConfigScreen();

    public void syncConfigsToPlayer(ServerPlayer player) {
    }

    public void sendSyncedConfigsToAllPlayers() {
    }

    public static class ConfigLoadingException extends RuntimeException {
        public ConfigLoadingException(ModConfigHolder config, Exception cause) {
            super(
                "Failed to load config file "
                    + config.getFileName()
                    + " of type "
                    + config.getConfigType()
                    + " for mod "
                    + config.getModId()
                    + ". Try deleting it",
                cause
            );
        }
    }
}
