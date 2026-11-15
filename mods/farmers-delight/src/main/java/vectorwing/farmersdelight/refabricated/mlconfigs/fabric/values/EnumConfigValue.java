package vectorwing.farmersdelight.refabricated.mlconfigs.fabric.values;

import com.google.gson.JsonObject;
import vectorwing.farmersdelight.refabricated.mlconfigs.ConfigBuilder;

public class EnumConfigValue<T extends Enum<T>> extends ConfigValue<T> {
    private final T[] acceptedValues;

    public EnumConfigValue(String name, T defaultValue) {
        super(name, defaultValue);
        this.acceptedValues = defaultValue.getDeclaringClass().getEnumConstants();
    }

    public boolean isValid(T value) {
        return true;
    }

    public Class<T> getEnumClass() {
        return this.defaultValue.getDeclaringClass();
    }

    @Override
    public void loadFromJson(JsonObject element) {
        if (element.has(this.name)) {
            try {
                String s = element.get(this.name).getAsString();

                for (T v : this.acceptedValues) {
                    if (v.name().equals(s)) {
                        this.value = v;
                        return;
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

        object.addProperty(this.name, this.value.name());
    }
}
