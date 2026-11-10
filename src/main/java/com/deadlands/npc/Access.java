package com.deadlands.npc;

import com.deadlands.world.town.TownIdentity;
import com.deadlands.world.town.TownLookup;
import com.deadlands.world.town.TownSite;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Who may walk into a settlement.
 *
 * <ul>
 *   <li>Anyone a faction holds <b>hostile</b> is attacked on sight (that is {@link NpcEntity}).</li>
 *   <li>Anyone it is <b>wary</b> of is told to leave when they come inside. If they are still
 *       inside {@link #GRACE_SECONDS} seconds later they are trespassing, and the settlement's
 *       fighters treat them as hostile until they go.</li>
 *   <li>Military towns keep a <b>curfew</b>: after dark only those the military counts as friends
 *       may be inside; everyone else is turned away the same way.</li>
 * </ul>
 *
 * Leaving the settlement clears both the warning and the trespass.
 */
public final class Access {
	public static final int GRACE_SECONDS = 25;
	private static final Map<UUID, Integer> UNWELCOME_SECONDS = new HashMap<>();
	private static final Map<UUID, Long> TRESPASSING = new HashMap<>();

	private Access() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 == 13) {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					check(player);
				}
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> clear(handler.getPlayer().getUUID()));
	}

	/** Is this survivor trespassing in the settlement with this town key? */
	public static boolean trespassing(Player player, long townKey) {
		Long key = TRESPASSING.get(player.getUUID());
		return townKey != 0L && key != null && key == townKey;
	}

	/** Should someone with this standing be turned away from a settlement of this faction right now? */
	public static boolean unwelcome(Faction faction, Reputation.Standing standing, boolean dark) {
		if (standing == Reputation.Standing.WARY) {
			return true;
		}
		return faction == Faction.MILITARY && dark && standing.ordinal() < Reputation.Standing.FRIENDLY.ordinal();
	}

	private static void clear(UUID id) {
		UNWELCOME_SECONDS.remove(id);
		TRESPASSING.remove(id);
	}

	private static void check(ServerPlayer player) {
		ServerLevel level = player.level();
		UUID id = player.getUUID();
		if (level != level.getServer().overworld() || player.isCreative() || player.isSpectator()) {
			clear(id);
			return;
		}
		Settlements.Settlement settlement = Settlements.at(level, player.blockPosition());
		if (settlement == null) {
			clear(id);
			return;
		}
		Faction faction = settlement.faction();
		Reputation.Standing standing = Reputation.standing(player, faction);
		boolean dark = level.isDarkOutside();
		if (!unwelcome(faction, standing, dark)) {
			clear(id);
			return;
		}
		int seconds = UNWELCOME_SECONDS.merge(id, 1, Integer::sum);
		if (seconds == 1) {
			TownSite town = TownLookup.nearest(level, settlement.center(), 32.0).orElse(null);
			Component name = town == null ? Component.translatable(faction.translationKey()) : Component.literal(TownIdentity.of(level.getSeed(), town).name());
			player.displayClientMessage(Component.translatable(standing == Reputation.Standing.WARY ? "message.deadlands.access.unwelcome"
				: "message.deadlands.access.curfew", name, GRACE_SECONDS), false);
		} else if (seconds == GRACE_SECONDS) {
			TownSite town = TownLookup.nearest(level, settlement.center(), 32.0).orElse(null);
			if (town != null) {
				TRESPASSING.put(id, town.key());
			}
			player.displayClientMessage(Component.translatable("message.deadlands.access.trespass"), false);
		}
	}
}
