package vectorwing.farmersdelight.refabricated.mlconfigs.fabric.values;

import com.google.common.base.Preconditions;
import com.google.gson.JsonObject;
import java.util.Objects;
import vectorwing.farmersdelight.refabricated.mlconfigs.ConfigBuilder;

public class IntConfigValue extends ConfigValue<Integer> {
    private final Integer min;
    private final Integer max;

    public IntConfigValue(String name, Integer defaultValue, Integer min, Integer max) {
        super(name, defaultValue);
        this.min = Objects.requireNonNull(min);
        this.max = Objects.requireNonNull(max);
        Preconditions.checkState(this.isValid(defaultValue), "Config defaults are invalid");
    }

    public boolean isValid(Integer value) {
        return value >= this.min && value <= this.max;
    }

    @Override
    public void loadFromJson(JsonObject element) {
        if (element.has(this.name)) {
            try {
                this.value = element.get(this.name).getAsInt();
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

    public Integer getMax() {
        return this.max;
    }

    public Integer getMin() {
        return this.min;
    }
}
