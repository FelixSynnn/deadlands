package vectorwing.farmersdelight.common.item;

import java.util.Optional;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.item.v1.EnchantingContext;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipePropertySet;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import vectorwing.farmersdelight.common.block.SkilletBlock;
import vectorwing.farmersdelight.common.block.entity.SkilletBlockEntity;
import vectorwing.farmersdelight.common.item.component.ItemStackWrapper;
import vectorwing.farmersdelight.common.registry.ModDataComponents;
import vectorwing.farmersdelight.common.registry.ModItems;
import vectorwing.farmersdelight.common.registry.ModSounds;
import vectorwing.farmersdelight.common.tag.ModTags;
import vectorwing.farmersdelight.common.utility.ItemUtils;
import vectorwing.farmersdelight.common.utility.TextUtils;

public class SkilletItem extends BlockItem {
    public static final float FLIP_TIME = 12.0F;
    public static final ToolMaterial SKILLET_MATERIAL = ToolMaterial.IRON;
    protected static final Identifier FD_ATTACK_KNOCKBACK_UUID = Identifier.fromNamespaceAndPath("farmersdelight", "base_attack_knockback");

    public SkilletItem(Block block, Properties properties) {
        super(block, properties.durability(SKILLET_MATERIAL.durability()));
    }

    public static ItemAttributeModifiers createAttributes(ToolMaterial material, float attackDamage, float attackSpeed) {
        return ItemAttributeModifiers.builder()
            .add(
                Attributes.ATTACK_DAMAGE,
                new AttributeModifier(BASE_ATTACK_DAMAGE_ID, attackDamage + material.attackDamageBonus(), Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND
            )
            .add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, attackSpeed, Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
            .add(Attributes.ATTACK_KNOCKBACK, new AttributeModifier(FD_ATTACK_KNOCKBACK_UUID, 1.0, Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
            .build();
    }

    @Override
    public boolean allowComponentsUpdateAnimation(Player player, InteractionHand hand, ItemStack oldStack, ItemStack newStack) {
        return !compareComponents(oldStack, newStack, ModDataComponents.SKILLET_FLIP_TIMESTAMP.get())
                && !compareComponents(oldStack, newStack, ModDataComponents.COOKING_TIME_LENGTH.get())
                && !compareComponents(oldStack, newStack, ModDataComponents.SKILLET_INGREDIENT.get())
            ? super.allowComponentsUpdateAnimation(player, hand, oldStack, newStack)
            : false;
    }

    private static boolean compareComponents(ItemStack oldStack, ItemStack newStack, DataComponentType<?> componentType) {
        return oldStack.has(componentType) && newStack.has(componentType) ? !oldStack.get(componentType).equals(newStack.get(componentType)) : false;
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        stack.hurtAndBreak(1, attacker, EquipmentSlot.MAINHAND);
    }

    private static boolean isPlayerNearHeatSource(Player player, LevelReader level) {
        if (player.isOnFire()) {
            return true;
        }

        BlockPos pos = player.blockPosition();

        for (BlockPos nearbyPos : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
            if (level.getBlockState(nearbyPos).is(ModTags.Blocks.HEAT_SOURCES)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void appendHoverText(ItemStack itemStack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag tooltipFlag) {
        builder.accept(TextUtils.PLACEABLE_SNEAKING);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        int fireAspectLevel = ItemUtils.getValidatedEnchantmentLevel(Enchantments.FIRE_ASPECT, entity.level().registryAccess(), stack);
        int cookingTime = stack.getOrDefault(ModDataComponents.COOKING_TIME_LENGTH.get(), 0);
        return SkilletBlock.getSkilletCookingTime(cookingTime, fireAspectLevel);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack skilletStack = player.getItemInHand(hand);
        if (isPlayerNearHeatSource(player, level)) {
            InteractionHand otherHand = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            ItemStack cookingStack = player.getItemInHand(otherHand);
            if (!skilletStack.getOrDefault(ModDataComponents.SKILLET_INGREDIENT.get(), ItemStackWrapper.EMPTY).getStack().isEmpty()) {
                player.startUsingItem(hand);
                return InteractionResult.PASS;
            }

            RecipePropertySet propertySet = level.recipeAccess().propertySet(RecipePropertySet.CAMPFIRE_INPUT);
            if (propertySet.test(cookingStack)) {
                if (player.isUnderWater()) {
                    player.displayClientMessage(TextUtils.item("skillet.underwater"), true);
                    return InteractionResult.PASS;
                }

                if (level instanceof ServerLevel serverLevel) {
                    Optional<RecipeHolder<CampfireCookingRecipe>> recipe = serverLevel.recipeAccess()
                        .getRecipeFor(RecipeType.CAMPFIRE_COOKING, new SingleRecipeInput(cookingStack), serverLevel);
                    if (recipe.isEmpty()) {
                        return InteractionResult.CONSUME;
                    }

                    ItemStack cookingStackCopy = cookingStack.copy();
                    ItemStack cookingStackUnit = cookingStackCopy.split(1);
                    skilletStack.set(ModDataComponents.SKILLET_INGREDIENT.get(), new ItemStackWrapper(cookingStackUnit));
                    skilletStack.set(ModDataComponents.COOKING_TIME_LENGTH.get(), recipe.get().value().cookingTime());
                    player.startUsingItem(hand);
                    player.setItemInHand(otherHand, cookingStackCopy);
                }

                return InteractionResult.CONSUME;
            }

            player.displayClientMessage(TextUtils.item("skillet.how_to_cook"), true);
        }

        return InteractionResult.PASS;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int count) {
        if (entity instanceof Player player) {
            if (stack.has(ModDataComponents.SKILLET_FLIP_TIMESTAMP.get())) {
                long flipTimeStamp = stack.get(ModDataComponents.SKILLET_FLIP_TIMESTAMP.get());
                long l = level.getGameTime() - flipTimeStamp;
                if ((float)l > 12.0F) {
                    stack.remove(ModDataComponents.SKILLET_FLIP_TIMESTAMP.get());
                    stack.set(ModDataComponents.SKILLET_FLIPPED.get(), !stack.getOrDefault(ModDataComponents.SKILLET_FLIPPED.get(), false));
                } else if (level.isClientSide() && (float)l == 4.0F) {
                    level.playSound(
                        player, entity, ModSounds.BLOCK_SKILLET_ADD_FOOD.get(), SoundSource.PLAYERS, 0.4F, level.getRandom().nextFloat() * 0.2F + 0.9F
                    );
                } else if (level.isClientSide() && level.getRandom().nextInt(50) == 0 && (float)l < 4.0F || (float)l > 9.0F) {
                    level.playSound(null, entity, ModSounds.BLOCK_SKILLET_SIZZLE.get(), SoundSource.PLAYERS, 0.4F, level.getRandom().nextFloat() * 0.2F + 0.9F);
                }
            } else if (level.isClientSide() && level.getRandom().nextInt(50) == 0) {
                level.playSound(null, entity, ModSounds.BLOCK_SKILLET_SIZZLE.get(), SoundSource.PLAYERS, 0.4F, level.getRandom().nextFloat() * 0.2F + 0.9F);
            }
        }
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (entity instanceof Player player) {
            ItemStackWrapper storedStack = stack.getOrDefault(ModDataComponents.SKILLET_INGREDIENT.get(), ItemStackWrapper.EMPTY);
            if (!storedStack.getStack().isEmpty()) {
                ItemStack cookingStack = storedStack.getStack();
                player.getInventory().placeItemBackInInventory(cookingStack);
                stack.remove(ModDataComponents.SKILLET_INGREDIENT.get());
                stack.remove(ModDataComponents.COOKING_TIME_LENGTH.get());
                stack.remove(ModDataComponents.SKILLET_FLIP_TIMESTAMP.get());
                stack.remove(ModDataComponents.SKILLET_FLIPPED.get());
            }
        }

        return super.releaseUsing(stack, level, entity, timeLeft);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof Player player && level instanceof ServerLevel serverLevel) {
            ItemStackWrapper storedStack = stack.getOrDefault(ModDataComponents.SKILLET_INGREDIENT.get(), ItemStackWrapper.EMPTY);
            if (!storedStack.getStack().isEmpty()) {
                ItemStack cookingStack = storedStack.getStack();
                Optional<RecipeHolder<CampfireCookingRecipe>> cookingRecipe = getCookingRecipe(cookingStack, serverLevel);
                cookingRecipe.ifPresent(recipe -> {
                    ItemStack resultStack = recipe.value().assemble(new SingleRecipeInput(cookingStack), serverLevel.registryAccess());
                    if (!player.getInventory().add(resultStack)) {
                        player.drop(resultStack, false);
                    }

                    if (player instanceof ServerPlayer) {
                        CriteriaTriggers.CONSUME_ITEM.trigger((ServerPlayer)player, stack);
                    }
                });
                InteractionHand otherHand = entity.getUsedItemHand() == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
                ItemStack otherHandStack = player.getItemInHand(otherHand);
                otherHandStack.shrink(1);
                stack.remove(ModDataComponents.SKILLET_INGREDIENT.get());
                stack.remove(ModDataComponents.COOKING_TIME_LENGTH.get());
                stack.remove(ModDataComponents.SKILLET_FLIP_TIMESTAMP.get());
                stack.remove(ModDataComponents.SKILLET_FLIPPED.get());
            }
        }

        return stack;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return stack.has(ModDataComponents.COOKING_TIME_LENGTH.get())
            ? Mth.clamp(
                Math.round(
                    13.0F
                        - (this.getUseDuration(stack, getClientPlayerHack()) + getClientPlayerHack().getUseItemRemainingTicks())
                            * 13.0F
                            / this.getUseDuration(stack, getClientPlayerHack())
                ),
                0,
                13
            )
            : super.getBarWidth(stack);
    }

    @Environment(EnvType.CLIENT)
    private static Player getClientPlayerHack() {
        return Minecraft.getInstance().player;
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return stack.has(ModDataComponents.COOKING_TIME_LENGTH.get()) ? 16747343 : super.getBarColor(stack);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return super.isBarVisible(stack) || stack.has(ModDataComponents.COOKING_TIME_LENGTH.get());
    }

    public static Optional<RecipeHolder<CampfireCookingRecipe>> getCookingRecipe(ItemStack stack, ServerLevel level) {
        return stack.isEmpty() ? Optional.empty() : level.recipeAccess().getRecipeFor(RecipeType.CAMPFIRE_COOKING, new SingleRecipeInput(stack), level);
    }

    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, @Nullable Player player, ItemStack stack, BlockState state) {
        super.updateCustomBlockEntityTag(pos, level, player, stack, state);
        if (level.getBlockEntity(pos) instanceof SkilletBlockEntity skillet) {
            skillet.setSkilletItem(stack);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity entity) {
        if (!level.isClientSide() && state.getDestroySpeed(level, pos) != 0.0F) {
            stack.hurtAndBreak(1, entity, EquipmentSlot.MAINHAND);
        }

        return true;
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        Player player = context.getPlayer();
        return player != null && player.isShiftKeyDown() ? super.place(context) : InteractionResult.PASS;
    }

    @Override
    public boolean canBeEnchantedWith(ItemStack stack, Holder<Enchantment> enchantment, EnchantingContext context) {
        return enchantment.is(Enchantments.SWEEPING_EDGE) ? false : super.canBeEnchantedWith(stack, enchantment, context);
    }

    public static class SkilletEvents {
        public static float attackPower = 0.0F;

        public static void playSkilletAttackSound(LivingEntity entity, DamageSource source) {
            if (source.getDirectEntity() instanceof LivingEntity livingEntity) {
                if (livingEntity.getItemInHand(InteractionHand.MAIN_HAND).is(ModItems.SKILLET.get())) {
                    float pitch = 0.9F + livingEntity.getRandom().nextFloat() * 0.2F;
                    if (livingEntity instanceof Player player) {
                        if (attackPower > 0.8F) {
                            player.playSound(ModSounds.ITEM_SKILLET_ATTACK_STRONG.get(), 1.0F, pitch);
                        } else {
                            player.playSound(ModSounds.ITEM_SKILLET_ATTACK_WEAK.get(), 0.8F, 0.9F);
                        }
                    } else {
                        livingEntity.playSound(ModSounds.ITEM_SKILLET_ATTACK_STRONG.get(), 1.0F, pitch);
                    }

                    attackPower = 0.0F;
                }
            }
        }
    }
}
