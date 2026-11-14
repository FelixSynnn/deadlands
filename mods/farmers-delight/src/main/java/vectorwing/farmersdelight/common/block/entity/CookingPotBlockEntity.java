package vectorwing.farmersdelight.common.block.entity;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.ints.IntImmutableList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.DataComponentMap.Builder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.ProblemReporter.ScopedCollector;
import net.minecraft.world.Containers;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.RecipeCraftingHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeManager.CachedCheck;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import vectorwing.farmersdelight.common.block.CookingPotBlock;
import vectorwing.farmersdelight.common.block.entity.container.CookingPotMenu;
import vectorwing.farmersdelight.common.block.entity.inventory.CookingPotItemHandler;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;
import vectorwing.farmersdelight.common.item.component.ItemStackWrapper;
import vectorwing.farmersdelight.common.registry.ModBlockEntityTypes;
import vectorwing.farmersdelight.common.registry.ModDataComponents;
import vectorwing.farmersdelight.common.registry.ModItems;
import vectorwing.farmersdelight.common.registry.ModParticleTypes;
import vectorwing.farmersdelight.common.registry.ModRecipeTypes;
import vectorwing.farmersdelight.common.utility.ItemUtils;
import vectorwing.farmersdelight.common.utility.TextUtils;
import vectorwing.farmersdelight.refabricated.inventory.ItemStackHandler;
import vectorwing.farmersdelight.refabricated.inventory.RecipeWrapper;

public class CookingPotBlockEntity
    extends SyncedBlockEntity
    implements ExtendedScreenHandlerFactory<BlockPos>,
    HeatableBlockEntity,
    Nameable,
    RecipeCraftingHolder {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Codec<Map<ResourceKey<Recipe<?>>, Integer>> RECIPES_USED_CODEC = Codec.unboundedMap(Recipe.KEY_CODEC, Codec.INT);
    public static final int MEAL_DISPLAY_SLOT = 6;
    public static final int CONTAINER_SLOT = 7;
    public static final int OUTPUT_SLOT = 8;
    public static final int INVENTORY_SIZE = 9;
    public static final Map<Item, Item> INGREDIENT_REMAINDER_OVERRIDES = Map.ofEntries(
        Map.entry(Items.POWDER_SNOW_BUCKET, Items.BUCKET),
        Map.entry(Items.AXOLOTL_BUCKET, Items.BUCKET),
        Map.entry(Items.COD_BUCKET, Items.BUCKET),
        Map.entry(Items.PUFFERFISH_BUCKET, Items.BUCKET),
        Map.entry(Items.SALMON_BUCKET, Items.BUCKET),
        Map.entry(Items.TROPICAL_FISH_BUCKET, Items.BUCKET),
        Map.entry(Items.SUSPICIOUS_STEW, Items.BOWL),
        Map.entry(Items.MUSHROOM_STEW, Items.BOWL),
        Map.entry(Items.RABBIT_STEW, Items.BOWL),
        Map.entry(Items.BEETROOT_SOUP, Items.BOWL),
        Map.entry(Items.POTION, Items.GLASS_BOTTLE),
        Map.entry(Items.SPLASH_POTION, Items.GLASS_BOTTLE),
        Map.entry(Items.LINGERING_POTION, Items.GLASS_BOTTLE),
        Map.entry(Items.EXPERIENCE_BOTTLE, Items.GLASS_BOTTLE)
    );
    private final ItemStackHandler inventory = this.createHandler();
    private final CookingPotItemHandler inputHandler = new CookingPotItemHandler(this.inventory, Direction.UP);
    private final CookingPotItemHandler outputHandler = new CookingPotItemHandler(this.inventory, Direction.DOWN);
    private int cookTime;
    private int cookTimeTotal;
    private ItemStack mealContainerStack = ItemStack.EMPTY;
    private Component customName;
    protected final ContainerData cookingPotData = this.createIntArray();
    private final Object2IntOpenHashMap<ResourceKey<Recipe<?>>> usedRecipeTracker = new Object2IntOpenHashMap<>();
    private final CachedCheck<RecipeWrapper, CookingPotRecipe> quickCheck = RecipeManager.createCheck(ModRecipeTypes.COOKING.get());

    public CookingPotBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.COOKING_POT.get(), pos, state);
    }

    public static void init() {
        ItemStorage.SIDED.registerForBlockEntity(CookingPotBlockEntity::getStorage, ModBlockEntityTypes.COOKING_POT.get());
    }

    public static ItemStack getMealFromItem(ItemStack cookingPotStack) {
        return !cookingPotStack.is(ModItems.COOKING_POT.get())
            ? ItemStack.EMPTY
            : cookingPotStack.getOrDefault(ModDataComponents.MEAL.get(), ItemStackWrapper.EMPTY).getStack();
    }

    public static void takeServingFromItem(ItemStack cookingPotStack) {
        if (cookingPotStack.is(ModItems.COOKING_POT.get())) {
            ItemStack mealStack = cookingPotStack.getOrDefault(ModDataComponents.MEAL.get(), ItemStackWrapper.EMPTY).getStack();
            mealStack.shrink(1);
            cookingPotStack.set(ModDataComponents.MEAL.get(), new ItemStackWrapper(mealStack));
        }
    }

    public static ItemStack getContainerFromItem(ItemStack cookingPotStack) {
        return !cookingPotStack.is(ModItems.COOKING_POT.get())
            ? ItemStack.EMPTY
            : cookingPotStack.getOrDefault(ModDataComponents.CONTAINER.get(), ItemStackWrapper.EMPTY).getStack();
    }

    @Override
    public void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.inventory.deserialize(input.childOrEmpty("Inventory"));
        this.cookTime = input.getIntOr("CookTime", 0);
        this.cookTimeTotal = input.getIntOr("CookTimeTotal", 0);
        this.mealContainerStack = input.read("Container", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        this.customName = input.read("CustomName", ComponentSerialization.CODEC).orElse(null);
        this.usedRecipeTracker.clear();
        this.usedRecipeTracker.putAll(input.read("RecipesUsed", RECIPES_USED_CODEC).orElse(Collections.emptyMap()));
    }

    @Override
    public void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("CookTime", this.cookTime);
        output.putInt("CookTimeTotal", this.cookTimeTotal);
        output.storeNullable("Container", ItemStack.CODEC, this.mealContainerStack.isEmpty() ? null : this.mealContainerStack);
        if (this.customName != null) {
            output.storeNullable("CustomName", ComponentSerialization.CODEC, this.customName);
        }

        this.inventory.serialize(output.child("Inventory"));
        output.store("RecipesUsed", RECIPES_USED_CODEC, this.usedRecipeTracker);
    }

    private void writeItems(ValueOutput output) {
        super.saveAdditional(output);
        output.storeNullable("Container", ItemStack.CODEC, this.mealContainerStack.isEmpty() ? null : this.mealContainerStack);
        this.inventory.serialize(output.child("Inventory"));
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (this.level instanceof ServerLevel serverLevel) {
            Containers.dropContents(this.level, pos, this.getDroppableInventory());
            this.getUsedRecipesAndPopExperience(serverLevel, Vec3.atCenterOf(pos));
            this.level.updateNeighbourForOutputSignal(pos, state.getBlock());
        }

        super.preRemoveSideEffects(pos, state);
    }

    public void writeMeal(ValueOutput output) {
        if (!this.getMeal().isEmpty()) {
            ItemStackHandler drops = new ItemStackHandler(9);

            for (int i = 0; i < 9; i++) {
                drops.setStackInSlot(i, i == 6 ? this.inventory.getStackInSlot(i) : ItemStack.EMPTY);
            }

            if (this.customName != null) {
                output.storeNullable("CustomName", ComponentSerialization.CODEC, this.customName);
            }

            output.storeNullable("Container", ItemStack.OPTIONAL_CODEC, this.mealContainerStack.isEmpty() ? null : this.mealContainerStack);
            drops.serialize(output.child("Inventory"));
        }
    }

    public ItemStack getAsItem() {
        ItemStack stack = new ItemStack(ModItems.COOKING_POT.get());
        stack.applyComponents(this.collectComponents());
        return stack;
    }

    public static void cookingTick(ServerLevel level, BlockPos pos, BlockState state, CookingPotBlockEntity cookingPot) {
        boolean isHeated = cookingPot.isHeated(level, pos);
        boolean didInventoryChange = false;
        if (isHeated && cookingPot.hasInput()) {
            Optional<RecipeHolder<CookingPotRecipe>> recipe = cookingPot.getMatchingRecipe(new RecipeWrapper(cookingPot.inventory), level);
            if (recipe.isPresent() && cookingPot.canCook(recipe.get().value())) {
                didInventoryChange = cookingPot.processCooking(recipe.get(), cookingPot);
            } else {
                cookingPot.cookTime = Mth.clamp(cookingPot.cookTime - 2, 0, cookingPot.cookTimeTotal);
            }
        } else if (cookingPot.cookTime > 0) {
            cookingPot.cookTime = Mth.clamp(cookingPot.cookTime - 2, 0, cookingPot.cookTimeTotal);
        }

        ItemStack mealStack = cookingPot.getMeal();
        if (!mealStack.isEmpty()) {
            if (!cookingPot.doesMealHaveContainer(mealStack)) {
                cookingPot.moveMealToOutput();
                didInventoryChange = true;
            } else if (!cookingPot.inventory.getStackInSlot(7).isEmpty()) {
                cookingPot.useStoredContainersOnMeal();
                didInventoryChange = true;
            }
        }

        if (didInventoryChange) {
            cookingPot.inventoryChanged();
        }
    }

    public static void animationTick(Level level, BlockPos pos, BlockState state, CookingPotBlockEntity cookingPot) {
        if (cookingPot.isHeated(level, pos)) {
            RandomSource random = level.getRandom();
            if (random.nextFloat() < 0.2F) {
                double x = pos.getX() + 0.5 + (random.nextDouble() * 0.6 - 0.3);
                double y = pos.getY() + 0.7;
                double z = pos.getZ() + 0.5 + (random.nextDouble() * 0.6 - 0.3);
                level.addParticle(ParticleTypes.BUBBLE_POP, x, y, z, 0.0, 0.0, 0.0);
            }

            if (random.nextFloat() < 0.05F) {
                double x = pos.getX() + 0.5 + (random.nextDouble() * 0.4 - 0.2);
                double y = pos.getY() + 0.5;
                double z = pos.getZ() + 0.5 + (random.nextDouble() * 0.4 - 0.2);
                double motionY = random.nextBoolean() ? 0.015 : 0.005;
                level.addParticle(ModParticleTypes.STEAM.get(), x, y, z, 0.0, motionY, 0.0);
            }
        }
    }

    private Optional<RecipeHolder<CookingPotRecipe>> getMatchingRecipe(RecipeWrapper inventoryWrapper, ServerLevel level) {
        if (level == null) {
            return Optional.empty();
        } else {
            return this.hasInput() ? this.quickCheck.getRecipeFor(inventoryWrapper, level) : Optional.empty();
        }
    }

    public ItemStack getContainer() {
        ItemStack mealStack = this.getMeal();
        if (!mealStack.isEmpty() && !this.mealContainerStack.isEmpty()) {
            return this.mealContainerStack;
        } else {
            return !mealStack.getRecipeRemainder().isEmpty() ? mealStack.getRecipeRemainder() : ItemStack.EMPTY;
        }
    }

    private boolean hasInput() {
        for (int i = 0; i < 6; i++) {
            if (!this.inventory.getStackInSlot(i).isEmpty()) {
                return true;
            }
        }

        return false;
    }

    protected boolean canCook(CookingPotRecipe recipe) {
        if (this.hasInput()) {
            ItemStack resultStack = recipe.assemble(new RecipeWrapper(this.inventory), this.level.registryAccess());
            if (resultStack.isEmpty()) {
                return false;
            } else {
                ItemStack storedMealStack = this.inventory.getStackInSlot(6);
                if (storedMealStack.isEmpty()) {
                    return true;
                } else if (!ItemStack.isSameItemSameComponents(storedMealStack, resultStack)) {
                    return false;
                } else {
                    return storedMealStack.getCount() + resultStack.getCount() <= Math.max(64, storedMealStack.getMaxStackSize())
                        ? true
                        : storedMealStack.getCount() + resultStack.getCount() <= resultStack.getMaxStackSize();
                }
            }
        } else {
            return false;
        }
    }

    private boolean processCooking(RecipeHolder<CookingPotRecipe> recipe, CookingPotBlockEntity cookingPot) {
        this.cookTime++;
        this.cookTimeTotal = recipe.value().getCookTime();
        if (this.cookTime < this.cookTimeTotal) {
            return false;
        }

        this.cookTime = 0;
        if (recipe.value().container() != null) {
            this.mealContainerStack = recipe.value().container().copy();
        }

        ItemStack resultStack = recipe.value().assemble(new RecipeWrapper(this.inventory), this.level.registryAccess());
        ItemStack storedMealStack = this.inventory.getStackInSlot(6);
        if (storedMealStack.isEmpty()) {
            this.inventory.setStackInSlot(6, resultStack.copy());
        } else if (ItemStack.isSameItemSameComponents(storedMealStack, resultStack)) {
            storedMealStack.grow(resultStack.getCount());
        }

        cookingPot.setRecipeUsed(recipe);

        for (int i = 0; i < 6; i++) {
            ItemStack slotStack = this.inventory.getStackInSlot(i);
            if (!slotStack.getRecipeRemainder().isEmpty()) {
                this.ejectIngredientRemainder(slotStack.getRecipeRemainder().copy());
            } else if (INGREDIENT_REMAINDER_OVERRIDES.containsKey(slotStack.getItem())) {
                this.ejectIngredientRemainder(INGREDIENT_REMAINDER_OVERRIDES.get(slotStack.getItem()).getDefaultInstance());
            }

            if (!slotStack.isEmpty()) {
                slotStack.shrink(1);
            }
        }

        return true;
    }

    protected void ejectIngredientRemainder(ItemStack remainderStack) {
        Direction direction = this.getBlockState().getValue(CookingPotBlock.FACING).getCounterClockWise();
        double x = this.worldPosition.getX() + 0.5 + direction.getStepX() * 0.25;
        double y = this.worldPosition.getY() + 0.7;
        double z = this.worldPosition.getZ() + 0.5 + direction.getStepZ() * 0.25;
        ItemUtils.spawnItemEntity(this.level, remainderStack, x, y, z, direction.getStepX() * 0.08F, 0.25, direction.getStepZ() * 0.08F);
    }

    @Override
    public void setRecipeUsed(@Nullable RecipeHolder<?> recipe) {
        if (recipe != null) {
            ResourceKey<Recipe<?>> recipeID = recipe.id();
            this.usedRecipeTracker.addTo(recipeID, 1);
        }
    }

    @Override
    public @Nullable RecipeHolder<?> getRecipeUsed() {
        return null;
    }

    @Override
    public void awardUsedRecipes(Player player, List<ItemStack> items) {
        if (player.level() instanceof ServerLevel serverLevel) {
            List<RecipeHolder<?>> usedRecipes = this.getUsedRecipesAndPopExperience(serverLevel, player.position());
            player.awardRecipes(usedRecipes);
            this.usedRecipeTracker.clear();
        }
    }

    public List<RecipeHolder<?>> getUsedRecipesAndPopExperience(ServerLevel level, Vec3 pos) {
        List<RecipeHolder<?>> list = Lists.newArrayList();

        for (Entry<ResourceKey<Recipe<?>>> entry : this.usedRecipeTracker.object2IntEntrySet()) {
            level.recipeAccess().byKey(entry.getKey()).ifPresent(recipe -> {
                list.add((RecipeHolder<?>)recipe);
                splitAndSpawnExperience(level, pos, entry.getIntValue(), ((CookingPotRecipe)recipe.value()).getExperience());
            });
        }

        return list;
    }

    private static void splitAndSpawnExperience(ServerLevel level, Vec3 pos, int craftedAmount, float experience) {
        int expTotal = Mth.floor(craftedAmount * experience);
        float expFraction = Mth.frac(craftedAmount * experience);
        if (expFraction != 0.0F && Math.random() < expFraction) {
            expTotal++;
        }

        ExperienceOrb.award(level, pos, expTotal);
    }

    public boolean isHeated() {
        return this.isHeated(Objects.requireNonNull(this.level), this.worldPosition);
    }

    public ItemStackHandler getInventory() {
        return this.inventory;
    }

    public ItemStack getMeal() {
        return this.inventory.getStackInSlot(6);
    }

    public NonNullList<ItemStack> getDroppableInventory() {
        NonNullList<ItemStack> drops = NonNullList.create();

        for (int i = 0; i < 9; i++) {
            if (i != 6) {
                drops.add(this.inventory.getStackInSlot(i));
            }
        }

        return drops;
    }

    private void moveMealToOutput() {
        ItemStack mealStack = this.inventory.getStackInSlot(6);
        ItemStack outputStack = this.inventory.getStackInSlot(8);
        int mealCount = Math.min(mealStack.getCount(), mealStack.getMaxStackSize() - outputStack.getCount());
        if (outputStack.isEmpty()) {
            this.inventory.setStackInSlot(8, mealStack.split(mealCount));
        } else if (ItemStack.isSameItemSameComponents(mealStack, outputStack)) {
            mealStack.shrink(mealCount);
            outputStack.grow(mealCount);
        }
    }

    private void useStoredContainersOnMeal() {
        ItemStack mealStack = this.inventory.getStackInSlot(6);
        ItemStack containerInputStack = this.inventory.getStackInSlot(7);
        ItemStack outputStack = this.inventory.getStackInSlot(8);
        if (this.isContainerValid(containerInputStack) && outputStack.getCount() < outputStack.getMaxStackSize()) {
            int smallerStackCount = Math.min(mealStack.getCount(), containerInputStack.getCount());
            int mealCount = Math.min(smallerStackCount, mealStack.getMaxStackSize() - outputStack.getCount());
            if (outputStack.isEmpty()) {
                containerInputStack.shrink(mealCount);
                this.inventory.setStackInSlot(8, mealStack.split(mealCount));
            } else if (ItemStack.isSameItemSameComponents(outputStack, mealStack)) {
                mealStack.shrink(mealCount);
                containerInputStack.shrink(mealCount);
                outputStack.grow(mealCount);
            }
        }
    }

    public ItemStack useHeldItemOnMeal(ItemStack container) {
        if (this.isContainerValid(container) && !this.getMeal().isEmpty()) {
            container.shrink(1);
            return this.getMeal().split(1);
        } else {
            return ItemStack.EMPTY;
        }
    }

    private boolean doesMealHaveContainer(ItemStack meal) {
        return !this.mealContainerStack.isEmpty() || !meal.getRecipeRemainder().isEmpty() && !meal.getRecipeRemainder().copy().isEmpty();
    }

    public boolean isContainerValid(ItemStack containerItem) {
        if (containerItem.isEmpty()) {
            return false;
        } else {
            return !this.mealContainerStack.isEmpty() ? ItemStack.isSameItem(this.mealContainerStack, containerItem) : false;
        }
    }

    @Override
    public Component getName() {
        return this.customName != null ? this.customName : TextUtils.container("cooking_pot");
    }

    @Override
    public Component getDisplayName() {
        return this.getName();
    }

    @Override
    public @Nullable Component getCustomName() {
        return this.customName;
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory player, Player entity) {
        return new CookingPotMenu(id, player, this, this.cookingPotData);
    }

    public @NonNull Storage<ItemVariant> getStorage(@Nullable Direction side) {
        return side != null && !side.equals(Direction.UP) ? this.outputHandler : this.inputHandler;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
    }

    @Override
    public CompoundTag getUpdateTag(Provider registries) {
        try (ScopedCollector scopedCollector = new ScopedCollector(this.problemPath(), LOGGER)) {
            TagValueOutput tagValueOutput = TagValueOutput.createWithContext(scopedCollector, registries);
            this.writeItems(tagValueOutput);
            return tagValueOutput.buildResult();
        }
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter componentInput) {
        super.applyImplicitComponents(componentInput);
        this.customName = componentInput.get(DataComponents.CUSTOM_NAME);
        this.getInventory().setStackInSlot(6, componentInput.getOrDefault(ModDataComponents.MEAL.get(), ItemStackWrapper.EMPTY).getStack());
        this.mealContainerStack = componentInput.getOrDefault(ModDataComponents.CONTAINER.get(), ItemStackWrapper.EMPTY).getStack();
    }

    @Override
    protected void collectImplicitComponents(Builder components) {
        super.collectImplicitComponents(components);
        components.set(DataComponents.CUSTOM_NAME, this.customName);
        if (!this.getMeal().isEmpty()) {
            components.set(ModDataComponents.MEAL.get(), new ItemStackWrapper(this.getMeal()));
        }

        if (!this.getContainer().isEmpty()) {
            components.set(ModDataComponents.CONTAINER.get(), new ItemStackWrapper(this.getContainer()));
        }
    }

    private ItemStackHandler createHandler() {
        return new ItemStackHandler(9) {
            @Override
            protected int getStackLimit(int slot, ItemStack stack) {
                return slot == 6 ? Math.max(64, stack.getMaxStackSize()) : super.getStackLimit(slot, stack);
            }

            @Override
            protected void onContentsChanged(int slot) {
                CookingPotBlockEntity.this.inventoryChanged();
            }

            @Override
            public IntList getInputSlotIndexes() {
                return IntImmutableList.of(IntStream.range(0, 6).toArray());
            }
        };
    }

    private ContainerData createIntArray() {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> CookingPotBlockEntity.this.cookTime;
                    case 1 -> CookingPotBlockEntity.this.cookTimeTotal;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                switch (index) {
                    case 0:
                        CookingPotBlockEntity.this.cookTime = value;
                        break;
                    case 1:
                        CookingPotBlockEntity.this.cookTimeTotal = value;
                }
            }

            @Override
            public int getCount() {
                return 2;
            }
        };
    }

    public BlockPos getScreenOpeningData(ServerPlayer player) {
        return this.getBlockPos();
    }
}
