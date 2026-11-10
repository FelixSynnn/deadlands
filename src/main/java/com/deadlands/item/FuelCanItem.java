package com.deadlands.item;

import com.deadlands.registry.DLDataComponents;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * A jerry can. It holds up to {@link #CAPACITY} litres ({@code deadlands:fuel} on the item); use it
 * on a fuel pump that still has fuel to fill it, and on a vehicle to pour it into the tank.
 */
public class FuelCanItem extends Item {
	public static final float CAPACITY = 20.0F;

	public FuelCanItem(Properties properties) {
		super(properties);
	}

	public static float litres(ItemStack can) {
		return Mth.clamp(can.getOrDefault(DLDataComponents.FUEL, 0.0F), 0.0F, CAPACITY);
	}

	public static void setLitres(ItemStack can, float litres) {
		can.set(DLDataComponents.FUEL, Mth.clamp(litres, 0.0F, CAPACITY));
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return true;
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return Math.round(13.0F * litres(stack) / CAPACITY);
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return 0xC8902E;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag) {
		lines.accept(Component.translatable("item.deadlands.fuel_can.desc").withStyle(ChatFormatting.GRAY));
		lines.accept(Component.translatable("item.deadlands.fuel_can.litres", Math.round(litres(stack)), Math.round(CAPACITY)).withStyle(ChatFormatting.DARK_GRAY));
	}
}
