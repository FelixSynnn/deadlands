package com.deadlands.test;

import com.deadlands.Deadlands;
import com.deadlands.block.RoadLineBlock;
import com.deadlands.client.ui.DLContainerScreen;
import com.deadlands.container.ContainerCondition;
import com.deadlands.container.ContainerKind;
import com.deadlands.container.ContainerLock;
import com.deadlands.container.LootContainerBlock;
import com.deadlands.container.LootContainerBlockEntity;
import com.deadlands.registry.DLBlocks;
import com.deadlands.registry.DLContainers;
import com.deadlands.registry.DLDataComponents;
import com.deadlands.registry.DLItems;
import com.mojang.datafixers.util.Pair;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * Milestone 2 in the real client: a materials-and-decals scene, every container opened with a
 * real right-click (one locked until its key is used), and both structures placed from their
 * templates. Returns what the reload check needs to prove nothing regenerates.
 */
final class MilestoneTwoScenes {
	record Result(BlockPos crate, List<String> crateContents, BlockPos shackCabinet) {
	}

	private MilestoneTwoScenes() {
	}

	static Result run(ClientGameTestContext context, TestSingleplayerContext world) {
		TestServerContext server = world.getServer();
		server.runCommand("time add 6000");
		server.runCommand("weather clear");
		BlockPos origin = server.computeOnServer(s -> {
			ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
			int x = player.getBlockX() + 24;
			int z = player.getBlockZ() + 6;
			return new BlockPos(x, s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
		});
		int y = origin.getY();

		// Scene 1: road surfaces, the material wall and decals.
		server.runOnServer(s -> buildMaterials(s.overworld(), origin));
		look(context, world, origin.getX() + 0.5, y, origin.getZ() + 1.5, 0, 12);
		hud(context, false);
		shot(context, "deadlands-09-materials-and-decals");

		// Scene 2: one of each container; the locker is left ajar, the medical cabinet is locked.
		BlockPos row = origin.offset(22, 0, 0);
		server.runOnServer(s -> buildContainers(s.overworld(), row));
		look(context, world, row.getX() + 0.5, y, row.getZ() - 0.5, 0, 16);
		shot(context, "deadlands-10-containers-closed");

		BlockPos crate = row.offset(-4, 0, 4);
		open(context, world, crate, "military_crate", true);
		List<String> crateContents = server.computeOnServer(s -> contents(s, crate));
		check(!crateContents.isEmpty(), "opening the crate rolls its loot (" + crateContents.size() + " stacks)");
		check(server.computeOnServer(s -> ((LootContainerBlockEntity) s.overworld().getBlockEntity(crate)).getLootTable() == null),
			"the crate's loot table is cleared once rolled");
		open(context, world, row.offset(-2, 0, 4), "filing_cabinet", false);

		BlockPos cabinet = row.offset(0, 0, 4);
		look(context, world, cabinet.getX() + 0.5, y, cabinet.getZ() - 1.8, 0, 28);
		hud(context, true);
		click(context, cabinet);
		context.waitTicks(12);
		shot(context, "deadlands-12-locked-cabinet");
		check(server.computeOnServer(s -> ((LootContainerBlockEntity) s.overworld().getBlockEntity(cabinet)).lock().isLocked()),
			"a locked cabinet stays locked without its key");
		// Breaking it open by hand does not work either: hold the real attack button for 2 seconds.
		context.getInput().holdMouseFor(GLFW.GLFW_MOUSE_BUTTON_LEFT, 40);
		context.waitTicks(5);
		check(server.computeOnServer(s -> s.overworld().getBlockEntity(cabinet) instanceof LootContainerBlockEntity c && c.lock().isLocked()),
			"a survivor cannot break a locked cabinet by hand");
		// The key goes into hotbar slot 2 (slot 1 holds the journal) and is picked with the real number key.
		server.runOnServer(s -> {
			ItemStack key = new ItemStack(DLItems.KEY);
			key.set(DLDataComponents.KEY_ID, "ranger_station");
			s.getPlayerList().getPlayers().getFirst().getInventory().setItem(1, key);
		});
		context.waitTicks(2);
		context.getInput().pressKey(options -> options.keyHotbarSlots[1]);
		context.waitTicks(2);
		check(server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getMainHandItem().is(DLItems.KEY)),
			"pressing hotbar key 2 puts the ranger station key in hand");
		hud(context, false);
		open(context, world, cabinet, "medical_cabinet", true);
		check(server.computeOnServer(s -> !((LootContainerBlockEntity) s.overworld().getBlockEntity(cabinet)).lock().isLocked()),
			"the matching key unlocks the cabinet for good");
		context.getInput().pressKey(options -> options.keyHotbarSlots[0]);
		open(context, world, row.offset(2, 0, 4), "supply_locker", false);
		open(context, world, row.offset(4, 0, 4), "toolbox", false);

		// Scene 3: the structures, placed from their templates without rotation. The camera works in
		// spectator mode so it can hold still in the air (a survivor would fall during the wait).
		server.runCommand("gamemode spectator @p");
		BlockPos shack = origin.offset(44, -1, 0);
		server.runCommand("place template deadlands:roadside_shack " + shack.getX() + " " + shack.getY() + " " + shack.getZ());
		look(context, world, shack.getX() + 4.5, y, shack.getZ() - 5.5, 0, 6);
		shot(context, "deadlands-13-ranger-shack");
		// The front wall is template row z=1 (the doorway); the room is x 2..6, z 2..6.
		look(context, world, shack.getX() + 4.5, y, shack.getZ() + 2.15, -15, 24);
		shot(context, "deadlands-14-ranger-shack-inside");
		look(context, world, shack.getX() + 4.5, y, shack.getZ() + 5.9, 180, 20);
		shot(context, "deadlands-14-ranger-shack-door");
		BlockPos shackCabinet = shack.offset(6, 1, 3);
		check(server.computeOnServer(s -> s.overworld().getBlockEntity(shackCabinet) instanceof LootContainerBlockEntity c
			&& c.lock().isLocked() && c.getLootTable() != null), "the placed shack's medical cabinet is locked and still holds unrolled loot");

		BlockPos cache = origin.offset(64, -1, 0);
		server.runCommand("place template deadlands:supply_cache " + cache.getX() + " " + cache.getY() + " " + cache.getZ());
		look(context, world, cache.getX() + 5.5, y + 6, cache.getZ() - 3.5, 0, 45);
		shot(context, "deadlands-15-supply-cache");
		server.runCommand("gamemode survival @p");
		hud(context, true);
		return new Result(crate, crateContents, shackCabinet);
	}

	// ---------------------------------------------------------------------------------- natural generation

	/** What the world generator placed: the structure's box and the containers found inside it. */
	record Found(BoundingBox box, int containers, int locked, int distance, String list) {
	}

	/**
	 * A normal (non-flat) world with a fixed seed, structures on: both ruins must be found by the
	 * world generator itself, with every container and lock intact after placement and rotation.
	 */
	static void naturalStructures(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false)
			.adjustSettings(creator -> creator.setSeed("deadlands")).create()) {
			world.getClientWorld().waitForChunksRender();
			TestServerContext server = world.getServer();
			server.runCommand("difficulty peaceful");
			server.runCommand("time set 6000");
			server.runCommand("weather clear");
			server.runCommand("gamemode spectator @p");
			hud(context, false);
			int[][] expected = {{4, 1}, {5, 1}}; // containers, locked: from the templates
			String[] ids = {"roadside_shack", "supply_cache"};
			for (int i = 0; i < ids.length; i++) {
				String id = ids[i];
				Found found = server.computeOnServer(s -> locate(s, id));
				String what = "a " + id.replace('_', ' ') + " generates naturally in a normal world";
				check(found != null, what + (found == null ? "" : " (" + found.distance() + " blocks from spawn)"));
				// World events (Milestone 9) may leave a crate of their own nearby, so extra unlocked containers are allowed.
				check(found.containers() >= expected[i][0] && found.locked() == expected[i][1],
					"the natural " + id.replace('_', ' ') + " keeps its " + expected[i][0] + " containers and its lock ("
						+ found.containers() + " containers, " + found.locked() + " locked:" + found.list() + ")");
				BlockPos c = found.box().getCenter();
				double camX = c.getX() + 0.5 - 8.0, camZ = c.getZ() + 0.5 - 8.0, camY = found.box().maxY() + 8.0;
				float yaw = (float) Math.toDegrees(Math.atan2(-8.0, 8.0));
				float pitch = (float) Math.toDegrees(Math.atan2(camY + 1.62 - c.getY(), Math.hypot(8.0, 8.0)));
				look(context, world, camX, camY, camZ, yaw, pitch);
				context.waitTicks(20);
				shot(context, "deadlands-16-natural-" + id);
			}
			hud(context, true);
		}
	}

	static Found locate(MinecraftServer server, String id) {
		ServerLevel level = server.overworld();
		Holder<Structure> structure = server.registryAccess().lookupOrThrow(Registries.STRUCTURE)
			.getOrThrow(ResourceKey.create(Registries.STRUCTURE, Deadlands.id(id)));
		BlockPos from = server.getPlayerList().getPlayers().getFirst().blockPosition();
		Pair<BlockPos, Holder<Structure>> hit = level.getChunkSource().getGenerator()
			.findNearestMapStructure(level, HolderSet.direct(structure), from, 64, false);
		if (hit == null) {
			return null;
		}
		ChunkPos chunk = new ChunkPos(hit.getFirst());
		StructureStart start = level.getChunk(chunk.x, chunk.z).getStartForStructure(structure.value());
		if (start == null || !start.isValid()) {
			return null;
		}
		BoundingBox box = start.getBoundingBox();
		int containers = 0;
		int locked = 0;
		StringBuilder list = new StringBuilder();
		for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (level.getBlockEntity(pos) instanceof LootContainerBlockEntity container) {
				containers++;
				list.append(' ').append(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).getPath())
					.append('@').append(pos.getX() - box.minX()).append(',').append(pos.getY() - box.minY()).append(',').append(pos.getZ() - box.minZ());
				for (Structure other : level.structureManager().getAllStructuresAt(pos).keySet()) {
					list.append('[').append(server.registryAccess().lookupOrThrow(Registries.STRUCTURE).getKey(other)).append(']');
				}
				if (container.lock().isLocked() && container.getLootTable() != null) {
					locked++;
				}
			}
		}
		int distance = (int) Math.sqrt(from.distSqr(box.getCenter().atY(from.getY())));
		Deadlands.LOGGER.info("Natural {} at {} ({} pieces): containers{}", id, box, start.getPieces().size(), list);
		return new Found(box, containers, locked, distance, list.toString());
	}

	// ---------------------------------------------------------------------------------- scene builders

	private static void buildMaterials(ServerLevel level, BlockPos o) {
		int y = o.getY();
		for (int x = -9; x <= 9; x++) {
			for (int z = -1; z <= 11; z++) {
				for (int h = 0; h <= 5; h++) {
					level.setBlock(new BlockPos(o.getX() + x, y + h, o.getZ() + z), Blocks.AIR.defaultBlockState(), 2);
				}
				BlockState floor;
				if (z >= 7) {
					floor = DLBlocks.SIDEWALK.defaultBlockState();
				} else if (x == 0) {
					floor = DLBlocks.ASPHALT_YELLOW_LINE.defaultBlockState().setValue(RoadLineBlock.AXIS, Direction.Axis.Z);
				} else if (x == -5 || x == 5) {
					floor = DLBlocks.ASPHALT_WHITE_LINE.defaultBlockState().setValue(RoadLineBlock.AXIS, Direction.Axis.Z);
				} else {
					floor = (Math.floorMod(x * 31 + z * 17, 5) == 0 ? DLBlocks.CRACKED_ASPHALT : DLBlocks.ASPHALT).defaultBlockState();
				}
				level.setBlock(new BlockPos(o.getX() + x, y - 1, o.getZ() + z), floor, 2);
			}
		}
		List<Block> wall = List.of(
			DLBlocks.CONCRETE, DLBlocks.CRACKED_CONCRETE, DLBlocks.STAINED_CONCRETE, DLBlocks.MOSSY_CONCRETE,
			DLBlocks.WEATHERED_BRICKS, DLBlocks.SOOTY_BRICKS, DLBlocks.PLASTER_WALL, DLBlocks.PEELING_PLASTER,
			DLBlocks.HOSPITAL_TILES, DLBlocks.CRACKED_HOSPITAL_TILES, DLBlocks.BOARDED_PLANKS, DLBlocks.WEATHERED_SIDING,
			DLBlocks.RUSTED_SHEET_METAL, DLBlocks.CORRUGATED_METAL, DLBlocks.DIAMOND_PLATE, DLBlocks.RUBBLE,
			DLBlocks.ASH, DLBlocks.DRY_DIRT, DLBlocks.DEAD_GRASS_BLOCK, DLBlocks.BROKEN_GLASS,
			DLBlocks.SIDEWALK, DLBlocks.ASPHALT, DLBlocks.CRACKED_ASPHALT, DLBlocks.CONCRETE);
		for (int i = 0; i < wall.size(); i++) {
			level.setBlock(new BlockPos(o.getX() - 6 + i % 12, y + i / 12, o.getZ() + 10), wall.get(i).defaultBlockState(), 2);
		}
		Block[] slabs = {DLBlocks.ASPHALT_SLAB, DLBlocks.CRACKED_ASPHALT_SLAB, DLBlocks.CONCRETE_SLAB, DLBlocks.SIDEWALK_SLAB};
		for (int i = 0; i < 12; i++) {
			level.setBlock(new BlockPos(o.getX() - 6 + i, y + 2, o.getZ() + 10), slabs[i % 4].defaultBlockState(), 2);
		}
		for (int i = 0; i < slabs.length; i++) {
			level.setBlock(new BlockPos(o.getX() + 7, y, o.getZ() + 2 + i), slabs[i].defaultBlockState(), 2);
		}
		wallDecal(level, o.offset(-3, 1, 9), DLBlocks.BLOODY_HANDPRINT);
		wallDecal(level, o.offset(2, 0, 9), DLBlocks.SURVIVOR_MARK);
		wallDecal(level, o.offset(5, 1, 9), DLBlocks.BLOOD_SMEAR);
		floorDecal(level, o.offset(-2, 0, 5), DLBlocks.DRIED_BLOOD_POOL, 1);
		floorDecal(level, o.offset(-2, 0, 4), DLBlocks.DRAG_MARKS, 0);
		floorDecal(level, o.offset(-2, 0, 3), DLBlocks.DRAG_MARKS, 0);
		floorDecal(level, o.offset(3, 0, 6), DLBlocks.SCORCH_MARK, 2);
		floorDecal(level, o.offset(1, 0, 4), DLBlocks.DRIED_BLOOD_SPOTS, 3);
		floorDecal(level, o.offset(-6, 0, 6), DLBlocks.FADED_STAIN, 0);
	}

	private static void wallDecal(ServerLevel level, BlockPos pos, Block decal) {
		level.setBlock(pos, decal.defaultBlockState().setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.WALL)
			.setValue(FaceAttachedHorizontalDirectionalBlock.FACING, Direction.NORTH), 2);
	}

	private static void floorDecal(ServerLevel level, BlockPos pos, Block decal, int turn) {
		level.setBlock(pos, decal.defaultBlockState().setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR)
			.setValue(com.deadlands.block.DecalBlock.TURN, turn), 2);
	}

	private static void buildContainers(ServerLevel level, BlockPos o) {
		int y = o.getY();
		for (int x = -6; x <= 6; x++) {
			for (int z = -3; z <= 7; z++) {
				for (int h = 0; h <= 4; h++) {
					level.setBlock(new BlockPos(o.getX() + x, y + h, o.getZ() + z), Blocks.AIR.defaultBlockState(), 2);
				}
				level.setBlock(new BlockPos(o.getX() + x, y - 1, o.getZ() + z), DLBlocks.CONCRETE.defaultBlockState(), 2);
			}
			for (int h = 0; h <= 2; h++) {
				Block back = h == 0 ? DLBlocks.SOOTY_BRICKS : DLBlocks.PEELING_PLASTER;
				level.setBlock(new BlockPos(o.getX() + x, y + h, o.getZ() + 6), back.defaultBlockState(), 2);
			}
		}
		placeContainer(level, o.offset(-4, 0, 4), ContainerKind.MILITARY_CRATE, ContainerCondition.CLEAN, false, null);
		placeContainer(level, o.offset(-2, 0, 4), ContainerKind.FILING_CABINET, ContainerCondition.DUSTY, false, null);
		placeContainer(level, o.offset(0, 0, 4), ContainerKind.MEDICAL_CABINET, ContainerCondition.RUSTY, false, ContainerLock.key("ranger_station"));
		placeContainer(level, o.offset(2, 0, 4), ContainerKind.SUPPLY_LOCKER, ContainerCondition.DAMAGED, true, null);
		placeContainer(level, o.offset(4, 0, 4), ContainerKind.TOOLBOX, ContainerCondition.CLEAN, false, null);
	}

	private static void placeContainer(ServerLevel level, BlockPos pos, ContainerKind kind, ContainerCondition condition, boolean ajar, ContainerLock lock) {
		BlockState state = DLContainers.get(kind).defaultBlockState()
			.setValue(LootContainerBlock.FACING, Direction.NORTH)
			.setValue(LootContainerBlock.CONDITION, condition)
			.setValue(LootContainerBlock.AJAR, ajar);
		level.setBlock(pos, state, 3);
		if (level.getBlockEntity(pos) instanceof LootContainerBlockEntity container) {
			container.setLootTable(kind.lootTable(), pos.asLong());
			if (lock != null) {
				container.setLock(lock);
			}
		}
	}

	// ---------------------------------------------------------------------------------- helpers

	private static void open(ClientGameTestContext context, TestSingleplayerContext world, BlockPos pos, String name, boolean screenshotScreen) {
		look(context, world, pos.getX() + 0.5, pos.getY(), pos.getZ() - 1.8, 0, 28);
		click(context, pos);
		context.waitForScreen(DLContainerScreen.class);
		context.waitTicks(4);
		if (screenshotScreen) {
			// Park the cursor in a corner so no slot shows its hover highlight.
			context.getInput().setCursorPos(4, 4);
			context.waitTicks(2);
			shot(context, "deadlands-11-screen-" + name);
		}
		// Hide the screen on the client only: the server keeps the menu open, so the container stays open.
		context.setScreen(() -> null);
		context.waitTicks(32);
		shot(context, "deadlands-11-open-" + name);
		world.getServer().runOnServer(s -> s.getPlayerList().getPlayers().getFirst().closeContainer());
		context.waitTicks(25);
	}

	private static void click(ClientGameTestContext context, BlockPos pos) {
		context.runOnClient(client -> client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false)));
	}

	private static void look(ClientGameTestContext context, TestSingleplayerContext world, double x, double y, double z, float yaw, float pitch) {
		world.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @p %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
		context.waitTicks(10);
		world.getClientWorld().waitForChunksRender();
		context.waitTicks(10);
	}

	private static void hud(ClientGameTestContext context, boolean visible) {
		context.runOnClient(client -> client.options.hideGui = !visible);
	}

	static List<String> contents(MinecraftServer server, BlockPos pos) {
		List<String> out = new ArrayList<>();
		if (server.overworld().getBlockEntity(pos) instanceof LootContainerBlockEntity container && container.getLootTable() == null) {
			for (int i = 0; i < container.getContainerSize(); i++) {
				ItemStack stack = container.getItem(i);
				if (!stack.isEmpty()) {
					out.add(i + ":" + BuiltInRegistries.ITEM.getKey(stack.getItem()) + "x" + stack.getCount());
				}
			}
		}
		return out;
	}

	private static void shot(ClientGameTestContext context, String name) {
		Path path = context.takeScreenshot(name);
		Deadlands.LOGGER.info("Screenshot: {}", path.toAbsolutePath());
	}

	private static void check(boolean condition, String what) {
		if (!condition) {
			throw new AssertionError("DEADLANDS client test failed: " + what);
		}
		Deadlands.LOGGER.info("PASS: {}", what);
	}
}
