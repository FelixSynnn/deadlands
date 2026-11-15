package vectorwing.farmersdelight.refabricated.mlconfigs.fabric;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import vectorwing.farmersdelight.refabricated.mlconfigs.ConfigBuilder;

public class ConfigSubCategory extends ConfigEntry {
    private final List<ConfigEntry> entries = new ArrayList<>();

    public ConfigSubCategory(String name) {
        super(name);
    }

    public void addEntry(ConfigEntry entry) {
        this.entries.add(entry);
    }

    public List<ConfigEntry> getEntries() {
        return this.entries;
    }

    @Override
    public void loadFromJson(JsonObject object) {
        if (object.has(this.name)) {
            if (object.get(this.name) instanceof JsonObject jo) {
                this.entries.forEach(l -> l.loadFromJson(jo));
            }
        } else {
            ConfigBuilder.LOGGER.warn("Config file had missing category {}", this.name);
        }
    }

    @Override
    public void saveToJson(JsonObject object) {
        JsonObject category = new JsonObject();
        this.entries.forEach(l -> l.saveToJson(category));
        object.add(this.name, category);
    }
}
