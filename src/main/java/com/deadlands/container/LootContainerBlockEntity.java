package com.deadlands.container;

import com.deadlands.config.ConfigManager;
import com.deadlands.config.DeadlandsConfig;
import com.deadlands.registry.DLBlockEntities;
import com.deadlands.registry.DLSounds;
import java.util.List;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jspecify.annotations.Nullable;

/**
 * Inventory, loot, lock and animation state of a {@link LootContainerBlock}.
 *
 * <p>Loot: a structure stores only a loot table id ({@code LootTable}). It is rolled exactly once,
 * the first time anyone opens (or breaks) the container, then cleared; from then on the contents
 * are ordinary saved items, so unloading and reloading chunks never regenerates loot. The roll
 * honours the Loot settings: container fill, damaged containers holding less, and the rarity
 * multiplier for an extra roll on the kind's rare table.
 */
public class LootContainerBlockEntity extends RandomizableContainerBlockEntity implements ExtendedScreenHandlerFactory<ContainerOpenData> {
	private static final int EVENT_OPEN_COUNT = 1;
	private static final float AJAR_OPENNESS = 0.16F;

	private final ContainerKind kind;
	private NonNullList<ItemStack> items;
	private ContainerLock lock = ContainerLock.NONE;

	// Client-side animation.
	private float openness;
	private float previousOpenness;
	private boolean shouldBeOpen;

	private final ContainerOpenersCounter openers = new ContainerOpenersCounter() {
		@Override
		protected void onOpen(Level level, BlockPos pos, BlockState state) {
			playSound(level, pos, kind.openSound().value());
		}

		@Override
		protected void onClose(Level level, BlockPos pos, BlockState state) {
			playSound(level, pos, kind.closeSound().value());
		}

		@Override
		protected void openerCountChanged(Level level, BlockPos pos, BlockState state, int previous, int current) {
			level.blockEvent(pos, state.getBlock(), EVENT_OPEN_COUNT, current);
		}

		@Override
		public boolean isOwnContainer(Player player) {
			return player.containerMenu instanceof DLContainerMenu menu && menu.getContainer() == LootContainerBlockEntity.this;
		}
	};

	public LootContainerBlockEntity(BlockPos pos, BlockState state) {
		super(DLBlockEntities.LOOT_CONTAINER, pos, state);
		this.kind = state.getBlock() instanceof LootContainerBlock block ? block.kind() : ContainerKind.MILITARY_CRATE;
		this.items = NonNullList.withSize(kind.slots(), ItemStack.EMPTY);
	}

	public ContainerKind kind() {
		return kind;
	}

	public ContainerCondition condition() {
		return getBlockState().hasProperty(LootContainerBlock.CONDITION) ? getBlockState().getValue(LootContainerBlock.CONDITION) : ContainerCondition.CLEAN;
	}

	// ---------------------------------------------------------------------------------- inventory

	@Override
	public int getContainerSize() {
		return kind.slots();
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return items;
	}

	@Override
	protected void setItems(NonNullList<ItemStack> list) {
		this.items = list;
	}

	@Override
	protected Component getDefaultName() {
		return Component.translatable("container.deadlands." + kind.id());
	}

	@Override
	protected AbstractContainerMenu createMenu(int syncId, Inventory inventory) {
		return new DLContainerMenu(syncId, inventory, this, kind.rows(), kind.style());
	}

	@Override
	public ContainerOpenData getScreenOpeningData(ServerPlayer player) {
		return new ContainerOpenData(kind.rows(), kind.style().ordinal());
	}

	// ---------------------------------------------------------------------------------- persistence

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
		if (!tryLoadLootTable(input)) {
			ContainerHelper.loadAllItems(input, items);
		}
		this.lock = input.read("deadlands_lock", ContainerLock.CODEC).orElse(ContainerLock.NONE);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (!trySaveLootTable(output)) {
			ContainerHelper.saveAllItems(output, items);
		}
		if (lock.isLocked()) {
			output.store("deadlands_lock", ContainerLock.CODEC, lock);
		}
	}

	// ---------------------------------------------------------------------------------- loot

	@Override
	public void unpackLootTable(@Nullable Player player) {
		ResourceKey<LootTable> table = getLootTable();
		if (table == null || level == null || level.isClientSide() || level.getServer() == null) {
			super.unpackLootTable(player);
			return;
		}
		long seed = getLootTableSeed();
		RandomSource random = seed == 0L ? RandomSource.create() : RandomSource.create(seed * 31L + 17L);
		super.unpackLootTable(player);

		DeadlandsConfig.Loot settings = ConfigManager.get().loot;
		double fill = settings.containerFill * (condition() == ContainerCondition.DAMAGED ? 0.5 : 1.0);
		while (fill > 1.0) {
			if (random.nextDouble() < Math.min(1.0, fill - 1.0)) {
				rollInto(table, player, random.nextLong());
			}
			fill -= 1.0;
		}
		if (fill < 1.0) {
			for (int i = 0; i < items.size(); i++) {
				if (!items.get(i).isEmpty() && random.nextDouble() > fill) {
					items.set(i, ItemStack.EMPTY);
				}
			}
		}
		if (random.nextDouble() < kind.rareChance() * settings.rarityMultiplier) {
			rollInto(kind.rareLootTable(), player, random.nextLong());
		}
		if (player instanceof net.minecraft.server.level.ServerPlayer survivor) {
			if (random.nextFloat() < com.deadlands.skill.Skills.extraLootChance(survivor)) {
				rollInto(table, player, random.nextLong());
			}
			com.deadlands.skill.Skills.award(survivor, com.deadlands.skill.Skill.SCAVENGER, com.deadlands.skill.Skills.XP_CONTAINER);
		}
		// The story's additions: lore, trail notes, quest items, radio parts.
		if (level instanceof net.minecraft.server.level.ServerLevel server) {
			for (ItemStack extra : com.deadlands.story.StoryLoot.extras(server, getBlockPos(), table.identifier().getPath(),
				player instanceof net.minecraft.server.level.ServerPlayer survivor ? survivor : null, random)) {
				for (int i = 0; i < items.size(); i++) {
					if (items.get(i).isEmpty()) {
						items.set(i, extra);
						break;
					}
				}
			}
		}
		setChanged();
	}

	private void rollInto(ResourceKey<LootTable> table, @Nullable Player player, long seed) {
		setLootTable(table, seed);
		super.unpackLootTable(player);
	}

	// ---------------------------------------------------------------------------------- locks

	public ContainerLock lock() {
		return lock;
	}

	public void setLock(ContainerLock lock) {
		this.lock = lock;
		setChanged();
		if (level != null && !level.isClientSide()) {
			// Clients need the lock to predict mining (locked containers resist survival players).
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	/** Clients get the lock only, never the contents. */
	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		CompoundTag tag = new CompoundTag();
		if (lock.isLocked()) {
			tag.store("deadlands_lock", ContainerLock.CODEC, lock);
		}
		return tag;
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	/**
	 * True if the player may open the container. A locked container opens (and stays unlocked)
	 * when the player carries the right key or keycard in either hand.
	 */
	public boolean tryUnlock(ServerPlayer player) {
		if (!lock.isLocked() || player.isCreative() && player.isShiftKeyDown()) {
			return true;
		}
		for (ItemStack held : List.of(player.getMainHandItem(), player.getOffhandItem())) {
			if (lock.opensWith(held)) {
				player.displayClientMessage(Component.translatable("lock.deadlands.unlocked", held.getHoverName()), true);
				playSound(level, worldPosition, DLSounds.CONTAINER_UNLOCK.value());
				setLock(ContainerLock.NONE);
				return true;
			}
		}
		player.displayClientMessage(Component.translatable("lock.deadlands.locked", getDisplayName(), lock.requirement()), true);
		playSound(level, worldPosition, DLSounds.CONTAINER_LOCKED.value());
		return false;
	}

	// ---------------------------------------------------------------------------------- opening

	@Override
	public void startOpen(ContainerUser user) {
		if (!remove && !user.getLivingEntity().isSpectator()) {
			if (user.getLivingEntity() instanceof ServerPlayer player) {
				com.deadlands.npc.Settlements.containerOpened(player, getBlockPos());
			}
			openers.incrementOpeners(user.getLivingEntity(), getLevel(), getBlockPos(), getBlockState(), user.getContainerInteractionRange());
		}
	}

	@Override
	public void stopOpen(ContainerUser user) {
		if (!remove && !user.getLivingEntity().isSpectator()) {
			openers.decrementOpeners(user.getLivingEntity(), getLevel(), getBlockPos(), getBlockState());
		}
	}

	@Override
	public List<ContainerUser> getEntitiesWithContainerOpen() {
		return openers.getEntitiesWithContainerOpen(getLevel(), getBlockPos());
	}

	public void recheckOpen() {
		if (!remove) {
			openers.recheckOpeners(getLevel(), getBlockPos(), getBlockState());
		}
	}

	public int openerCount() {
		return openers.getOpenerCount();
	}

	@Override
	public boolean triggerEvent(int id, int param) {
		if (id == EVENT_OPEN_COUNT) {
			shouldBeOpen = param > 0;
			return true;
		}
		return super.triggerEvent(id, param);
	}

	private static void playSound(@Nullable Level level, BlockPos pos, SoundEvent sound) {
		if (level != null) {
			level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, sound, SoundSource.BLOCKS, 0.7F,
				0.92F + level.random.nextFloat() * 0.16F);
		}
	}

	// ---------------------------------------------------------------------------------- animation (client)

	public static void clientTick(Level level, BlockPos pos, BlockState state, LootContainerBlockEntity container) {
		container.previousOpenness = container.openness;
		float rest = state.getValue(LootContainerBlock.AJAR) ? AJAR_OPENNESS : 0.0F;
		float target = container.shouldBeOpen ? 1.0F : rest;
		float speed = container.kind.openSpeed();
		if (container.openness < target) {
			container.openness = Math.min(target, container.openness + speed);
		} else if (container.openness > target) {
			container.openness = Math.max(target, container.openness - speed * 1.4F);
		}
	}

	/** 0 closed .. 1 fully open, eased so heavy lids start slowly and settle. */
	public float openness(float partialTick) {
		float t = Mth.lerp(partialTick, previousOpenness, openness);
		return 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
	}
}
