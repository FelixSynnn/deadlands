package com.deadlands.item;

import com.deadlands.registry.DLDataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The flashlight every survivor carries (Hard Rule 4). Use it to switch it on or off; held in
 * either hand and on, it throws a cone of light ahead (drawn on the client by the terrain and entity
 * shaders, see {@code FlashlightClient}).
 *
 * <p>The charge is {@value #MAX_CHARGE} seconds of light. It drains only while held and on, and
 * recharges whenever it is off (a second of charge per second), so a survivor is never left in the
 * dark for good: half a minute off gives a quarter of a minute of light. The bar shows the charge.
 */
public class FlashlightItem extends Item {
	public static final int MAX_CHARGE = 1800;

	public FlashlightItem(Properties properties) {
		super(properties.stacksTo(1).component(DLDataComponents.BATTERY, MAX_CHARGE).component(DLDataComponents.FLASHLIGHT_ON, false));
	}

	public static boolean isOn(ItemStack stack) {
		return stack.getOrDefault(DLDataComponents.FLASHLIGHT_ON, false);
	}

	public static int charge(ItemStack stack) {
		return stack.getOrDefault(DLDataComponents.BATTERY, MAX_CHARGE);
	}

	/** On and with charge left: it is shining. */
	public static boolean shining(ItemStack stack) {
		return stack.getItem() instanceof FlashlightItem && isOn(stack) && charge(stack) > 0;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!level.isClientSide()) {
			boolean on = !isOn(stack) && charge(stack) > 0;
			stack.set(DLDataComponents.FLASHLIGHT_ON, on);
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
				on ? SoundEvents.STONE_BUTTON_CLICK_ON : SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.PLAYERS, 0.35F, on ? 1.6F : 1.3F);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return true;
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return Math.round(13.0F * charge(stack) / MAX_CHARGE);
	}

	@Override
	public int getBarColor(ItemStack stack) {
		float fraction = Mth.clamp(charge(stack) / (float) MAX_CHARGE, 0.0F, 1.0F);
		// Pale yellow while charged, turning orange-red as it runs down.
		int red = 232;
		int green = Math.round(90 + 110 * fraction);
		int blue = Math.round(60 + 40 * fraction);
		return red << 16 | green << 8 | blue;
	}
}
