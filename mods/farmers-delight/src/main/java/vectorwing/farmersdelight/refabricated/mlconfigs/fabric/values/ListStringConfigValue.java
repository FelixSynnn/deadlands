package vectorwing.farmersdelight.refabricated.mlconfigs.fabric.values;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import vectorwing.farmersdelight.refabricated.mlconfigs.ConfigBuilder;

public class ListStringConfigValue<T extends String> extends ConfigValue<List<String>> {
    private final Predicate<Object> predicate;

    public ListStringConfigValue(String name, List<String> defaultValue, Predicate<Object> validator) {
        super(name, defaultValue);
        this.predicate = validator;
    }

    public boolean isValid(List<String> value) {
        return true;
    }

    @Override
    public void loadFromJson(JsonObject element) {
        if (element.has(this.name)) {
            try {
                if (element.get(this.name) instanceof JsonArray ja) {
                    this.value = new ArrayList<>();

                    for (JsonElement v : ja) {
                        T s = (T)v.getAsString();
                        if (this.predicate.test(s)) {
                            this.value.add(s);
                        }
                    }
                }

                if (this.isValid(this.value)) {
                    return;
                }

                this.value = this.defaultValue;
            } catch (Exception var7) {
            }

            ConfigBuilder.LOGGER.warn("Config file had incorrect entry {}, correcting", this.name);
        } else {
            ConfigBuilder.LOGGER.warn("Config file had missing entry {}", this.name);
        }
    }

    @Override
    public void saveToJson(JsonObject object) {
        if (this.value == null) {
            this.value = this.defaultValue;
        }

        JsonArray ja = new JsonArray();
        this.value.forEach(ja::add);
        object.add(this.name, ja);
    }
}
