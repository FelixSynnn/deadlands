package vectorwing.farmersdelight.common.block.entity;

import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter.ScopedCollector;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.RecipeManager.CachedCheck;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEvent.Context;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec2;
import org.jspecify.annotations.Nullable;
import vectorwing.farmersdelight.FarmersDelight;
import vectorwing.farmersdelight.common.block.AbstractStoveBlock;
import vectorwing.farmersdelight.common.utility.ItemUtils;
import vectorwing.farmersdelight.refabricated.inventory.ItemStackHandler;

public abstract class AbstractStoveBlockEntity extends BlockEntity implements Clearable {
    private final ItemStackHandler items;
    private final int[] cookingTimes;
    private final int[] cookingTimesTotal;
    private final CachedCheck<SingleRecipeInput, ? extends AbstractCookingRecipe> quickRecipeLookup;

    protected AbstractStoveBlockEntity(
        BlockEntityType<?> blockEntityType, BlockPos blockPos, BlockState blockState, RecipeType<? extends AbstractCookingRecipe> recipeType
    ) {
        super(blockEntityType, blockPos, blockState);
        int inventorySlotCount = this.getInventorySlotCount();
        this.items = createHandler(inventorySlotCount);
        this.cookingTimes = new int[inventorySlotCount];
        this.cookingTimesTotal = new int[inventorySlotCount];
        this.quickRecipeLookup = RecipeManager.createCheck(recipeType);
    }

    protected abstract int getInventorySlotCount();

    @Deprecated
    public abstract Vec2 getStoveItemOffset(int var1);

    public ItemStackHandler getItems() {
        return this.items;
    }

    @Override
    public void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.items.deserialize(input.childOrEmpty("Inventory"));
        if (input.getIntArray("CookingTimes").isPresent()) {
            int[] arrayCookingTimes = input.getIntArray("CookingTimes").get();
            System.arraycopy(arrayCookingTimes, 0, this.cookingTimes, 0, Math.min(this.cookingTimes.length, arrayCookingTimes.length));
        }

        if (input.getIntArray("CookingTotalTimes").isPresent()) {
            int[] arrayCookingTimesTotal = input.getIntArray("CookingTotalTimes").get();
            System.arraycopy(arrayCookingTimesTotal, 0, this.cookingTimesTotal, 0, Math.min(this.cookingTimesTotal.length, arrayCookingTimesTotal.length));
        }
    }

    @Override
    public void saveAdditional(ValueOutput output) {
        this.writeItems(output);
        output.putIntArray("CookingTimes", this.cookingTimesTotal);
        output.putIntArray("CookingTotalTimes", this.cookingTimes);
    }

    private void writeItems(ValueOutput output) {
        super.saveAdditional(output);
        this.items.serialize(output.child("Inventory"));
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (this.level != null) {
            ItemUtils.dropItems(this.level, pos, this.getItems());
        }

        super.preRemoveSideEffects(pos, state);
    }

    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(Provider registries) {
        try (ScopedCollector scopedCollector = new ScopedCollector(this.problemPath(), FarmersDelight.LOGGER)) {
            TagValueOutput tagValueOutput = TagValueOutput.createWithContext(scopedCollector, registries);
            this.writeItems(tagValueOutput);
            return tagValueOutput.buildResult();
        }
    }

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state, AbstractStoveBlockEntity stoveEntity) {
        if (!stoveEntity.isEmpty()) {
            if (stoveEntity.shouldDropItems()) {
                stoveEntity.dropAllItems();
                stoveEntity.setChanged();
            } else {
                if (state.getValue(AbstractStoveBlock.LIT)) {
                    stoveEntity.cookAndOutputItems(level);
                } else {
                    stoveEntity.coolItems();
                }
            }
        }
    }

    private void cookAndOutputItems(ServerLevel level) {
        boolean didChange = false;

        for (int i = 0; i < this.items.getSlotCount(); i++) {
            ItemStack ingredient = this.items.getStackInSlot(i);
            if (!ingredient.isEmpty()) {
                didChange = true;
                this.cookingTimesTotal[i]++;
                if (this.cookingTimesTotal[i] >= this.cookingTimes[i]) {
                    SingleRecipeInput input = new SingleRecipeInput(ingredient);
                    ItemStack result = this.quickRecipeLookup
                        .getRecipeFor(input, level)
                        .map(recipe -> recipe.value().assemble(input, level.registryAccess()))
                        .orElse(ingredient);
                    if (result.isItemEnabled(level.enabledFeatures())) {
                        ItemUtils.spawnItemEntity(
                            level,
                            result.copy(),
                            this.worldPosition.getX() + 0.5,
                            this.worldPosition.getY() + 1.0,
                            this.worldPosition.getZ() + 0.5,
                            level.getRandom().nextGaussian() * 0.01F,
                            0.1F,
                            level.getRandom().nextGaussian() * 0.01F
                        );
                        this.items.setStackInSlot(i, ItemStack.EMPTY);
                        BlockState state = this.getBlockState();
                        level.sendBlockUpdated(this.worldPosition, state, state, 3);
                        level.gameEvent(GameEvent.BLOCK_CHANGE, this.worldPosition, Context.of(state));
                    }
                }
            }
        }

        if (didChange) {
            this.setChanged();
        }
    }

    private void coolItems() {
        assert this.level != null;
        boolean didChange = false;

        for (int i = 0; i < this.items.getSlotCount(); i++) {
            int thisItemCookingProgress = this.cookingTimesTotal[i];
            if (thisItemCookingProgress > 0) {
                didChange = true;
                this.cookingTimesTotal[i] = Mth.clamp(thisItemCookingProgress - 2, 0, this.cookingTimes[i]);
            }
        }

        if (didChange) {
            this.setChanged();
        }
    }

    public Optional<? extends RecipeHolder<? extends AbstractCookingRecipe>> getCookingRecipe(ItemStack itemStack) {
        return this.level instanceof ServerLevel serverLevel
            ? this.quickRecipeLookup.getRecipeFor(new SingleRecipeInput(itemStack), serverLevel)
            : Optional.empty();
    }

    public int getNextEmptySlot() {
        return IntStream.range(0, this.items.getSlotCount()).filter(i -> this.items.getStackInSlot(i).isEmpty()).findFirst().orElse(-1);
    }

    public boolean placeFood(@Nullable Entity entity, ItemStack foodStackToPlace, RecipeHolder<? extends AbstractCookingRecipe> recipe) {
        assert this.level != null;
        int emptySlotIndex = this.getNextEmptySlot();
        if (emptySlotIndex < 0) {
            return false;
        }

        assert this.items.getStackInSlot(emptySlotIndex).isEmpty();
        this.cookingTimes[emptySlotIndex] = recipe.value().cookingTime();
        this.cookingTimesTotal[emptySlotIndex] = 0;
        this.items.setStackInSlot(emptySlotIndex, foodStackToPlace.split(1));
        BlockState state = this.getBlockState();
        this.level.sendBlockUpdated(this.worldPosition, state, state, 3);
        this.level.gameEvent(GameEvent.BLOCK_CHANGE, this.worldPosition, Context.of(entity, state));
        this.setChanged();
        return true;
    }

    public boolean shouldDropItems() {
        return this.level == null ? false : AbstractStoveBlock.isStoveTopCovered(this.level, this.worldPosition, this.getBlockState());
    }

    public Stream<ItemStack> streamItems() {
        return IntStream.range(0, this.items.getSlotCount()).mapToObj(this.items::getStackInSlot);
    }

    public boolean isEmpty() {
        return this.streamItems().allMatch(ItemStack::isEmpty);
    }

    public boolean isFull() {
        return this.streamItems().noneMatch(ItemStack::isEmpty);
    }

    public void dropAllItems() {
        if (this.level != null) {
            ItemUtils.dropItems(this.level, this.worldPosition, this.items);
            BlockState state = this.getBlockState();
            this.level.sendBlockUpdated(this.worldPosition, state, state, 3);
            this.level.gameEvent(GameEvent.BLOCK_CHANGE, this.worldPosition, Context.of(state));
        }
    }

    public void extinguish() {
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
            this.setChanged();
        }
    }

    public void ignite() {
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
            this.setChanged();
        }
    }

    @Override
    public void clearContent() {
        this.streamItems().forEach(stack -> stack.setCount(0));
    }

    private static ItemStackHandler createHandler(int slotCount) {
        return new ItemStackHandler(slotCount) {
            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }
        };
    }
}
