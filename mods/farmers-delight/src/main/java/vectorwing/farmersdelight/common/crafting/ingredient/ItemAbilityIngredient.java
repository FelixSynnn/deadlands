package vectorwing.farmersdelight.common.crafting.ingredient;

import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredient;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import vectorwing.farmersdelight.FarmersDelight;
import vectorwing.farmersdelight.refabricated.ItemAbility;

@MethodsReturnNonnullByDefault
public class ItemAbilityIngredient implements CustomIngredient {
    public static final ItemAbilityIngredient.Serializer SERIALIZER = new ItemAbilityIngredient.Serializer();
    public static final Identifier SERIALIZER_ID = FarmersDelight.id("item_ability");
    protected final ItemAbility itemAbility;
    protected List<Holder<Item>> itemStacks;

    public ItemAbilityIngredient(ItemAbility itemAbility) {
        this.itemAbility = itemAbility;
    }

    public static void touch() {
        CustomIngredientSerializer.register(SERIALIZER);
    }

    protected void dissolve() {
        if (this.itemStacks == null) {
            this.itemStacks = BuiltInRegistries.ITEM.stream().map(BuiltInRegistries.ITEM::wrapAsHolder).filter(this.itemAbility::canPerformAction).toList();
        }
    }

    @Override
    public boolean test(@Nullable ItemStack stack) {
        return stack != null && this.itemAbility.canPerformAction(stack);
    }

    @Override
    public Stream<Holder<Item>> getMatchingItems() {
        this.dissolve();
        return this.itemStacks.stream();
    }

    public ItemAbility getItemAbility() {
        return this.itemAbility;
    }

    @Override
    public boolean requiresTesting() {
        return false;
    }

    @Override
    public CustomIngredientSerializer<?> getSerializer() {
        return SERIALIZER;
    }

    public static class Serializer implements CustomIngredientSerializer<ItemAbilityIngredient> {
        public static final MapCodec<ItemAbilityIngredient> CODEC = RecordCodecBuilder.mapCodec(
            inst -> inst.group(ItemAbility.CODEC.fieldOf("action").forGetter(ItemAbilityIngredient::getItemAbility)).apply(inst, ItemAbilityIngredient::new)
        );
        public static final StreamCodec<RegistryFriendlyByteBuf, ItemAbilityIngredient> STREAM_CODEC = ByteBufCodecs.fromCodec(ItemAbility.CODEC)
            .map(ItemAbilityIngredient::new, ItemAbilityIngredient::getItemAbility)
            .cast();

        @Override
        public Identifier getIdentifier() {
            return ItemAbilityIngredient.SERIALIZER_ID;
        }

        @Override
        public MapCodec<ItemAbilityIngredient> getCodec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, ItemAbilityIngredient> getPacketCodec() {
            return STREAM_CODEC;
        }
    }
}
