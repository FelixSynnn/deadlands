package vectorwing.farmersdelight.refabricated.mlconfigs.fabric;

import com.google.gson.JsonObject;

public abstract class ConfigEntry {
    protected final String name;

    protected ConfigEntry(String name) {
        this.name = name;
    }

    public abstract void loadFromJson(JsonObject var1);

    public abstract void saveToJson(JsonObject var1);

    public String getName() {
        return this.name;
    }
}
