package vectorwing.farmersdelight.refabricated.mlconfigs.fabric.values;

import com.google.gson.JsonObject;
import vectorwing.farmersdelight.refabricated.mlconfigs.ConfigBuilder;

public class BoolConfigValue extends ConfigValue<Boolean> {
    public BoolConfigValue(String name, Boolean defaultValue) {
        super(name, defaultValue);
    }

    public boolean isValid(Boolean value) {
        return true;
    }

    @Override
    public void loadFromJson(JsonObject element) {
        if (element.has(this.name)) {
            try {
                this.value = element.get(this.name).getAsBoolean();
                if (this.isValid(this.value)) {
                    return;
                }

                this.value = this.defaultValue;
            } catch (Exception var3) {
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

        object.addProperty(this.name, this.value);
    }
}
