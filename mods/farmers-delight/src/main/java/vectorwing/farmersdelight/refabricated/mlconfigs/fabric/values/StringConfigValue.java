package vectorwing.farmersdelight.refabricated.mlconfigs.fabric.values;

import com.google.common.base.Preconditions;
import com.google.gson.JsonObject;
import java.util.function.Predicate;
import vectorwing.farmersdelight.refabricated.mlconfigs.ConfigBuilder;

public class StringConfigValue extends ConfigValue<String> {
    private final Predicate<Object> validator;

    public StringConfigValue(String name, String defaultValue, Predicate<Object> validator) {
        super(name, defaultValue);
        this.validator = validator;
        Preconditions.checkState(this.isValid(defaultValue), "Config defaults are invalid");
    }

    public boolean isValid(String value) {
        return this.validator.test(value);
    }

    @Override
    public void loadFromJson(JsonObject element) {
        if (element.has(this.name)) {
            try {
                this.value = element.get(this.name).getAsString();
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
