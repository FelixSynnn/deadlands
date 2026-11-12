package vectorwing.farmersdelight.common.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import vectorwing.farmersdelight.common.crafting.ingredient.ChanceResult;
import vectorwing.farmersdelight.common.registry.ModRecipeBookCategories;
import vectorwing.farmersdelight.common.registry.ModRecipeSerializers;
import vectorwing.farmersdelight.common.registry.ModRecipeTypes;

public class CuttingBoardRecipe implements Recipe<CuttingBoardRecipeInput> {
    public static final int MAX_RESULTS = 4;
    private final String group;
    private final Ingredient input;
    private final Ingredient tool;
    private final List<ChanceResult> results;
    private final @Nullable Holder<SoundEvent> soundEvent;

    public CuttingBoardRecipe(String group, Ingredient input, Ingredient tool, List<ChanceResult> results, @Nullable Holder<SoundEvent> soundEvent) {
        this.group = group;
        this.input = input;
        this.tool = tool;
        this.results = results;
        this.soundEvent = soundEvent;
    }

    private CuttingBoardRecipe(String group, Ingredient input, Ingredient tool, List<ChanceResult> results, Optional<Holder<SoundEvent>> soundEvent) {
        this(group, input, tool, results, soundEvent.orElse(null));
    }

    public boolean matches(CuttingBoardRecipeInput input, Level level) {
        return this.input.test(input.item()) && this.tool.test(input.tool());
    }

    public ItemStack assemble(CuttingBoardRecipeInput inv, Provider provider) {
        return this.results.get(0).stack().copy();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    @Override
    public String group() {
        return this.group;
    }

    public Ingredient getInput() {
        return this.input;
    }

    public Ingredient getTool() {
        return this.tool;
    }

    public List<ItemStack> getResults() {
        return this.getRollableResults().stream().map(ChanceResult::stack).collect(Collectors.toList());
    }

    public List<ChanceResult> getRollableResults() {
        return this.results;
    }

    public List<ItemStack> rollResults(RandomSource rand, int fortuneLevel) {
        List<ItemStack> results = new ArrayList<>();

        for (ChanceResult output : this.getRollableResults()) {
            ItemStack stack = output.rollOutput(rand, fortuneLevel);
            if (!stack.isEmpty()) {
                results.add(stack);
            }
        }

        return results;
    }

    public Optional<SoundEvent> getSoundEvent() {
        return this.getSoundEventHolder().map(Holder::value);
    }

    public Optional<Holder<SoundEvent>> getSoundEventHolder() {
        return Optional.ofNullable(this.soundEvent);
    }

    @Override
    public RecipeSerializer<CuttingBoardRecipe> getSerializer() {
        return ModRecipeSerializers.CUTTING.get();
    }

    @Override
    public RecipeType<CuttingBoardRecipe> getType() {
        return ModRecipeTypes.CUTTING.get();
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return ModRecipeBookCategories.CUTTING.get();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o != null && this.getClass() == o.getClass()) {
            CuttingBoardRecipe that = (CuttingBoardRecipe)o;
            if (!this.group().equals(that.group())) {
                return false;
            } else if (!this.input.equals(that.input)) {
                return false;
            } else if (!this.getTool().equals(that.getTool())) {
                return false;
            } else {
                return !this.getResults().equals(that.getResults()) ? false : Objects.equals(this.soundEvent, that.soundEvent);
            }
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.group, this.input, this.tool, this.results, this.soundEvent);
    }

    public static class Serializer {
        public static final StreamCodec<RegistryFriendlyByteBuf, CuttingBoardRecipe> STREAM_CODEC = StreamCodec.of(
            CuttingBoardRecipe.Serializer::toNetwork, CuttingBoardRecipe.Serializer::fromNetwork
        );
        private static final MapCodec<CuttingBoardRecipe> CODEC = RecordCodecBuilder.mapCodec(
            inst -> inst.group(
                    Codec.STRING.optionalFieldOf("group", "").forGetter(CuttingBoardRecipe::group),
                    Ingredient.CODEC
                        .listOf()
                        .fieldOf("ingredients")
                        .validate(
                            ingredients -> {
                                if (ingredients.isEmpty()) {
                                    return DataResult.error(() -> "No ingredients for cutting recipe");
                                } else {
                                    return ingredients.size() > 1
                                        ? DataResult.error(() -> "Too many ingredients for cutting recipe! Please define only one ingredient")
                                        : DataResult.success(ingredients);
                                }
                            }
                        )
                        .forGetter(cuttingBoardRecipe -> Collections.singletonList(cuttingBoardRecipe.getInput())),
                    Ingredient.CODEC.fieldOf("tool").forGetter(CuttingBoardRecipe::getTool),
                    ChanceResult.CODEC.listOf(1, 4).fieldOf("result").forGetter(CuttingBoardRecipe::getRollableResults),
                    SoundEvent.CODEC.optionalFieldOf("sound").forGetter(CuttingBoardRecipe::getSoundEventHolder)
                )
                .apply(
                    inst,
                    (s, ingredients, ingredient, chanceResults, soundEventHolder) -> new CuttingBoardRecipe(
                        s, ingredients.getFirst(), ingredient, chanceResults, soundEventHolder
                    )
                )
        );

        public static CuttingBoardRecipe fromNetwork(RegistryFriendlyByteBuf buffer) {
            String group = buffer.readUtf(32767);
            Ingredient inputItem = Ingredient.CONTENTS_STREAM_CODEC.decode(buffer);
            Ingredient tool = Ingredient.CONTENTS_STREAM_CODEC.decode(buffer);
            List<ChanceResult> results = ChanceResult.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buffer);
            Optional<Holder<SoundEvent>> soundEvent = (Optional<Holder<SoundEvent>>)SoundEvent.STREAM_CODEC.apply(ByteBufCodecs::optional).decode(buffer);
            return new CuttingBoardRecipe(group, inputItem, tool, results, soundEvent.orElse(null));
        }

        public static void toNetwork(RegistryFriendlyByteBuf buffer, CuttingBoardRecipe recipe) {
            buffer.writeUtf(recipe.group);
            Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, recipe.input);
            Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, recipe.tool);
            ChanceResult.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buffer, recipe.results);
            SoundEvent.STREAM_CODEC.apply(ByteBufCodecs::optional).encode(buffer, Optional.ofNullable(recipe.soundEvent));
        }

        public static MapCodec<CuttingBoardRecipe> codec() {
            return CODEC;
        }

        public static StreamCodec<RegistryFriendlyByteBuf, CuttingBoardRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
