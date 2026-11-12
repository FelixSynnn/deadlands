package vectorwing.farmersdelight.integration.rei.client.display;

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
import me.shedaniel.rei.plugin.client.displays.ClientsidedRecipeBookDisplay;
import me.shedaniel.rei.plugin.common.displays.crafting.CraftingDisplay;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.jspecify.annotations.Nullable;
import vectorwing.farmersdelight.client.recipe.CookingPotRecipeDisplay;
import vectorwing.farmersdelight.integration.rei.REICategoryIdentifiers;

public class ClientsidedCookingPotDisplay extends BasicDisplay implements CraftingDisplay, ClientsidedRecipeBookDisplay {
    private final Optional<RecipeDisplayId> id;
    private final Optional<EntryIngredient> container;
    private final int cookTime;
    private final float experience;
    public static final DisplaySerializer<ClientsidedCookingPotDisplay> SERIALIZER = DisplaySerializer.of(
        RecordCodecBuilder.mapCodec(
            inst -> inst.group(
                    EntryIngredient.codec().listOf().fieldOf("inputs").forGetter(BasicDisplay::getInputEntries),
                    EntryIngredient.codec().listOf().fieldOf("outputs").forGetter(BasicDisplay::getOutputEntries),
                    Codec.INT.xmap(RecipeDisplayId::new, RecipeDisplayId::index).optionalFieldOf("id").forGetter(ClientsidedCookingPotDisplay::recipeDisplayId),
                    EntryIngredient.codec().optionalFieldOf("container").forGetter(ClientsidedCookingPotDisplay::getOutputContainer),
                    Codec.INT.fieldOf("cook_time").forGetter(ClientsidedCookingPotDisplay::getCookTime),
                    Codec.FLOAT.fieldOf("experience").forGetter(ClientsidedCookingPotDisplay::getExperience)
                )
                .apply(inst, ClientsidedCookingPotDisplay::new)
        ),
        StreamCodec.composite(
            EntryIngredient.streamCodec().apply(ByteBufCodecs.list()),
            BasicDisplay::getInputEntries,
            EntryIngredient.streamCodec().apply(ByteBufCodecs.list()),
            BasicDisplay::getOutputEntries,
            ByteBufCodecs.optional(RecipeDisplayId.STREAM_CODEC),
            ClientsidedCookingPotDisplay::recipeDisplayId,
            ByteBufCodecs.optional(EntryIngredient.streamCodec()),
            ClientsidedCookingPotDisplay::getOutputContainer,
            ByteBufCodecs.INT,
            ClientsidedCookingPotDisplay::getCookTime,
            ByteBufCodecs.FLOAT,
            ClientsidedCookingPotDisplay::getExperience,
            ClientsidedCookingPotDisplay::new
        )
    );

    public ClientsidedCookingPotDisplay(CookingPotRecipeDisplay recipe, Optional<RecipeDisplayId> id) {
        this(
            EntryIngredients.ofSlotDisplays(recipe.ingredients()),
            List.of(EntryIngredients.ofSlotDisplay(recipe.result())),
            id,
            recipe.container().map(slotDisplay -> EntryIngredients.ofSlotDisplay(slotDisplay)),
            recipe.duration(),
            recipe.experience()
        );
    }

    public ClientsidedCookingPotDisplay(
        List<EntryIngredient> inputs,
        List<EntryIngredient> outputs,
        Optional<RecipeDisplayId> id,
        Optional<EntryIngredient> container,
        int cookTime,
        float experience
    ) {
        super(inputs, outputs);
        this.id = id;
        this.container = container;
        this.cookTime = cookTime;
        this.experience = experience;
    }

    public Optional<EntryIngredient> getOutputContainer() {
        return this.container;
    }

    public int getCookTime() {
        return this.cookTime;
    }

    public float getExperience() {
        return this.experience;
    }

    public boolean isShapeless() {
        return true;
    }

    public int getWidth() {
        return 3;
    }

    public int getHeight() {
        return 2;
    }

    public CategoryIdentifier<?> getCategoryIdentifier() {
        return REICategoryIdentifiers.COOKING;
    }

    public @Nullable DisplaySerializer<? extends Display> getSerializer() {
        return SERIALIZER;
    }

    public Optional<RecipeDisplayId> recipeDisplayId() {
        return this.id;
    }
}
