package vectorwing.farmersdelight.data.builder;

import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import java.util.Objects;
import net.minecraft.advancements.Criterion;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeBuilder;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.ItemLike;
import org.jspecify.annotations.Nullable;
import vectorwing.farmersdelight.common.crafting.CuttingBoardRecipe;
import vectorwing.farmersdelight.common.crafting.ingredient.ChanceResult;

@MethodsReturnNonnullByDefault
public class CuttingBoardRecipeBuilder implements RecipeBuilder {
    private final NonNullList<ChanceResult> results = NonNullList.createWithCapacity(4);
    private final Ingredient ingredient;
    private final Ingredient tool;
    private Holder<SoundEvent> soundEvent;
    private @Nullable String namespace;
    private CuttingRecipeFolder folder;

    public CuttingBoardRecipeBuilder(Ingredient ingredient, Ingredient tool, ItemLike mainResult, int count, float chance) {
        this.results.add(new ChanceResult(new ItemStack(mainResult.asItem(), count), chance));
        this.ingredient = ingredient;
        this.tool = tool;
        this.folder = CuttingRecipeFolder.CUTTING;
    }

    public static CuttingBoardRecipeBuilder cuttingRecipe(Ingredient ingredient, Ingredient tool, ItemLike mainResult, int count) {
        return new CuttingBoardRecipeBuilder(ingredient, tool, mainResult, count, 1.0F);
    }

    public static CuttingBoardRecipeBuilder cuttingRecipe(Ingredient ingredient, Ingredient tool, ItemLike mainResult, int count, float chance) {
        return new CuttingBoardRecipeBuilder(ingredient, tool, mainResult, count, chance);
    }

    public static CuttingBoardRecipeBuilder cuttingRecipe(Ingredient ingredient, Ingredient tool, ItemLike mainResult) {
        return new CuttingBoardRecipeBuilder(ingredient, tool, mainResult, 1, 1.0F);
    }

    public CuttingBoardRecipeBuilder addResult(ItemLike result) {
        return this.addResult(result, 1);
    }

    public CuttingBoardRecipeBuilder addResult(ItemLike result, int count) {
        this.results.add(new ChanceResult(new ItemStack(result.asItem(), count), 1.0F));
        return this;
    }

    public CuttingBoardRecipeBuilder addResultWithChance(ItemLike result, float chance) {
        return this.addResultWithChance(result, chance, 1);
    }

    public CuttingBoardRecipeBuilder addResultWithChance(ItemLike result, float chance, int count) {
        this.results.add(new ChanceResult(new ItemStack(result.asItem(), count), chance));
        return this;
    }

    public CuttingBoardRecipeBuilder addSound(SoundEvent soundEvent) {
        this.soundEvent = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(soundEvent);
        return this;
    }

    public CuttingBoardRecipeBuilder addSound(Holder<SoundEvent> soundEvent) {
        this.soundEvent = soundEvent;
        return this;
    }

    @Override
    public RecipeBuilder unlockedBy(String p_176496_, Criterion<?> p_301065_) {
        return this;
    }

    public CuttingBoardRecipeBuilder setNamespace(String namespace) {
        this.namespace = namespace;
        return this;
    }

    public CuttingBoardRecipeBuilder salvaging() {
        this.folder = CuttingRecipeFolder.SALVAGING;
        return this;
    }

    @Override
    public RecipeBuilder group(@Nullable String p_176495_) {
        return this;
    }

    @Override
    public Item getResult() {
        return this.ingredient.items().findFirst().map(Holder::value).orElseThrow();
    }

    public static Identifier getDefaultRecipeId(ItemLike itemLike) {
        return Objects.requireNonNull(BuiltInRegistries.ITEM.getKey(itemLike.asItem()));
    }

    public void saveToFD(RecipeOutput output) {
        this.setNamespace("farmersdelight").save(output);
    }

    @Override
    public void save(RecipeOutput output) {
        Identifier defaultLocation = getDefaultRecipeId(this.getResult());
        this.build(
            output,
            Identifier.fromNamespaceAndPath(this.namespace != null ? this.namespace : defaultLocation.getNamespace(), defaultLocation.getPath())
                .withPrefix(this.folder.getSerializedName() + "/")
        );
    }

    public void build(RecipeOutput outputIn, String save) {
        Identifier Identifier = BuiltInRegistries.ITEM.getKey(this.getResult());
        if (Identifier.parse(save).equals(Identifier)) {
            throw new IllegalStateException("Cutting Recipe " + save + " should remove its 'save' argument");
        }

        this.build(outputIn, Identifier.parse(save));
    }

    public void build(RecipeOutput output, Identifier id) {
        this.save(output, ResourceKey.create(Registries.RECIPE, id));
    }

    public void save(RecipeOutput output, Identifier id) {
        this.build(output, id);
    }

    @Override
    public void save(RecipeOutput output, ResourceKey<Recipe<?>> resourceKey) {
        CuttingBoardRecipe recipe = new CuttingBoardRecipe("", this.ingredient, this.tool, this.results, this.soundEvent);
        output.accept(resourceKey, recipe, null);
    }
}
