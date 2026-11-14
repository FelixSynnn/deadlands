package com.huaxiaosheng.spapacks.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.huaxiaosheng.spapacks.SpaPacks;
import com.huaxiaosheng.spapacks.platform.Platform;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class EmfShield {
    private static final boolean PRESENT = Platform.isModLoaded("entity_model_features");
    private static final Path LEGACY_CONFIG = Platform.configDir().resolve("smoothplayeranimations.json");
    private static boolean done;
    private static int attempts;

    private EmfShield() {
    }

    static void install() {
        if (PRESENT) {
            cleanUp();
            Platform.onClientTick(client -> cleanUp());
        }
    }

    static synchronized void cleanUp() {
        if (PRESENT && !done && attempts <= 200) {
            attempts++;

            try {
                Object handler = Class.forName("traben.entity_model_features.EMF").getMethod("config").invoke(null);
                if (handler == null) {
                    return;
                }

                Object cfg = handler.getClass().getMethod("getConfig").invoke(handler);
                if (cfg == null) {
                    return;
                }

                Field field = cfg.getClass().getField("modelsNamesDisabled");
                Set<String> disabled = (Set<String>)field.get(cfg);
                done = true;
                Set<String> leftovers = new LinkedHashSet<>(ModConfig.get().emfShieldOwnedIds);
                List<String> legacy = legacyOwned();
                leftovers.addAll(legacy);
                if (leftovers.isEmpty()) {
                    return;
                }

                List<String> given = new ArrayList<>();

                for (String id : leftovers) {
                    if (disabled.remove(id)) {
                        given.add(id);
                    }
                }

                if (!given.isEmpty()) {
                    handler.getClass().getMethod("saveToFile").invoke(handler);
                }

                ModConfig.get().emfShieldOwnedIds = new ArrayList<>();
                ModConfig.save();
                if (!legacy.isEmpty()) {
                    clearLegacyOwned();
                }

                SpaPacks.LOGGER.info("[cem] Entity Model Features: gave back the player models earlier versions had left disabled in its config: {}", given);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                done = true;
                SpaPacks.LOGGER.warn("[cem] cannot read Entity Model Features' disabled models", e);
            }
        }
    }

    private static List<String> legacyOwned() {
        List<String> ids = new ArrayList<>();
        JsonObject root = readLegacy();
        if (root != null) {
            JsonElement var3 = root.get("emfShieldOwnedIds");
            if (var3 instanceof JsonArray) {
                for (JsonElement e : (JsonArray)var3) {
                    if (e.isJsonPrimitive()) {
                        ids.add(e.getAsString());
                    }
                }
            }
        }

        return ids;
    }

    private static void clearLegacyOwned() {
        JsonObject root = readLegacy();
        if (root != null) {
            root.add("emfShieldOwnedIds", new JsonArray());

            try {
                Files.writeString(LEGACY_CONFIG, new GsonBuilder().setPrettyPrinting().create().toJson(root), StandardCharsets.UTF_8);
            } catch (Exception e) {
                SpaPacks.LOGGER.warn("[cem] cannot clear the EMF records in {}", LEGACY_CONFIG, e);
            }
        }
    }

    private static JsonObject readLegacy() {
        if (!Platform.isModLoaded("smoothplayeranimations") && Files.isRegularFile(LEGACY_CONFIG)) {
            try {
                JsonElement e = JsonParser.parseString(Files.readString(LEGACY_CONFIG, StandardCharsets.UTF_8));
                return e.isJsonObject() ? e.getAsJsonObject() : null;
            } catch (Exception e) {
                return null;
            }
        } else {
            return null;
        }
    }
}
