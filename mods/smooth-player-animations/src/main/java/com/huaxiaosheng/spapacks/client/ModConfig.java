package com.huaxiaosheng.spapacks.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.huaxiaosheng.spapacks.SpaPacks;
import com.huaxiaosheng.spapacks.anim.signal.ItemRules;
import com.huaxiaosheng.spapacks.platform.Platform;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = Platform.configDir().resolve("spa_packs.json");
    private static ModConfig current = new ModConfig();
    private static ItemRules itemRules;
    public boolean enabled = true;
    public boolean cemEnabled = false;
    public boolean faceOverlay = true;
    public boolean externalAnimations = true;
    public boolean capePhysics = true;
    public boolean lanternPhysics = true;
    public boolean footIk = true;
    public boolean handReach = true;
    public List<String> emfShieldOwnedIds = new ArrayList<>();
    public List<String> disabledAnimations = new ArrayList<>();
    public List<String> disabledAnimationsCem = new ArrayList<>();
    public List<String> enabledAnimations = new ArrayList<>();
    public List<String> enabledAnimationsCem = new ArrayList<>();
    public List<ModConfig.ItemAnimation> itemAnimations = new ArrayList<>();
    private static ModConfig.Switches bentSwitches;
    private static ModConfig.Switches cemSwitches;

    public static ItemRules itemRules() {
        if (itemRules == null) {
            JsonArray rules = new JsonArray();

            for (ModConfig.ItemAnimation a : current.itemAnimations) {
                ItemChoice choice = ItemChoice.of(a == null ? null : a.animation);
                if (choice != null && a.item != null && !a.item.isBlank()) {
                    rules.add(choice.rule(a.item.trim()));
                }
            }

            JsonObject file = new JsonObject();
            file.add("rules", rules);
            itemRules = ItemRules.of(List.of(file), new ArrayList<>());
        }

        return itemRules;
    }

    public static ModConfig.Switches switches(boolean cem) {
        if (cem) {
            if (cemSwitches == null) {
                cemSwitches = new ModConfig.Switches(Set.copyOf(current.disabledAnimationsCem), Set.copyOf(current.enabledAnimationsCem));
            }

            return cemSwitches;
        } else {
            if (bentSwitches == null) {
                bentSwitches = new ModConfig.Switches(Set.copyOf(current.disabledAnimations), Set.copyOf(current.enabledAnimations));
            }

            return bentSwitches;
        }
    }

    private static void invalidate() {
        itemRules = null;
        bentSwitches = null;
        cemSwitches = null;
    }

    private void fillMissing() {
        this.emfShieldOwnedIds = orEmpty(this.emfShieldOwnedIds);
        this.disabledAnimations = orEmpty(this.disabledAnimations);
        this.disabledAnimationsCem = orEmpty(this.disabledAnimationsCem);
        this.enabledAnimations = orEmpty(this.enabledAnimations);
        this.enabledAnimationsCem = orEmpty(this.enabledAnimationsCem);
        this.itemAnimations = orEmpty(this.itemAnimations);
        this.disabledAnimations.removeIf(Objects::isNull);
        this.disabledAnimationsCem.removeIf(Objects::isNull);
        this.enabledAnimations.removeIf(Objects::isNull);
        this.enabledAnimationsCem.removeIf(Objects::isNull);
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? new ArrayList<>() : list;
    }

    public static ModConfig get() {
        return current;
    }

    public static boolean on() {
        return current.enabled && !SafeMode.off();
    }

    static void load() {
        if (Files.exists(FILE)) {
            try (Reader reader = Files.newBufferedReader(FILE)) {
                ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
                if (loaded != null) {
                    loaded.fillMissing();
                    current = loaded;
                    invalidate();
                }
            } catch (Exception e) {
                SpaPacks.LOGGER.warn("Cannot read {}; using defaults for this session", FILE, e);
                return;
            }
        }

        save();
    }

    static boolean save() {
        invalidate();

        try {
            Files.createDirectories(FILE.getParent());

            try (Writer writer = Files.newBufferedWriter(FILE)) {
                GSON.toJson(current, writer);
            }

            return true;
        } catch (Exception e) {
            SpaPacks.LOGGER.warn("Cannot write {}", FILE, e);
            return false;
        }
    }

    public static final class ItemAnimation {
        public String item = "";
        public String animation = ItemChoice.SWORD.key;

        ModConfig.ItemAnimation copy() {
            ModConfig.ItemAnimation c = new ModConfig.ItemAnimation();
            c.item = this.item;
            c.animation = this.animation;
            return c;
        }
    }

    public record Switches(Set<String> off, Set<String> on) {
    }
}
