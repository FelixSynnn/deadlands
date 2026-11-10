package com.deadlands.item;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** A melee weapon: sword-like attributes from its properties, plus a description and its condition in the tooltip. */
public class MeleeItem extends Item {
	public MeleeItem(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag) {
		String id = BuiltInRegistries.ITEM.getKey(this).getPath();
		lines.accept(Component.translatable("item.deadlands." + id + ".desc").withStyle(ChatFormatting.GRAY));
		lines.accept(ItemCondition.of(stack).line());
	}
}
