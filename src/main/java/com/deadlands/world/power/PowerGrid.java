package com.deadlands.world.power;

import com.deadlands.config.ConfigManager;
import com.deadlands.network.DLNetworking;
import com.deadlands.persistence.ChronicleEntry;
import com.deadlands.persistence.DeadlandsWorldData;
import com.deadlands.progression.WorldClock;
import com.deadlands.world.town.TownIdentity;
import com.deadlands.world.town.TownLookup;
import com.deadlands.world.town.TownSite;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

/**
 * Which towns have power again. A running generator ({@code PowerGeneratorBlock}) powers every
 * town whose centre is within {@link #REACH} blocks. Powered towns are saved in
 * {@link DeadlandsWorldData}; their street lamps light at night ({@code LampHeadBlock}), lit
 * streets keep the infected from appearing there, natural spawning in a powered town is lower at
 * night, and the town card shows the power is back.
 */
public final class PowerGrid {
	/** How far a generator's lines reach, in blocks from the generator to a town centre. */
	public static final double REACH = 700.0;
	private static final double TOWN_EXTENT = 150.0;

	private PowerGrid() {
	}

	/** Restore power around a generator that was just started. Returns how many towns gained power. */
	public static int restore(ServerLevel level, BlockPos generator, Player player) {
		DeadlandsWorldData data = DeadlandsWorldData.get(level.getServer());
		int gained = 0;
		List<TownSite> towns = TownLookup.around(level, generator, 2);
		for (TownSite town : towns) {
			double dx = town.centerX() - generator.getX();
			double dz = town.centerZ() - generator.getZ();
			if (dx * dx + dz * dz <= REACH * REACH && data.powerTown(town.key())) {
				gained++;
				String name = TownIdentity.of(level.getSeed(), town).name();
				data.addChronicle(new ChronicleEntry(WorldClock.currentDay(level.getServer()), "chronicle.deadlands.power_restored",
					List.of(player.getName().getString(), name)), ConfigManager.get().general.chronicleLength);
			}
		}
		if (gained > 0) {
			if (player instanceof net.minecraft.server.level.ServerPlayer survivor) {
				com.deadlands.npc.Reputation.change(survivor, com.deadlands.npc.Faction.SETTLERS, com.deadlands.npc.Reputation.POWER_RESTORED);
			}
			DLNetworking.broadcastStatus(level.getServer());
		}
		return gained;
	}

	/** True when {@code pos} is inside a town whose power was restored. */
	public static boolean isPowered(ServerLevel level, BlockPos pos) {
		Optional<TownSite> town = TownLookup.nearest(level, pos, TOWN_EXTENT);
		return town.isPresent() && isPowered(level, town.get());
	}

	public static boolean isPowered(ServerLevel level, TownSite town) {
		return DeadlandsWorldData.get(level.getServer()).isTownPowered(town.key());
	}
}
