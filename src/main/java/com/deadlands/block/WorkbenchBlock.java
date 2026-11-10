package com.deadlands.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A survivor's workbench: a 3x3 crafting grid wherever one stands (garages, workshops, the works).
 * It takes the same recipes as a crafting table, the DEADLANDS ones included. The screen is still
 * Minecraft's crafting screen; a DEADLANDS crafting screen comes with the custom inventory.
 */
public class WorkbenchBlock extends FacingDecorBlock {
	public static final MapCodec<WorkbenchBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		DecorKind.CODEC.fieldOf("kind").forGetter(WorkbenchBlock::kind),
		propertiesCodec()
	).apply(instance, WorkbenchBlock::new));

	public WorkbenchBlock(DecorKind kind, Properties properties) {
		super(kind, properties);
	}

	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide()) {
			player.openMenu(new SimpleMenuProvider((id, inventory, opener) -> new Menu(id, inventory, ContainerLevelAccess.create(level, pos), pos),
				Component.translatable("container.deadlands.workbench")));
		}
		return InteractionResult.SUCCESS;
	}

	/** The vanilla crafting menu, except that it stays open at a workbench instead of a crafting table. */
	private static final class Menu extends CraftingMenu {
		private final BlockPos pos;

		Menu(int id, Inventory inventory, ContainerLevelAccess access, BlockPos pos) {
			super(id, inventory, access);
			this.pos = pos;
		}

		@Override
		public boolean stillValid(Player player) {
			return player.level().getBlockState(pos).getBlock() instanceof WorkbenchBlock && player.isWithinBlockInteractionRange(pos, 4.0);
		}
	}
}
