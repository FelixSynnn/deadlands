package com.deadlands.test;

import com.deadlands.Deadlands;
import com.deadlands.entity.InfectedEntity;
import com.deadlands.entity.InfectedKind;
import com.deadlands.registry.DLBlocks;
import com.deadlands.registry.DLEntities;
import com.deadlands.registry.DLWeapons;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Screenshots for reviewing the overhaul's looks in the real renderer: every infected kind in a row,
 * a row of walkers in the new outfits (hospital patient, industrial coveralls, survivor, firefighter)
 * and older ones, a close-up, and the same scene at dusk. Standing still (no AI) and facing the camera.
 */
final class LookScenes {
	private static final int[] WALKER_LOOKS = {24, 26, 28, 30, 12, 8, 14, 2};
	/** Where {@link #blocks} looks over its floors and walls from, for later scenes. */
	private static double[] blockView;

	private LookScenes() {
	}

	static void infected(ClientGameTestContext context, TestSingleplayerContext world) {
		TestServerContext server = world.getServer();
		server.runCommand("time set 6000");
		server.runCommand("weather clear");
		server.runCommand("gamemode spectator @p");
		server.runCommand("tp @p ~40 ~ ~ 0 6");
		context.waitTicks(20);
		world.getClientWorld().waitForChunksRender();
		int[] ids = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			ServerLevel level = s.overworld();
			List<Integer> out = new ArrayList<>();
			InfectedKind[] kinds = InfectedKind.values();
			for (int i = 0; i < kinds.length; i++) {
				out.add(stand(level, DLEntities.type(kinds[i]), p.getX() + (i - (kinds.length - 1) / 2.0) * 1.6, p.getY(), p.getZ() + 6.0, -1));
			}
			for (int i = 0; i < WALKER_LOOKS.length; i++) {
				out.add(stand(level, DLEntities.WALKER, p.getX() + (i - (WALKER_LOOKS.length - 1) / 2.0) * 1.5, p.getY(), p.getZ() + 9.5, WALKER_LOOKS[i]));
			}
			return out.stream().mapToInt(Integer::intValue).toArray();
		});
		context.runOnClient(client -> client.options.hideGui = true);
		context.waitTicks(30);
		shot(context, "deadlands-40-infected-lineup");
		// "~" in a server command is relative to the world spawn, not the player: use the player's own position.
		double[] at = server.computeOnServer(sv -> new double[] {player(sv).getX(), player(sv).getY(), player(sv).getZ()});
		server.runCommand(String.format(java.util.Locale.ROOT, "tp @p %.2f %.2f %.2f 0 4", at[0], at[1], at[2] + 4.2));
		context.waitTicks(20);
		shot(context, "deadlands-41-infected-closeup");
		server.runCommand("time set 12600");
		context.waitTicks(20);
		shot(context, "deadlands-42-infected-dusk");
		context.runOnClient(client -> client.options.hideGui = false);
		server.runOnServer(s -> {
			for (int id : ids) {
				Entity e = s.overworld().getEntity(id);
				if (e != null) {
					e.discard();
				}
			}
		});
		server.runCommand("time set 6000");
		server.runCommand("gamemode creative @p");
	}

	/** The weapons in the hotbar (icons) and in the hand (first person), two hotbars' worth. */
	static void weapons(ClientGameTestContext context, TestSingleplayerContext world) {
		TestServerContext server = world.getServer();
		server.runCommand("gamemode survival @p");
		server.runCommand("time set 6000");
		Item[][] bars = {
			{DLWeapons.SURVIVAL_KNIFE, DLWeapons.HUNTING_KNIFE, DLWeapons.MACHETE, DLWeapons.RUSTY_MACHETE, DLWeapons.NAIL_BAT, DLWeapons.LEAD_PIPE,
				DLWeapons.PIPE_WRENCH, DLWeapons.FIRE_AXE, DLWeapons.CROWBAR},
			{DLWeapons.PISTOL, DLWeapons.SHOTGUN, DLWeapons.HUNTING_RIFLE, Items.IRON_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD, Items.IRON_AXE,
				Items.DIAMOND_AXE, DLWeapons.BASEBALL_BAT}};
		int[][] held = {{0, 2, 4, 7}, {0, 1, 2, 3}};
		for (int b = 0; b < bars.length; b++) {
			Item[] bar = bars[b];
			server.runOnServer(s -> {
				var inventory = player(s).getInventory();
				for (int i = 0; i < bar.length; i++) {
					inventory.setItem(i, new ItemStack(bar[i]));
				}
			});
			context.waitTicks(5);
			for (int slot : held[b]) {
				context.getInput().pressKey(options -> options.keyHotbarSlots[slot]);
				context.waitTicks(30); // slow weapons rise as their swing recovers
				shot(context, "deadlands-43-held-" + b + "-" + slot);
			}
		}
		server.runCommand("clear @p");
		server.runCommand("gamemode creative @p");
	}

	/**
	 * Block textures: an asphalt floor, a concrete wall and a brick wall big enough for repetition to
	 * show (the per-position variants should break it up), vanilla ground beside them, and a row of
	 * the overhaul's new blocks (concrete, asphalt, planks, metal, glass).
	 */
	static void blocks(ClientGameTestContext context, TestSingleplayerContext world) {
		TestServerContext server = world.getServer();
		server.runCommand("gamemode spectator @p");
		server.runCommand("time set 5000");
		server.runCommand("weather clear");
		double[] at = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			ServerLevel level = s.overworld();
			BlockPos o = BlockPos.containing(p.getX() - 10, p.getY() - 1, p.getZ() + 6);
			Block[] news = {DLBlocks.DIRTY_CONCRETE, DLBlocks.BROKEN_CONCRETE, DLBlocks.POTHOLED_ASPHALT, DLBlocks.ROTTING_PLANKS, DLBlocks.FADED_PLANKS,
				DLBlocks.CORRODED_METAL, DLBlocks.INDUSTRIAL_PANEL, DLBlocks.DIRTY_GLASS, DLBlocks.CRACKED_GLASS, DLBlocks.DUSTY_GLASS};
			for (int x = 0; x < 21; x++) {
				for (int z = 0; z < 12; z++) {
					Block ground = x < 7 ? DLBlocks.ASPHALT : x < 14 ? Blocks.STONE : Blocks.DIRT;
					level.setBlockAndUpdate(o.offset(x, 0, z), ground.defaultBlockState());
				}
				for (int y = 1; y <= 6; y++) {
					Block wall = x < 7 ? DLBlocks.CONCRETE : x < 14 ? DLBlocks.WEATHERED_BRICKS : Blocks.COBBLESTONE;
					level.setBlockAndUpdate(o.offset(x, y, 12), wall.defaultBlockState());
				}
			}
			for (int i = 0; i < news.length; i++) {
				level.setBlockAndUpdate(o.offset(5 + i, 1, 9), news[i].defaultBlockState());
				level.setBlockAndUpdate(o.offset(5 + i, 2, 9), news[i].defaultBlockState());
			}
			return new double[] {o.getX() + 10.5, o.getY() + 3.2, o.getZ() + 2.0};
		});
		blockView = at;
		server.runCommand(String.format(java.util.Locale.ROOT, "tp @p %.2f %.2f %.2f 0 18", at[0], at[1], at[2]));
		context.runOnClient(client -> client.options.hideGui = true);
		context.waitTicks(20);
		world.getClientWorld().waitForChunksRender();
		context.waitTicks(10);
		shot(context, "deadlands-44-block-variants");
		server.runCommand(String.format(java.util.Locale.ROOT, "tp @p %.2f %.2f %.2f 0 8", at[0], at[1] - 1.2, at[2] + 4.5));
		context.waitTicks(15);
		shot(context, "deadlands-45-new-blocks");
		context.runOnClient(client -> client.options.hideGui = false);
		server.runCommand("gamemode creative @p");
	}

	/**
	 * Embers at night, in front of the new blocks: they give their own light (lit by the world, they
	 * used to turn almost black in the dark). Several bursts, so the screenshot catches them mid-life.
	 */
	static void embers(ClientGameTestContext context, TestSingleplayerContext world) {
		TestServerContext server = world.getServer();
		server.runCommand("gamemode spectator @p");
		server.runCommand("time set 18000");
		double[] at = server.computeOnServer(s -> new double[] {player(s).getX(), player(s).getY(), player(s).getZ()});
		context.runOnClient(client -> client.options.hideGui = true);
		context.waitTicks(10);
		for (int i = 0; i < 4; i++) {
			server.runCommand(String.format(java.util.Locale.ROOT, "particle deadlands:ember %.2f %.2f %.2f 0.5 0.3 0.5 0.02 50 force",
				at[0], at[1] + 1.3, at[2] + 1.5));
			context.waitTicks(3);
		}
		shot(context, "deadlands-47-embers-at-night");
		context.runOnClient(client -> client.options.hideGui = false);
		server.runCommand("time set 6000");
		server.runCommand("gamemode creative @p");
	}

	/** The viewpoint over the block floors and walls; {@link #blocks} must have run. */
	static double[] blockView() {
		if (blockView == null) {
			throw new AssertionError("DEADLANDS client test failed: the block scene has not run yet");
		}
		return blockView;
	}

	private static int stand(ServerLevel level, EntityType<?> type, double x, double y, double z, int look) {
		Entity entity = type.create(level, EntitySpawnReason.COMMAND);
		if (!(entity instanceof InfectedEntity infected)) {
			throw new AssertionError("DEADLANDS client test failed: not an infected type " + type);
		}
		infected.snapTo(x, y, z, 180.0F, 0.0F);
		infected.finalizeSpawn(level, level.getCurrentDifficultyAt(infected.blockPosition()), EntitySpawnReason.COMMAND, null);
		if (look >= 0) {
			infected.setVariant(look);
		}
		infected.setNoAi(true);
		infected.setPersistenceRequired();
		infected.setYHeadRot(180.0F);
		infected.yBodyRot = 180.0F;
		level.addFreshEntity(infected);
		return infected.getId();
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void shot(ClientGameTestContext context, String name) {
		Path path = context.takeScreenshot(name);
		Deadlands.LOGGER.info("Screenshot: {}", path.toAbsolutePath());
	}
}
