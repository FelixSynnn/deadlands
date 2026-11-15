package com.deadlands.world.town;

import com.deadlands.config.ConfigManager;
import com.deadlands.container.ContainerLock;
import com.deadlands.container.LootContainerBlock;
import com.deadlands.container.LootContainerBlockEntity;
import com.deadlands.nest.NestKind;
import com.deadlands.registry.DLBlocks;
import com.deadlands.registry.DLNests;
import com.deadlands.world.DLWorldgen;
import com.deadlands.world.Hashing;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * A building from a structure template (data/deadlands/structure/town/...), turned to face its
 * street and set at street level. After the template is placed it gets a foundation down to the
 * ground, the hillside above its footprint is cleared, and the town's condition wears it down:
 * holes in the roof, broken walls, blood and rubble on the floors. Damage is decided per block
 * position, so the parts of a building in different chunks agree.
 */
public class TownBuildingPiece extends TemplateStructurePiece {
	private final TownCondition condition;
	private final long seed;

	public TownBuildingPiece(StructureTemplateManager templates, Identifier template, BlockPos origin, Rotation rotation, TownCondition condition, long seed) {
		super(DLWorldgen.TOWN_BUILDING, 0, templates, template, template.toString(), settings(rotation), origin);
		this.condition = condition;
		this.seed = seed;
	}

	public TownBuildingPiece(StructureTemplateManager templates, CompoundTag tag) {
		super(DLWorldgen.TOWN_BUILDING, tag, templates, id -> settings(tag.read("Rot", Rotation.LEGACY_CODEC).orElse(Rotation.NONE)));
		this.condition = TownCondition.byId(tag.getStringOr("Cond", "abandoned"));
		this.seed = tag.getLongOr("Seed", 0L);
	}

	private static StructurePlaceSettings settings(Rotation rotation) {
		return new StructurePlaceSettings()
			.setRotation(rotation)
			.setMirror(Mirror.NONE)
			.addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK)
			.setLiquidSettings(LiquidSettings.IGNORE_WATERLOGGING);
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		super.addAdditionalSaveData(context, tag);
		tag.store("Rot", Rotation.LEGACY_CODEC, placeSettings.getRotation());
		tag.putString("Cond", condition.id());
		tag.putLong("Seed", seed);
	}

	/** The template this building was made from, e.g. {@code deadlands:town/clinic}. */
	public String templateName() {
		return templateName;
	}

	@Override
	protected void handleDataMarker(String marker, BlockPos pos, ServerLevelAccessor level, RandomSource random, BoundingBox box) {
		// Templates carry their contents directly; no data markers are used.
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
		BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
		BoundingBox box = getBoundingBox();
		int floor = box.minY();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int minX = Math.max(box.minX(), chunkBox.minX());
		int maxX = Math.min(box.maxX(), chunkBox.maxX());
		int minZ = Math.max(box.minZ(), chunkBox.minZ());
		int maxZ = Math.min(box.maxZ(), chunkBox.maxZ());
		// Clear the hillside above the footprint so the building is not buried.
		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
				for (int y = box.maxY() + 1; y < top && y <= box.maxY() + 12; y++) {
					pos.set(x, y, z);
					level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}
		super.postProcess(level, structureManager, generator, random, chunkBox, chunkPos, pivot);
		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				// Foundation: fill under the floor down to solid ground.
				for (int y = floor - 1; y >= floor - 12 && y > level.getMinY(); y--) {
					pos.set(x, y, z);
					BlockState below = level.getBlockState(pos);
					if (below.isSolid() && below.getFluidState().isEmpty()) {
						break;
					}
					level.setBlock(pos, y == floor - 1 ? DLBlocks.CONCRETE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
				wearColumn(level, pos, box, x, z);
			}
		}
		settleContainers(level, pos, box, minX, maxX, minZ, maxZ);
		infest(level, pos, box, minX, maxX, minZ, maxZ);
		populate(level, pos, box, minX, maxX, minZ, maxZ);
		terminal(level, pos, box, minX, maxX, minZ, maxZ);
	}

	/**
	 * Every clinic, emergency room, pharmacy and hospital has a Kessler terminal left running, on the ground floor toward
	 * a corner (the first free floor spot spiralling out from a point a third of the way across), facing the room. Unit
	 * 07 lives in it ({@code story.MythosTerminal}). Decided from the building alone, so every chunk agrees.
	 */
	private void terminal(WorldGenLevel level, BlockPos.MutableBlockPos pos, BoundingBox box, int minX, int maxX, int minZ, int maxZ) {
		if (!(templateName.contains("clinic") || templateName.contains("emergency") || templateName.contains("pharmacy")
			|| templateName.contains("hospital"))) {
			return;
		}
		int y = box.minY() + 1;
		int cx = (box.minX() + box.maxX()) / 2;
		int cz = (box.minZ() + box.maxZ()) / 2;
		int sx = box.minX() + (box.maxX() - box.minX()) / 3;
		int sz = box.minZ() + (box.maxZ() - box.minZ()) / 3;
		for (int radius = 0; radius <= 5; radius++) {
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
						continue;
					}
					int x = sx + dx;
					int z = sz + dz;
					pos.set(x, y, z);
					boolean free = level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()
						&& level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP);
					if (!free) {
						continue;
					}
					if (x >= minX && x <= maxX && z >= minZ && z <= maxZ) {
						net.minecraft.core.Direction facing = Math.abs(cx - x) > Math.abs(cz - z)
							? (cx > x ? net.minecraft.core.Direction.EAST : net.minecraft.core.Direction.WEST)
							: (cz > z ? net.minecraft.core.Direction.SOUTH : net.minecraft.core.Direction.NORTH);
						level.setBlock(pos, com.deadlands.registry.DLMythos.TERMINAL.defaultBlockState()
							.setValue(com.deadlands.block.TerminalBlock.FACING, facing), Block.UPDATE_CLIENTS);
					}
					return; // decided even when another chunk holds the spot
				}
			}
		}
	}

	/**
	 * Some buildings became nests: a nest core on the ground floor near the middle and biomass
	 * creeping across the floor around it. How many depends on the town's condition (a third of an
	 * overrun town's buildings, hardly any in a survivor town); garages, warehouses and the works get
	 * industrial nests, military towns military ones, a rare overrun building an advanced nest.
	 * Every decision is a hash of the building's seed or of the block position, so the parts of a
	 * building in different chunks agree.
	 */
	/**
	 * Residents. In survivor holdouts, military checkpoints and bandit towns most buildings get a
	 * spawn marker that becomes one survivor the first time the chunk ticks. Who lives where follows
	 * the building: a doctor in the clinic, a mechanic in the garage, a trader in the shops, the
	 * leader in the town hall, guards at the police station, farmers in barns and farmhouses,
	 * settlers elsewhere. Bandit towns hold bandits; checkpoints hold guards.
	 */
	private void populate(WorldGenLevel level, BlockPos.MutableBlockPos pos, BoundingBox box, int minX, int maxX, int minZ, int maxZ) {
		double chance = switch (condition) {
			case SURVIVOR -> 0.8;
			case BANDIT -> 0.7;
			case MILITARY -> 0.5;
			default -> 0.0;
		};
		long roll = Hashing.mix(seed ^ 0x6E7063L);
		if (Hashing.unit(roll) >= chance) {
			return;
		}
		com.deadlands.block.SpawnMarkerBlock.Spawn spawn;
		if (condition == TownCondition.BANDIT) {
			spawn = com.deadlands.block.SpawnMarkerBlock.Spawn.NPC_BANDIT;
		} else if (condition == TownCondition.MILITARY) {
			spawn = com.deadlands.block.SpawnMarkerBlock.Spawn.NPC_GUARD;
		} else if (templateName.contains("clinic") || templateName.contains("pharmacy") || templateName.contains("emergency")) {
			spawn = com.deadlands.block.SpawnMarkerBlock.Spawn.NPC_DOCTOR;
		} else if (templateName.contains("garage") || templateName.contains("factory")) {
			spawn = com.deadlands.block.SpawnMarkerBlock.Spawn.NPC_MECHANIC;
		} else if (templateName.contains("store") || templateName.contains("grocery") || templateName.contains("diner") || templateName.contains("saloon")) {
			spawn = com.deadlands.block.SpawnMarkerBlock.Spawn.NPC_TRADER;
		} else if (templateName.contains("town_hall") || templateName.contains("courthouse")) {
			spawn = com.deadlands.block.SpawnMarkerBlock.Spawn.NPC_LEADER;
		} else if (templateName.contains("police") || templateName.contains("sheriff") || templateName.contains("fire_station")) {
			spawn = com.deadlands.block.SpawnMarkerBlock.Spawn.NPC_GUARD;
		} else if (templateName.contains("barn") || templateName.contains("farmhouse")) {
			spawn = com.deadlands.block.SpawnMarkerBlock.Spawn.NPC_FARMER;
		} else {
			spawn = Hashing.unit(Hashing.mix(roll ^ 0x77L)) < 0.2 ? com.deadlands.block.SpawnMarkerBlock.Spawn.NPC_GUARD : com.deadlands.block.SpawnMarkerBlock.Spawn.NPC_SETTLER;
		}
		int y = box.minY() + 1;
		int cx = (box.minX() + box.maxX()) / 2;
		int cz = (box.minZ() + box.maxZ()) / 2;
		// The first free floor spot spiralling out from the middle that this chunk holds.
		for (int radius = 0; radius <= 4; radius++) {
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
						continue;
					}
					int x = cx + dx;
					int z = cz + dz;
					pos.set(x, y, z);
					boolean free = level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()
						&& level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP);
					if (!free) {
						continue;
					}
					if (x >= minX && x <= maxX && z >= minZ && z <= maxZ) {
						level.setBlock(pos, com.deadlands.registry.DLMarkers.SPAWN_MARKER.defaultBlockState()
							.setValue(com.deadlands.block.SpawnMarkerBlock.SPAWN, spawn), Block.UPDATE_CLIENTS);
					}
					return; // the spot is decided even when another chunk holds it
				}
			}
		}
	}

	private void infest(WorldGenLevel level, BlockPos.MutableBlockPos pos, BoundingBox box, int minX, int maxX, int minZ, int maxZ) {
		double chance = switch (condition) {
			case OVERRUN -> 0.35;
			case ABANDONED -> 0.2;
			case EVACUATED -> 0.12;
			case MILITARY -> 0.1;
			case BANDIT -> 0.05;
			case SURVIVOR -> 0.03;
		};
		long roll = Hashing.mix(seed ^ 0x9E57L);
		if (Hashing.unit(roll) >= chance) {
			return;
		}
		NestKind kind;
		if (condition == TownCondition.OVERRUN && Hashing.unit(Hashing.mix(roll)) < 0.08) {
			kind = NestKind.ADVANCED;
		} else if (templateName.contains("garage") || templateName.contains("warehouse") || templateName.contains("factory")) {
			kind = NestKind.INDUSTRIAL;
		} else if (condition == TownCondition.MILITARY) {
			kind = NestKind.MILITARY;
		} else {
			kind = NestKind.RESIDENTIAL;
		}
		int y = box.minY() + 1;
		int nx = (box.minX() + box.maxX()) / 2 + (int) Math.floorMod(roll >> 8, 5L) - 2;
		int nz = (box.minZ() + box.maxZ()) / 2 + (int) Math.floorMod(roll >> 16, 5L) - 2;
		for (int x = Math.max(minX, nx - 3); x <= Math.min(maxX, nx + 3); x++) {
			for (int z = Math.max(minZ, nz - 3); z <= Math.min(maxZ, nz + 3); z++) {
				pos.set(x, y, z);
				if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)) {
					continue;
				}
				if (x == nx && z == nz) {
					level.setBlock(pos, DLNests.nest(kind).defaultBlockState(), Block.UPDATE_CLIENTS);
					continue;
				}
				double d = Math.hypot(x - nx, z - nz);
				if (Hashing.unit(Hashing.of(seed, x, z, 0xB10AL)) < 0.85 - d * 0.2) {
					level.setBlock(pos, DLNests.BIOMASS_GROWTH.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}
	}

	/**
	 * Settles the containers: in towns whose condition left little behind (survivors and bandits took
	 * what they could carry) some are already emptied and hanging open; of the rest, a share
	 * ({@code loot.lockedShare}) start locked with a level 1 keycard or the maintenance key. Locks the
	 * template set itself are kept, and locked containers are never emptied.
	 */
	private void settleContainers(WorldGenLevel level, BlockPos.MutableBlockPos pos, BoundingBox box, int minX, int maxX, int minZ, int maxZ) {
		double emptied = Math.max(0.0, 1.0 - condition.lootMultiplier()) * 0.8;
		double locked = ConfigManager.get().loot.lockedShare;
		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				for (int y = box.minY() + 1; y <= box.maxY(); y++) {
					pos.set(x, y, z);
					if (!(level.getBlockEntity(pos) instanceof LootContainerBlockEntity container) || container.lock().isLocked()) {
						continue;
					}
					BlockState state = level.getBlockState(pos);
					if (!state.hasProperty(LootContainerBlock.AJAR)) {
						continue;
					}
					if (Hashing.unit(Hashing.of(seed, x, z, 0x5CA7L + y)) < emptied) {
						container.setLootTable(null);
						level.setBlock(pos, state.setValue(LootContainerBlock.AJAR, true), Block.UPDATE_CLIENTS);
					} else if (Hashing.unit(Hashing.of(seed, x, z, 0x10C4L + y)) < locked) {
						boolean card = Hashing.unit(Hashing.of(seed, x, z, 0xCA4DL + y)) < 0.6;
						container.setLock(card ? ContainerLock.keycard(1) : ContainerLock.key("maintenance"));
						level.setBlock(pos, state.setValue(LootContainerBlock.AJAR, false), Block.UPDATE_CLIENTS);
					}
				}
			}
		}
	}

	/** The condition's damage for one column of the building. */
	private void wearColumn(WorldGenLevel level, BlockPos.MutableBlockPos pos, BoundingBox box, int x, int z) {
		boolean edge = x == box.minX() || x == box.maxX() || z == box.minZ() || z == box.maxZ();
		for (int y = box.minY() + 1; y <= box.maxY(); y++) {
			pos.set(x, y, z);
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || state.getBlock() instanceof LootContainerBlock || level.getBlockEntity(pos) != null) {
				continue;
			}
			double roll = Hashing.unit(Hashing.of(seed, x, z, y));
			boolean roof = y == box.maxY() || level.getBlockState(pos.above()).isAir() && y >= box.maxY() - 1;
			if (roof && roll < condition.damage() * 0.18) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			} else if (edge && y > box.minY() + 1 && roll < condition.damage() * 0.05) {
				level.setBlock(pos, roll < condition.damage() * 0.02 ? DLBlocks.RUBBLE.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
		// Blood and rubble on the ground floor.
		pos.set(x, box.minY() + 1, z);
		if (!edge && level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).isSolid()) {
			double roll = Hashing.unit(Hashing.of(seed, x, z, 0xB100DL));
			if (roll < condition.blood() * 0.02) {
				Block decal = roll < condition.blood() * 0.007 ? DLBlocks.DRIED_BLOOD_POOL : DLBlocks.DRIED_BLOOD_SPOTS;
				level.setBlock(pos, decal.defaultBlockState().setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR)
					.setValue(com.deadlands.block.DecalBlock.TURN, (int) Math.floorMod(Hashing.mix((long) x * 31 + z), 4L)), Block.UPDATE_CLIENTS);
			} else if (roll > 1.0 - condition.damage() * 0.03) {
				level.setBlock(pos, DLBlocks.RUBBLE.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
	}
}
