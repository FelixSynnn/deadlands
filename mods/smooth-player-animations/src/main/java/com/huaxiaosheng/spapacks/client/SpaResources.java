package com.huaxiaosheng.spapacks.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.huaxiaosheng.spapacks.SpaPacks;
import com.huaxiaosheng.spapacks.anim.graph.AnimGraph;
import com.huaxiaosheng.spapacks.anim.graph.GraphLoader;
import com.huaxiaosheng.spapacks.anim.signal.ItemRules;
import com.huaxiaosheng.spapacks.animation.Clip;
import com.huaxiaosheng.spapacks.animation.ClipLibrary;
import com.huaxiaosheng.spapacks.animation.vanilla.VanillaClip;
import com.huaxiaosheng.spapacks.animation.vanilla.VanillaClipLibrary;
import com.huaxiaosheng.spapacks.platform.Game;
import com.huaxiaosheng.spapacks.platform.Platform;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

final class SpaResources {
    private static final String CLIPS = "spa/bent";
    private static final String VANILLA_CLIPS = "spa/vanilla";
    private static final Identifier GRAPH = Game.id("spa_packs", "spa/graph/player.json");
    private static volatile AnimGraph graph;
    private static final Identifier CAPE = Game.id("spa_packs", "spa/cape.json");
    private static final Identifier LEGACY_ITEMS = Game.id("smoothplayeranimations", "item_animations.json");
    private static volatile ItemRules items = ItemRules.NONE;
    private static final List<String> PENDING = new ArrayList<>();
    private static volatile boolean ready;

    private SpaResources() {
    }

    static boolean ready() {
        return ready;
    }

    static AnimGraph graph() {
        return graph;
    }

    static ItemRules items() {
        return items;
    }

    private static <T> List<T> reversed(List<T> list) {
        List<T> copy = new ArrayList<>(list);
        Collections.reverse(copy);
        return copy;
    }

    private static void loadItems(ResourceManager manager, List<String> problems) {
        List<JsonObject> files = new ArrayList<>();
        List<Resource> stacks = new ArrayList<>();

        for (List<Resource> stack : manager.listResourceStacks("spa", id -> id.getPath().equals("spa/items.json")).values()) {
            stacks.addAll(reversed(stack));
        }

        stacks.addAll(reversed(manager.getResourceStack(LEGACY_ITEMS)));

        for (Resource resource : stacks) {
            try (Reader reader = resource.openAsReader()) {
                files.add(JsonParser.parseReader(reader).getAsJsonObject());
            } catch (Exception ex) {
                problems.add("物品规则（" + resource.sourcePackId() + "）：" + ex.getMessage());
                SpaPacks.LOGGER.warn("[spa] cannot read item rules from pack {}", resource.sourcePackId(), ex);
            }
        }

        List<String> skipped = new ArrayList<>();
        items = ItemRules.of(files, skipped);

        for (String s : skipped) {
            problems.add("物品规则：" + s);
        }
    }

    private static void loadCape(ResourceManager manager, List<String> problems) {
        JsonObject merged = new JsonObject();

        for (Resource resource : manager.getResourceStack(CAPE)) {
            try (Reader reader = resource.openAsReader()) {
                for (Entry<String, JsonElement> e : JsonParser.parseReader(reader).getAsJsonObject().entrySet()) {
                    merged.add(e.getKey(), e.getValue());
                }
            } catch (Exception ex) {
                problems.add("spa/cape.json（" + resource.sourcePackId() + "）：" + ex.getMessage() + "；这个包的披风设置被忽略");
                SpaPacks.LOGGER.warn("[spa] cannot read {} from pack {}", new Object[]{CAPE, resource.sourcePackId(), ex});
            }
        }

        CapeCloth.settings(CapeCloth.Settings.of(merged));
    }

    static void install() {
        Platform.onResourceReload(Game.id("spa_packs", "animations"), manager -> {
            load(manager);
            TextureMasks.clear();
        });
        Platform.onClientTick(client -> {
            if (client.player != null && !PENDING.isEmpty()) {
                synchronized (PENDING) {
                    for (String line : PENDING) {
                        Game.chat(client.player, Component.literal("[SPA] " + line).withStyle(ChatFormatting.YELLOW));
                    }

                    PENDING.clear();
                }
            }
        });
    }

    private static void loadGraph(ResourceManager manager, List<String> problems) {
        JsonObject merged = null;

        for (Resource resource : manager.getResourceStack(GRAPH)) {
            try (Reader reader = resource.openAsReader()) {
                JsonObject layer = JsonParser.parseReader(reader).getAsJsonObject();
                merged = merged == null ? layer : GraphLoader.merge(merged, layer);
            } catch (Exception ex) {
                problems.add("spa/graph/player.json（" + resource.sourcePackId() + "）：" + ex.getMessage() + "；这个包的动画图被忽略");
                SpaPacks.LOGGER.warn("[spa] cannot read {} from pack {}", new Object[]{GRAPH, resource.sourcePackId(), ex});
            }
        }

        if (merged == null) {
            problems.add("找不到动画图 spa/graph/player.json");
        } else {
            try {
                graph = GraphLoader.load(merged);
            } catch (Exception ex) {
                problems.add("动画图：" + ex.getMessage() + (graph != null ? "；继续使用上一份" : ""));
                SpaPacks.LOGGER.warn("[spa] the animation graph does not compile", ex);
            }
        }
    }

    static void load(ResourceManager manager) {
        List<String> problems = new ArrayList<>();
        Map<String, Clip> clips = new HashMap<>();
        Map<Identifier, List<Resource>> found = manager.listResourceStacks("spa/bent", id -> id.getPath().endsWith(".json"));

        for (Entry<Identifier, List<Resource>> e : found.entrySet()) {
            Identifier file = e.getKey();
            String path = file.getPath();
            String id = file.getNamespace() + ":" + path.substring("spa/bent".length() + 1, path.length() - ".json".length());
            List<Resource> stack = e.getValue();

            for (int i = stack.size() - 1; i >= 0; i--) {
                Resource resource = stack.get(i);

                try (Reader reader = resource.openAsReader()) {
                    clips.put(id, Clip.parse(id, reader));
                    break;
                } catch (Exception ex) {
                    problems.add(id + "（" + resource.sourcePackId() + "）：" + ex.getMessage());
                    SpaPacks.LOGGER.warn("[spa] cannot read clip {} from pack {}", new Object[]{id, resource.sourcePackId(), ex});
                }
            }
        }

        ClipLibrary.set(clips);
        Map<String, VanillaClip> vanilla = new HashMap<>();

        for (Entry<Identifier, List<Resource>> e : manager.listResourceStacks("spa/vanilla", id -> id.getPath().endsWith(".json")).entrySet()) {
            Identifier file = e.getKey();
            String prefix = file.getNamespace().equals("spa_packs") ? "spa_packs:" : file.getNamespace() + ":";
            List<Resource> stack = e.getValue();

            for (int i = stack.size() - 1; i >= 0; i--) {
                Resource resource = stack.get(i);

                try (Reader reader = resource.openAsReader()) {
                    for (Entry<String, VanillaClip> a : VanillaClip.parseFile(reader).entrySet()) {
                        vanilla.put(prefix + a.getKey(), a.getValue());
                    }
                    break;
                } catch (Exception ex) {
                    problems.add(file.getPath() + "（" + resource.sourcePackId() + "）：" + ex.getMessage());
                    SpaPacks.LOGGER.warn("[spa] cannot read vanilla clips {} from pack {}", new Object[]{file, resource.sourcePackId(), ex});
                }
            }
        }

        VanillaClipLibrary.set(vanilla);
        loadItems(manager, problems);
        loadGraph(manager, problems);
        loadCape(manager, problems);
        AnimGraph g = graph;
        if (g != null) {
            List<String> missing = new ArrayList<>();

            for (String name : g.clipNames()) {
                if (g.resolve("bent", name, n -> ClipLibrary.get(n) != null) == null) {
                    missing.add(name);
                }
            }

            if (!missing.isEmpty()) {
                SpaPacks.LOGGER.info("[spa] the bent rig has no clip for {} (left to the pose below)", missing);
            }
        }

        ready = ClipLibrary.size() > 0;
        SpaPacks.LOGGER
            .info(
                "[spa] {} bent clips, {} vanilla clips, {} item rules loaded, {} problem(s)",
                new Object[]{clips.size(), vanilla.size(), items.size(), problems.size()}
            );
        synchronized (PENDING) {
            PENDING.clear();
            PENDING.addAll(problems.subList(0, Math.min(problems.size(), 8)));
            if (problems.size() > 8) {
                PENDING.add("……还有 " + (problems.size() - 8) + " 个问题，见日志");
            }
        }
    }
}
