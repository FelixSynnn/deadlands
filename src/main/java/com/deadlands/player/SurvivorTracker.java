package com.deadlands.player;

import com.deadlands.config.ConfigManager;
import com.deadlands.config.DeadlandsConfig;
import com.deadlands.network.DLNetworking;
import com.deadlands.persistence.ChronicleEntry;
import com.deadlands.persistence.DeadlandsWorldData;
import com.deadlands.persistence.PlayerRecord;
import com.deadlands.registry.DLItems;
import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Keeps each survivor's {@link PlayerRecord} up to date: arrival (first join), deaths and the
 * chronicle lines they produce. Sends the world status to players as soon as they can receive it.
 */
public final class SurvivorTracker {
	private SurvivorTracker() {
	}

	public static void init() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(handler.getPlayer(), server));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				onDeath(player);
			}
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> DLNetworking.sendStatus(newPlayer));
	}

	static void onJoin(ServerPlayer player, MinecraftServer server) {
		DeadlandsConfig config = ConfigManager.get();
		DeadlandsWorldData data = DeadlandsWorldData.get(server);
		long dayIndex = server.overworld().getDayCount();
		String name = player.getName().getString();
		boolean firstArrival = !data.hasPlayer(player.getUUID());
		PlayerRecord record = data.getOrCreatePlayer(player.getUUID(), name, dayIndex);
		record.setLastKnownName(name);
		record.setLastSeenDay(dayIndex);
		if (firstArrival) {
			com.deadlands.npc.NpcSpawns.onFirstArrival(player);
			com.deadlands.story.StoryIntro.begin(player);
			data.addChronicle(new ChronicleEntry(dayIndex + 1, "chronicle.deadlands.arrived", List.of(name)), config.general.chronicleLength);
		} else if (!com.deadlands.npc.NpcWorldData.get(server).isPlaced(com.deadlands.npc.NpcCharacter.HARLEY)) {
			com.deadlands.story.StoryIntro.repairHarley(player); // her spawn failed in an earlier version
		}
		if (config.general.giveJournalOnFirstJoin && !record.journalGiven()) {
			giveJournal(player);
			record.setJournalGiven(true);
		}
		data.setDirty();
		DLNetworking.sendStatus(player);
		if (firstArrival && config.general.announceNewDays) {
			DLNetworking.sendDayBegin(player, dayIndex + 1, true);
		}
	}

	static void onDeath(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		DeadlandsWorldData data = DeadlandsWorldData.get(server);
		long dayIndex = server.overworld().getDayCount();
		String name = player.getName().getString();
		PlayerRecord record = data.getOrCreatePlayer(player.getUUID(), name, dayIndex);
		long survived = record.daysSurvived(dayIndex);
		record.recordDeath(dayIndex);
		data.addChronicle(new ChronicleEntry(dayIndex + 1, "chronicle.deadlands.died", List.of(name, Long.toString(survived))),
			ConfigManager.get().general.chronicleLength);
		data.setDirty();
	}

	public static void giveJournal(ServerPlayer player) {
		ItemStack journal = new ItemStack(DLItems.SURVIVOR_JOURNAL);
		if (!player.addItem(journal)) {
			player.drop(journal, false);
		}
	}
}
