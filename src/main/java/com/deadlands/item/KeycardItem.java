package com.deadlands.item;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** Access keycard. Opens keycard locks of its level or lower (1 green, 2 blue, 3 red). */
public class KeycardItem extends Item {
	private final int accessLevel;

	public KeycardItem(int accessLevel, Properties properties) {
		super(properties);
		this.accessLevel = accessLevel;
	}

	public int accessLevel() {
		return accessLevel;
	}

	public static Component levelName(int level) {
		return Component.translatable("item.deadlands.keycard.level." + Math.max(1, Math.min(3, level)));
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		tooltip.accept(Component.translatable("item.deadlands.keycard.desc", accessLevel).withStyle(ChatFormatting.GRAY));
	}
}
