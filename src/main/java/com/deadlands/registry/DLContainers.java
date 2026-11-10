package com.deadlands.registry;

import com.deadlands.container.ContainerKind;
import com.deadlands.container.LootContainerBlock;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/** One block per {@link ContainerKind}. */
public final class DLContainers {
	private static final Map<ContainerKind, LootContainerBlock> BLOCKS = new EnumMap<>(ContainerKind.class);

	public static final LootContainerBlock MILITARY_CRATE = register(ContainerKind.MILITARY_CRATE,
		BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(2.5F).sound(SoundType.WOOD).ignitedByLava());
	public static final LootContainerBlock FILING_CABINET = register(ContainerKind.FILING_CABINET, metal(MapColor.COLOR_GRAY));
	public static final LootContainerBlock MEDICAL_CABINET = register(ContainerKind.MEDICAL_CABINET, metal(MapColor.SNOW));
	public static final LootContainerBlock SUPPLY_LOCKER = register(ContainerKind.SUPPLY_LOCKER, metal(MapColor.COLOR_GREEN));
	public static final LootContainerBlock TOOLBOX = register(ContainerKind.TOOLBOX, metal(MapColor.COLOR_RED));
	public static final LootContainerBlock KITCHEN_CUPBOARD = register(ContainerKind.KITCHEN_CUPBOARD, wood(MapColor.TERRACOTTA_WHITE));
	public static final LootContainerBlock FRIDGE = register(ContainerKind.FRIDGE, metal(MapColor.SNOW));
	public static final LootContainerBlock DRESSER = register(ContainerKind.DRESSER, wood(MapColor.WOOD));
	public static final LootContainerBlock SAFE = register(ContainerKind.SAFE,
		BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(8.0F, 1200.0F).sound(SoundType.METAL).requiresCorrectToolForDrops());
	public static final LootContainerBlock SCHOOL_DESK = register(ContainerKind.SCHOOL_DESK, wood(MapColor.WOOD));

	private DLContainers() {
	}

	private static BlockBehaviour.Properties wood(MapColor colour) {
		return BlockBehaviour.Properties.of().mapColor(colour).strength(2.0F, 3.0F).sound(SoundType.WOOD).ignitedByLava();
	}

	private static BlockBehaviour.Properties metal(MapColor colour) {
		return BlockBehaviour.Properties.of().mapColor(colour).strength(3.0F, 6.0F).sound(SoundType.METAL).noOcclusion();
	}

	private static LootContainerBlock register(ContainerKind kind, BlockBehaviour.Properties properties) {
		LootContainerBlock block = (LootContainerBlock) DLBlocks.register(kind.id(), p -> new LootContainerBlock(kind, p), properties.noOcclusion(), DLTab.CONTAINERS);
		BLOCKS.put(kind, block);
		return block;
	}

	public static LootContainerBlock get(ContainerKind kind) {
		return BLOCKS.get(kind);
	}

	public static Map<ContainerKind, LootContainerBlock> all() {
		return Collections.unmodifiableMap(BLOCKS);
	}

	public static void init() {
		// Class loading registers everything above.
	}
}
