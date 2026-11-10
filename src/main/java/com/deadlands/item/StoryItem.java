package com.deadlands.item;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** A quest item: research, samples, boss trophies, helicopter parts. Its tooltip says who wants it ({@code item.deadlands.<id>.desc}). */
public class StoryItem extends Item {
	public StoryItem(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag) {
		lines.accept(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
	}
}
