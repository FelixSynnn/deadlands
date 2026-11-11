package com.deadlands.achievement;

import com.deadlands.Deadlands;
import com.deadlands.event.DayChangeCallback;
import com.deadlands.item.FlashlightItem;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * DEADLANDS achievements (Phase 2 framework). They are Minecraft advancements under the
 * {@code deadlands} namespace ({@code data/deadlands/advancement/}, written by
 * {@code gen_framework.py}); vanilla's own are dropped when advancements load
 * ({@code ServerAdvancementManagerMixin}). Those a vanilla trigger cannot see use a single
 * {@code minecraft:impossible} criterion named {@code done} and are granted here with
 * {@link #award}.
 *
 * <p>Story and ending achievements are hidden until earned, and none is announced in chat, so
 * nothing about the story reaches a player before they get there (toasts are only the earner's).
 */
public final class Achievements {
	private static final Map<UUID, Night> NIGHTS = new HashMap<>();

	/** Whether a survivor saw tonight start, and whether their flashlight shone since. */
	private static final class Night {
		boolean watched;
		boolean lit;
	}

	private static boolean wasNight;

	private Achievements() {
	}

	/** Grant an achievement (its {@code done} criterion) to one survivor; nothing if already earned or unknown. */
	public static void award(ServerPlayer player, String path) {
		MinecraftServer server = player.level().getServer();
		AdvancementHolder holder = server.getAdvancements().get(Deadlands.id(path));
		if (holder != null) {
			player.getAdvancements().award(holder, "done");
		}
	}

	public static void awardAll(MinecraftServer server, String path) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			award(player, path);
		}
	}

	public static void init() {
		DayChangeCallback.EVENT.register((server, previousDay, newDay, skippedDays) -> {
			if (newDay >= 2) {
				awardAll(server, "survival/first_dawn");
			}
			if (newDay >= 8) {
				awardAll(server, "survival/week_one");
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (server.getPlayerList().getPlayerCount() >= 4) {
				awardAll(server, "coop/full_house");
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> NIGHTS.remove(handler.getPlayer().getUUID()));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 40 == 17) {
				watchNight(server);
			}
		});
	}

	/** A whole night through without the flashlight shining once earns {@code horror/dark_night}. */
	private static void watchNight(MinecraftServer server) {
		boolean night = server.overworld().isDarkOutside();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Night state = NIGHTS.computeIfAbsent(player.getUUID(), id -> new Night());
			if (night && !wasNight) {
				state.watched = true;
				state.lit = false;
			}
			if (night && (FlashlightItem.shining(player.getMainHandItem()) || FlashlightItem.shining(player.getOffhandItem()))) {
				state.lit = true;
			}
			if (!night && wasNight) {
				if (state.watched && !state.lit && player.isAlive()) {
					award(player, "horror/dark_night");
				}
				state.watched = false;
			}
		}
		wasNight = night;
	}
}
