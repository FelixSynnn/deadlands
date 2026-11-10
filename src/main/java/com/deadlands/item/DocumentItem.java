package com.deadlands.item;

import com.deadlands.bridge.ClientBridge;
import com.deadlands.registry.DLStory;
import com.deadlands.story.Archive;
import com.deadlands.story.Lore;
import com.deadlands.story.LoreEntry;
import java.util.List;
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

/**
 * A piece of lore: a journal page, a report, a newspaper, a tape transcript or a photograph. Its
 * name is the entry's title; using it opens it to read and adds it to the survivor's archive. The
 * item keeps the entry's id ({@link DLStory#LORE}) and, for a trail note, the place written in it.
 */
public class DocumentItem extends Item {
	public DocumentItem(Properties properties) {
		super(properties);
	}

	@Override
	public Component getName(ItemStack stack) {
		String id = stack.get(DLStory.LORE);
		return id == null ? super.getName(stack) : Component.translatable(Lore.titleKey(id));
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		String id = stack.get(DLStory.LORE);
		if (id == null || Lore.get(id) == null) {
			return InteractionResult.PASS;
		}
		List<String> args = stack.getOrDefault(DLStory.LORE_ARGS, List.of());
		if (level.isClientSide()) {
			ClientBridge.openDocument(id, args);
		} else if (player instanceof ServerPlayer survivor) {
			Archive.discover(survivor, id, args);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag) {
		String id = stack.get(DLStory.LORE);
		LoreEntry entry = id == null ? null : Lore.get(id);
		if (entry != null) {
			lines.accept(Component.translatable(entry.category().translationKey()).withStyle(ChatFormatting.GRAY));
			if (entry.part() > 0) {
				lines.accept(Component.translatable("lore.deadlands.part", entry.part(), Lore.series(entry.series()).size()).withStyle(ChatFormatting.DARK_GRAY));
			}
		}
	}
}
