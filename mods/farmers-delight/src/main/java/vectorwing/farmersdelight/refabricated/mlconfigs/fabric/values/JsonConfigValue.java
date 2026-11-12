package vectorwing.farmersdelight.refabricated.mlconfigs.fabric.values;

import com.google.common.base.Suppliers;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.function.Supplier;
import vectorwing.farmersdelight.refabricated.mlconfigs.ConfigBuilder;

public class JsonConfigValue extends ConfigValue<JsonElement> {
    private final com.google.common.base.Supplier<JsonElement> defValue;

    public JsonConfigValue(String name, Supplier<JsonElement> defaultSupplier) {
        super(name, null);
        this.defValue = Suppliers.memoize(defaultSupplier::get);
    }

    public boolean isValid(JsonElement value) {
        return value.isJsonObject();
    }

    @Override
    public void loadFromJson(JsonObject element) {
        if (element.has(this.name)) {
            try {
                this.value = element.get(this.name);
                if (this.isValid(this.value)) {
                    return;
                }

                this.value = this.getDefaultValue();
            } catch (Exception var3) {
            }

            ConfigBuilder.LOGGER.warn("Config file had incorrect entry {}, correcting", this.name);
        } else {
            ConfigBuilder.LOGGER.warn("Config file had missing entry {}", this.name);
        }
    }

    public JsonElement getDefaultValue() {
        return (JsonElement)this.defValue.get();
    }

    @Override
    public void saveToJson(JsonObject object) {
        if (this.value == null) {
            this.value = this.getDefaultValue();
        }

        object.add(this.name, this.value);
    }
}
