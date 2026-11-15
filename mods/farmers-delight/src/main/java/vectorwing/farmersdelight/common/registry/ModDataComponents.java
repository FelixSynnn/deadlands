package vectorwing.farmersdelight.common.registry;

import com.mojang.serialization.Codec;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.enchantment.ConditionalEffect;
import net.minecraft.world.item.enchantment.effects.EnchantmentValueEffect;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import vectorwing.farmersdelight.common.item.component.ItemStackWrapper;
import vectorwing.farmersdelight.refabricated.RegUtils;

public class ModDataComponents {
    public static final Supplier<DataComponentType<ItemStackWrapper>> MEAL = RegUtils.regComponent(
        "meal", builder -> builder.persistent(ItemStackWrapper.CODEC).networkSynchronized(ItemStackWrapper.STREAM_CODEC).cacheEncoding()
    );
    public static final Supplier<DataComponentType<ItemStackWrapper>> CONTAINER = RegUtils.regComponent(
        "container", builder -> builder.persistent(ItemStackWrapper.CODEC).networkSynchronized(ItemStackWrapper.STREAM_CODEC).cacheEncoding()
    );
    public static final Supplier<DataComponentType<Integer>> COOKING_TIME_LENGTH = RegUtils.regComponent(
        "cooking_time_length", builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.INT)
    );
    public static final Supplier<DataComponentType<ItemStackWrapper>> SKILLET_INGREDIENT = RegUtils.regComponent(
        "skillet_ingredient", builder -> builder.persistent(ItemStackWrapper.CODEC).networkSynchronized(ItemStackWrapper.STREAM_CODEC).cacheEncoding()
    );
    public static final Supplier<DataComponentType<Long>> SKILLET_FLIP_TIMESTAMP = RegUtils.regComponent(
        "skillet_flip_timestamp", builder -> builder.persistent(Codec.LONG).networkSynchronized(ByteBufCodecs.VAR_LONG).cacheEncoding()
    );
    public static final Supplier<DataComponentType<Boolean>> SKILLET_FLIPPED = RegUtils.regComponent(
        "skillet_flipped", builder -> builder.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).cacheEncoding()
    );
    public static final Supplier<DataComponentType<List<ConditionalEffect<EnchantmentValueEffect>>>> BACKSTABBING = RegUtils.regEnchComponent(
        "backstabbing", builder -> builder.persistent(ConditionalEffect.codec(EnchantmentValueEffect.CODEC, LootContextParamSets.ENCHANTED_DAMAGE).listOf())
    );

    public static void touch() {
    }
}
