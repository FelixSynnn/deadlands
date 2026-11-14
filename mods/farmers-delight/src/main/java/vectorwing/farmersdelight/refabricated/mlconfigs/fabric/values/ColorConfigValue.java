package vectorwing.farmersdelight.refabricated.mlconfigs.fabric.values;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import java.util.Locale;
import java.util.Optional;
import vectorwing.farmersdelight.refabricated.mlconfigs.ConfigBuilder;

public class ColorConfigValue extends IntConfigValue {
    public static final Codec<Integer> CODEC = Codec.either(
            Codec.INT, Codec.STRING.flatXmap(ColorConfigValue::isValidStringOrError, s -> isValidStringOrError(s).map(ColorConfigValue::formatString))
        )
        .xmap(
            either -> either.map(integer -> (Integer)integer, s -> Integer.parseUnsignedInt(s, 16)),
            integer -> Either.right("#" + String.format("%08X", integer))
        );

    public ColorConfigValue(String name, int defaultValue) {
        super(name, defaultValue, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    @Override
    public void loadFromJson(JsonObject element) {
        if (element.has(this.name)) {
            try {
                String s = element.get(this.name).getAsString();
                Optional<Pair<Integer, JsonElement>> result = CODEC.decode(JsonOps.INSTANCE, new JsonPrimitive(s)).result();
                if (result.isPresent()) {
                    this.value = result.get().getFirst();
                    return;
                }

                this.value = this.defaultValue;
            } catch (Exception var4) {
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

        object.addProperty(this.name, CODEC.encodeStart(JsonOps.INSTANCE, this.value).result().get().getAsString());
    }

    private static String formatString(String s) {
        return "#" + s.toUpperCase(Locale.ROOT);
    }

    public static DataResult<String> isValidStringOrError(String s) {
        String st = s;
        if (s.startsWith("0x")) {
            st = s.substring(2);
        } else if (s.startsWith("#")) {
            st = s.substring(1);
        }

        if (st.length() > 8) {
            return DataResult.error(() -> "Invalid dye format. Hex value must have up to 8 characters.");
        }

        try {
            int parsedValue = Integer.parseUnsignedInt(st, 16);
            return DataResult.success(st);
        } catch (NumberFormatException e) {
            return DataResult.error(() -> "Invalid dye format. Must be in hex format (0xff00ff00, #ff00ff00, ff00ff00) or integer value");
        }
    }
}
