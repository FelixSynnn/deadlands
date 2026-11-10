package com.deadlands.item;

import com.deadlands.container.ContainerLock;
import com.deadlands.registry.DLDataComponents;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** A key with a tag ({@code deadlands:key_id}); it opens containers locked with the same id. */
public class KeyItem extends Item {
	public KeyItem(Properties properties) {
		super(properties);
	}

	@Override
	public Component getName(ItemStack stack) {
		String id = stack.get(DLDataComponents.KEY_ID);
		return id == null || id.isEmpty() ? super.getName(stack) : Component.translatable("item.deadlands.key.named", ContainerLock.keyLabel(id));
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		String id = stack.get(DLDataComponents.KEY_ID);
		tooltip.accept(Component.translatable(id == null || id.isEmpty() ? "item.deadlands.key.blank" : "item.deadlands.key.desc")
			.withStyle(ChatFormatting.GRAY));
	}
}
