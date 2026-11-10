package com.deadlands.registry;

import com.deadlands.Deadlands;
import com.deadlands.block.DecalBlock;
import com.deadlands.nest.NestBlock;
import com.deadlands.nest.NestBlockEntity;
import com.deadlands.nest.NestKind;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * Nest cores (one per {@link NestKind}), the biomass that grows around them, the nest block
 * entity type, and the tissue sample nests and some infected drop. Call after {@link DLBlocks}.
 */
public final class DLNests {
	private static final Map<NestKind, Block> NESTS = new EnumMap<>(NestKind.class);

	static {
		for (NestKind kind : NestKind.values()) {
			NESTS.put(kind, DLBlocks.register(kind.blockId(), properties -> new NestBlock(kind, properties),
				BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_BROWN).strength(2.5F, 3.0F).sound(SoundType.SLIME_BLOCK)
					.noOcclusion().pushReaction(PushReaction.BLOCK).lightLevel(state -> kind == NestKind.ADVANCED ? 4 : 0),
				DLTab.CUSTOM_BLOCKS));
		}
	}

	public static final Block BIOMASS = DLBlocks.register("biomass", Block::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_BROWN).strength(1.0F).sound(SoundType.SLIME_BLOCK), DLTab.CUSTOM_BLOCKS);
	public static final Block BIOMASS_GROWTH = DLBlocks.register("biomass_growth", CarpetBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_BROWN).strength(0.2F).sound(SoundType.SLIME_BLOCK).noOcclusion()
			.pushReaction(PushReaction.DESTROY), DLTab.CUSTOM_BLOCKS);
	public static final Block BIOMASS_VEINS = DLBlocks.registerDecal("biomass_veins", DecalBlock::new,
		BlockBehaviour.Properties.of().noCollision().noOcclusion().strength(0.1F).noLootTable().replaceable().pushReaction(PushReaction.DESTROY)
			.sound(SoundType.SLIME_BLOCK));

	public static final BlockEntityType<NestBlockEntity> NEST = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE,
		Deadlands.id("nest"),
		FabricBlockEntityTypeBuilder.create(NestBlockEntity::new, NESTS.values().toArray(Block[]::new)).build()
	);

	public static final Item INFECTED_TISSUE = DLItems.register("infected_tissue", Item::new, new Item.Properties(), DLTab.CRAFTING_MATERIALS);

	private DLNests() {
	}

	public static Block nest(NestKind kind) {
		return NESTS.get(kind);
	}

	public static Map<NestKind, Block> nests() {
		return Collections.unmodifiableMap(NESTS);
	}

	public static void init() {
		// Class loading registers everything above.
	}
}
