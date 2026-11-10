package com.deadlands.item;

import com.deadlands.bridge.ClientBridge;
import com.deadlands.network.DLNetworking;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/** The survivor's field journal: day log, threat forecast and settings. Opens the journal screen. */
public class SurvivorJournalItem extends Item {
	public SurvivorJournalItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide()) {
			ClientBridge.openJournal();
		} else if (player instanceof ServerPlayer serverPlayer) {
			DLNetworking.sendStatus(serverPlayer);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		tooltip.accept(Component.translatable("item.deadlands.survivor_journal.desc").withStyle(ChatFormatting.GRAY));
		tooltip.accept(Component.translatable("item.deadlands.survivor_journal.hint").withStyle(ChatFormatting.DARK_GRAY));
	}
}
