package vectorwing.farmersdelight.common.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import io.netty.buffer.ByteBuf;
import java.util.EnumSet;
import java.util.function.IntFunction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.ByIdMap.OutOfBoundsStrategy;

public enum CookingPotBookCategory {
    MEALS("meals"),
    DRINKS("drinks"),
    MISC("misc");

    public static final Codec<CookingPotBookCategory> CODEC = Codec.STRING
        .flatXmap(
            s -> {
                CookingPotBookCategory tab = findByName(s);
                return tab == null
                    ? DataResult.error(
                        () -> "Optional field 'recipe_book_tab' does not match any valid tab. If defined, must be one of the following: "
                            + EnumSet.<CookingPotBookCategory>allOf(CookingPotBookCategory.class)
                    )
                    : DataResult.success(tab);
            },
            tab -> DataResult.success(tab.toString())
        );
    public static final IntFunction<CookingPotBookCategory> BY_ID = ByIdMap.continuous(Enum::ordinal, values(), OutOfBoundsStrategy.ZERO);
    public static final StreamCodec<ByteBuf, CookingPotBookCategory> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, Enum::ordinal);
    public final String name;

    CookingPotBookCategory(String name) {
        this.name = name;
    }

    public static CookingPotBookCategory findByName(String name) {
        for (CookingPotBookCategory value : values()) {
            if (value.name.equals(name)) {
                return value;
            }
        }

        return null;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
