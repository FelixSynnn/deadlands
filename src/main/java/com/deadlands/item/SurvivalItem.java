package com.deadlands.item;

import com.deadlands.survival.Condition;
import com.deadlands.survival.LocalSurvival;
import com.deadlands.survival.SurvivalAttachments;
import com.deadlands.survival.SurvivalData;
import com.deadlands.survival.SurvivalSystem;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * Food, drink and medicine: an item that is consumed (the eating, drinking or applying comes from
 * its {@code consumable} component, set in {@code DLSurvivalItems}) and then does something to the
 * survivor's body through {@link Use}. The tooltip says what it does
 * ({@code item.deadlands.<id>.desc}).
 */
public class SurvivalItem extends Item {
	/** What using the item does. Runs on the server once the item has been consumed. */
	@FunctionalInterface
	public interface Use {
		void apply(ServerPlayer player, SurvivalData body);
	}

	private final Use use;
	private final int treats;

	public SurvivalItem(Use use, Properties properties) {
		this(use, 0, properties);
	}

	/**
	 * @param treats bits of the {@link Condition}s this item is for; it cannot be used (and so is
	 *               not wasted) while the survivor has none of them. 0 means always usable.
	 */
	public SurvivalItem(Use use, int treats, Properties properties) {
		super(properties);
		this.use = use;
		this.treats = treats;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (treats != 0) {
			int conditions = level.isClientSide() ? LocalSurvival.get().conditions()
				: player instanceof ServerPlayer survivor && SurvivalSystem.applies(survivor) ? SurvivalAttachments.get(survivor).conditions() : treats;
			if ((conditions & treats) == 0) {
				if (!level.isClientSide()) {
					player.displayClientMessage(Component.translatable("message.deadlands.survival.not_needed"), true);
				}
				return InteractionResult.FAIL;
			}
		}
		return super.use(level, player, hand);
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		if (entity instanceof ServerPlayer player && SurvivalSystem.applies(player)) {
			use.apply(player, SurvivalAttachments.get(player));
		}
		return super.finishUsingItem(stack, level, entity);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag) {
		String id = BuiltInRegistries.ITEM.getKey(this).getPath();
		lines.accept(Component.translatable("item.deadlands." + id + ".desc").withStyle(ChatFormatting.GRAY));
	}
}
