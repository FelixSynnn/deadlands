package com.huaxiaosheng.spapacks.client;

import com.google.gson.JsonObject;

public enum ItemChoice {
    PUNCH("swing:punch", "swing", "kind", "punch"),
    SWORD("swing:sword", "swing", "kind", "sword"),
    PICKAXE("swing:pickaxe", "swing", "kind", "pickaxe"),
    AXE("swing:axe", "swing", "kind", "axe"),
    SHOVEL("swing:shovel", "swing", "kind", "shovel"),
    HOE("swing:hoe", "swing", "kind", "hoe"),
    TRIDENT("swing:trident", "swing", "kind", "trident"),
    MACE("swing:mace", "swing", "kind", "mace"),
    INTERACT("swing:interact", "swing", "animation", "interact"),
    SWING_VANILLA("swing:vanilla", "swing", null, null),
    EATING("use:eating", "use", "animation", "eating"),
    DRINKING("use:drinking", "use", "animation", "drinking"),
    BOW("use:bow", "use", "animation", "use_bow"),
    CROSSBOW("use:crossbow", "use", "animation", "use_crossbow"),
    SHIELD("use:shield", "use", "animation", "use_shield"),
    SPYGLASS("use:spyglass", "use", "animation", "use_spyglass"),
    TRIDENT_THROW("use:trident_throw", "use", "animation", "use_trident_throw"),
    HORN("use:horn", "use", "animation", "toot_horn"),
    BRUSH("use:brush", "use", "animation", "brush"),
    USE_VANILLA("use:vanilla", "use", null, null),
    TORCH("hold:torch", "hold", "animation", "hold_light"),
    LANTERN("hold:lantern", "hold", "animation", "hold_lantern"),
    SPEAR("hold:spear", "hold", "animation", "hold_spear"),
    HOLD_VANILLA("hold:vanilla", "hold", null, null),
    DRAW_WEAPON("draw:weapon", "draw", "animation", "draw_weapon"),
    DRAW_ITEM("draw:item", "draw", "animation", "draw_item"),
    DRAW_NONE("draw:none", "draw", null, null);

    public final String key;
    public final String type;
    private final String field;
    private final String value;

    ItemChoice(String key, String type, String field, String value) {
        this.key = key;
        this.type = type;
        this.field = field;
        this.value = value;
    }

    public static ItemChoice of(String key) {
        for (ItemChoice c : values()) {
            if (c.key.equals(key)) {
                return c;
            }
        }

        return null;
    }

    public JsonObject rule(String item) {
        JsonObject o = new JsonObject();
        o.addProperty("item", item);
        o.addProperty("type", this.type);
        if (this.field == null) {
            o.addProperty("enabled", false);
        } else {
            o.addProperty(this.field, this.value);
        }

        return o;
    }
}
