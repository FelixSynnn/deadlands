package com.deadlands.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * What state a weapon is in, from how much of its durability is left: GOOD (over 60%), WORN (over
 * 25%), FAILING (25% or less; guns start to jam). Shown in the weapon's tooltip.
 */
public enum ItemCondition {
	GOOD("good", ChatFormatting.GREEN),
	WORN("worn", ChatFormatting.YELLOW),
	FAILING("failing", ChatFormatting.RED);

	private final String id;
	private final ChatFormatting colour;

	ItemCondition(String id, ChatFormatting colour) {
		this.id = id;
		this.colour = colour;
	}

	public static ItemCondition of(ItemStack stack) {
		if (!stack.isDamageableItem() || stack.getMaxDamage() <= 0) {
			return GOOD;
		}
		float left = 1.0F - stack.getDamageValue() / (float) stack.getMaxDamage();
		return left > 0.6F ? GOOD : left > 0.25F ? WORN : FAILING;
	}

	/** "Condition: Worn", coloured. */
	public Component line() {
		return Component.translatable("item.deadlands.condition", Component.translatable("item.deadlands.condition." + id).withStyle(colour))
			.withStyle(ChatFormatting.DARK_GRAY);
	}
}
