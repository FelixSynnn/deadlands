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

	/** Blades cut (a sharp, wet impact); everything else is blunt (a heavy thud). */
	private static final java.util.Set<String> BLADES = java.util.Set.of("machete", "rusty_machete", "fire_axe", "survival_knife", "hunting_knife");

	/** Phase 4: the impact everyone nearby hears, by kind of weapon. */
	@Override
	public void hurtEnemy(ItemStack stack, net.minecraft.world.entity.LivingEntity target, net.minecraft.world.entity.LivingEntity attacker) {
		super.hurtEnemy(stack, target, attacker);
		if (!attacker.level().isClientSide()) {
			boolean blade = BLADES.contains(BuiltInRegistries.ITEM.getKey(this).getPath());
			attacker.level().playSound(null, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
				(blade ? com.deadlands.registry.DLSounds.WEAPON_HIT_BLADE : com.deadlands.registry.DLSounds.WEAPON_HIT_BLUNT).value(),
				net.minecraft.sounds.SoundSource.PLAYERS, 0.9F, 0.9F + attacker.getRandom().nextFloat() * 0.2F);
		}
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag) {
		String id = BuiltInRegistries.ITEM.getKey(this).getPath();
		lines.accept(Component.translatable("item.deadlands." + id + ".desc").withStyle(ChatFormatting.GRAY));
		lines.accept(ItemCondition.of(stack).line());
	}
}
