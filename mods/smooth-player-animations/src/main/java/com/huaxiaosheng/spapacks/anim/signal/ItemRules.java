package com.huaxiaosheng.spapacks.anim.signal;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.huaxiaosheng.spapacks.util.Json;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ItemRules {
    public static final String VANILLA = "@vanilla_arms";
    private final List<ItemRules.Rule> rules;
    private final Map<String, ItemRules.Rule[]> byItem = new HashMap<>();
    private static final ItemRules.Type[] TYPES = ItemRules.Type.values();
    public static final ItemRules NONE = new ItemRules(List.of());

    private ItemRules(List<ItemRules.Rule> rules) {
        this.rules = rules;
    }

    public int size() {
        return this.rules.size();
    }

    public static ItemRules of(List<JsonObject> files, List<String> problems) {
        List<ItemRules.Rule> rules = new ArrayList<>();

        for (JsonObject file : files) {
            JsonArray list = file.has("rules") && file.get("rules").isJsonArray() ? file.getAsJsonArray("rules") : null;
            if (list != null) {
                for (JsonElement e : list) {
                    ItemRules.Rule r = e.isJsonObject() ? parse(e.getAsJsonObject()) : null;
                    if (r == null) {
                        problems.add("items: skipped " + e);
                    } else {
                        rules.add(r);
                    }
                }
            }
        }

        return new ItemRules(List.copyOf(rules));
    }

    public ItemRules.Rule match(String id, ItemRules.Type type) {
        if (id != null && !id.isEmpty() && !this.rules.isEmpty()) {
            ItemRules.Rule[] found = this.byItem.get(id);
            if (found == null) {
                found = new ItemRules.Rule[TYPES.length];

                for (ItemRules.Type t : TYPES) {
                    found[t.ordinal()] = this.find(id, t);
                }

                this.byItem.put(id, found);
            }

            return found[type.ordinal()];
        } else {
            return null;
        }
    }

    private ItemRules.Rule find(String id, ItemRules.Type type) {
        int colon = id.indexOf(58);
        String namespace = colon < 0 ? "minecraft" : id.substring(0, colon);
        ItemRules.Rule best = null;
        int bestScore = -1;

        for (ItemRules.Rule r : this.rules) {
            if (r.type == type) {
                int score = r.score(id, namespace);
                if (score > bestScore) {
                    best = r;
                    bestScore = score;
                }
            }
        }

        return best;
    }

    static ItemRules.Rule parse(JsonObject o) {
        String item = string(o, "item");
        if (item != null && !item.isBlank()) {
            item = item.trim();
            ItemRules.Match match;
            String spec;
            if (item.startsWith("*")) {
                match = ItemRules.Match.SUFFIX;
                spec = item.substring(1);
            } else if (!item.contains(":")) {
                match = ItemRules.Match.NAMESPACE;
                spec = item;
            } else if (item.endsWith(":")) {
                match = ItemRules.Match.NAMESPACE;
                spec = item.substring(0, item.length() - 1);
            } else if (item.endsWith("*")) {
                match = ItemRules.Match.PREFIX;
                spec = item.substring(0, item.length() - 1);
            } else if (item.endsWith("_")) {
                match = ItemRules.Match.PREFIX;
                spec = item;
            } else {
                match = ItemRules.Match.EXACT;
                spec = item;
            }
            ItemRules.Type type = switch (String.valueOf(string(o, "type")).toLowerCase(Locale.ROOT)) {
                case "hold" -> ItemRules.Type.HOLD;
                case "use", "charge" -> ItemRules.Type.USE;
                case "idle" -> ItemRules.Type.IDLE;
                case "draw" -> ItemRules.Type.DRAW;
                default -> ItemRules.Type.SWING;
            };
            String animation = clipName(string(o, "animation"));
            String kind = type == ItemRules.Type.SWING ? string(o, "kind") : null;
            if (kind != null && !kind.isBlank()) {
                String k = kind.trim().toLowerCase(Locale.ROOT);
                animation = k.equals("punch") ? "punch" : "tool_" + k + "_swing";
            }

            boolean enabled = !o.has("enabled") || !o.get("enabled").isJsonPrimitive() || o.get("enabled").getAsBoolean();
            return new ItemRules.Rule(
                spec, match, type, animation == null ? "punch" : animation, enabled, clipName(string(o, "both")), clipName(string(o, "intro"))
            );
        } else {
            return null;
        }
    }

    private static String clipName(String s) {
        if (s != null && !s.isBlank()) {
            s = s.trim();
            return !s.startsWith("smoothplayeranimations:") && !s.startsWith("spa_packs:") ? s : s.substring(s.indexOf(58) + 1);
        } else {
            return null;
        }
    }

    private static String string(JsonObject o, String key) {
        return Json.string(o, key);
    }

    enum Match {
        EXACT,
        PREFIX,
        SUFFIX,
        NAMESPACE;
    }

    public record Rule(String spec, ItemRules.Match match, ItemRules.Type type, String animation, boolean enabled, String both, String intro) {
        int score(String id, String namespace) {
            return switch (this.match) {
                case EXACT -> this.spec.equals(id) ? 1000000 : -1;
                case PREFIX -> id.startsWith(this.spec) ? 1000 + this.spec.length() : -1;
                case SUFFIX -> id.endsWith(this.spec) ? 1000 + this.spec.length() : -1;
                case NAMESPACE -> namespace.equals(this.spec) ? 1 : -1;
            };
        }

        public String clip() {
            return this.enabled ? this.animation : "@vanilla_arms";
        }
    }

    public enum Type {
        SWING,
        HOLD,
        USE,
        IDLE,
        DRAW;
    }
}
