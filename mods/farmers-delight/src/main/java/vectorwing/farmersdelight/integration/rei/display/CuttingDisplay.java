package vectorwing.farmersdelight.integration.rei.display;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.display.DisplaySerializer;
import me.shedaniel.rei.api.common.display.basic.BasicDisplay;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import me.shedaniel.rei.plugin.common.displays.crafting.CraftingDisplay;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jspecify.annotations.Nullable;
import vectorwing.farmersdelight.common.crafting.CuttingBoardRecipe;
import vectorwing.farmersdelight.integration.rei.REICategoryIdentifiers;

public class CuttingDisplay extends BasicDisplay implements CraftingDisplay {
    private EntryIngredient tool;
    private List<Pair<EntryIngredient, Float>> chanceResults;
    private static final StreamCodec<RegistryFriendlyByteBuf, Pair<EntryIngredient, Float>> CHANCE_RESULTS_STREAM_CODEC = StreamCodec.composite(
        EntryIngredient.streamCodec(), Pair::getFirst, ByteBufCodecs.FLOAT, Pair::getSecond, Pair::new
    );
    public static final DisplaySerializer<CuttingDisplay> SERIALIZER = DisplaySerializer.of(
        RecordCodecBuilder.mapCodec(
            inst -> inst.group(
                    EntryIngredient.codec().listOf().fieldOf("inputs").forGetter(BasicDisplay::getInputEntries),
                    EntryIngredient.codec().listOf().fieldOf("outputs").forGetter(BasicDisplay::getOutputEntries),
                    Identifier.CODEC.optionalFieldOf("location").forGetter(BasicDisplay::getDisplayLocation),
                    EntryIngredient.codec().fieldOf("tool").forGetter(CuttingDisplay::getTool),
                    Codec.<EntryIngredient, Float>pair(EntryIngredient.codec(), Codec.FLOAT)
                        .listOf()
                        .fieldOf("chance_results")
                        .forGetter(CuttingDisplay::getRollableOutputs)
                )
                .apply(inst, CuttingDisplay::new)
        ),
        StreamCodec.composite(
            EntryIngredient.streamCodec().apply(ByteBufCodecs.list()),
            BasicDisplay::getInputEntries,
            EntryIngredient.streamCodec().apply(ByteBufCodecs.list()),
            BasicDisplay::getOutputEntries,
            ByteBufCodecs.optional(Identifier.STREAM_CODEC),
            BasicDisplay::getDisplayLocation,
            EntryIngredient.streamCodec(),
            CuttingDisplay::getTool,
            CHANCE_RESULTS_STREAM_CODEC.apply(ByteBufCodecs.list()),
            CuttingDisplay::getRollableOutputs,
            CuttingDisplay::new
        )
    );

    public CuttingDisplay(RecipeHolder<CuttingBoardRecipe> recipe) {
        this(
            List.of(EntryIngredients.ofIngredient(recipe.value().getInput())),
            recipe.value().getRollableResults().stream().map(result -> EntryIngredients.of(result.stack())).toList(),
            Optional.of(recipe.id().identifier()),
            EntryIngredients.ofIngredient(recipe.value().getTool()),
            recipe.value().getRollableResults().stream().map(result -> Pair.of(EntryIngredients.of(result.stack()), result.chance())).toList()
        );
    }

    public CuttingDisplay(
        List<EntryIngredient> inputs,
        List<EntryIngredient> outputs,
        Optional<Identifier> location,
        EntryIngredient tool,
        List<Pair<EntryIngredient, Float>> chanceResults
    ) {
        super(inputs, outputs, location);
        this.tool = tool;
        this.chanceResults = chanceResults;
    }

    public EntryIngredient getTool() {
        return this.tool;
    }

    public List<Pair<EntryIngredient, Float>> getRollableOutputs() {
        return this.chanceResults;
    }

    public CategoryIdentifier<?> getCategoryIdentifier() {
        return REICategoryIdentifiers.CUTTING;
    }

    public @Nullable DisplaySerializer<? extends Display> getSerializer() {
        return SERIALIZER;
    }

    public boolean isShapeless() {
        return false;
    }

    public int getWidth() {
        return 1;
    }

    public int getHeight() {
        return 1;
    }
}
