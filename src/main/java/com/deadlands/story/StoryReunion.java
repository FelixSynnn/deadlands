package com.deadlands.story;

import com.deadlands.cutscene.Cutscenes;
import com.deadlands.flags.StoryFlags;
import com.deadlands.npc.NpcCharacter;
import com.deadlands.npc.NpcSpawns;
import com.deadlands.npc.NpcWorldData;
import com.deadlands.npc.SafeSpots;
import com.deadlands.registry.DLSounds;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

/**
 * Story Canon (2026-11-12): FelixSyn and Harley, apart since the night it started, find each other at
 * the start of day 10. Once per world (the flag {@value #FLAG} is saved with it): the reunion cutscene
 * plays for everyone near a survivor (Harley and FelixSyn meet in front of them, with subtitles and a
 * sound cue), and FelixSyn then joins Harley at her camp. Before that he is story-only and never met by
 * chance ({@link NpcCharacter#storyOnly()}).
 */
public final class StoryReunion {
	public static final String FLAG = "felixsyn_harley_reunited";
	public static final long DAY = 10L;

	private StoryReunion() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 == 7) {
				tick(server);
			}
		});
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		long day = level.getDayTime() / 24000L + 1L;
		long time = Math.floorMod(level.getDayTime(), 24000L);
		// The start of day 10 (or later, if the morning was slept through): in daylight, with someone there.
		if (day < DAY || time > 11000L || StoryFlags.get(server).has(FLAG)) {
			return;
		}
		List<ServerPlayer> players = level.players().stream().filter(p -> !p.isSpectator() && p.isAlive()).toList();
		if (players.isEmpty()) {
			return;
		}
		trigger(level, players.get(level.getRandom().nextInt(players.size())));
	}

	/** Play the reunion around {@code player} now; false if it already happened. */
	public static boolean trigger(ServerLevel level, ServerPlayer player) {
		if (!StoryFlags.get(level.getServer()).set(FLAG)) {
			return false;
		}
		level.playSound(null, player.blockPosition(), DLSounds.EVENT_REUNION.value(), SoundSource.AMBIENT, 0.8F, 1.0F);
		Cutscenes.playNear(level, Cutscenes.REUNION, player.position(), player.getYRot(), 48.0);
		joinHarley(level, player);
		return true;
	}

	/** FelixSyn stays: at Harley's camp if she has one, otherwise beside the survivor. */
	private static void joinHarley(ServerLevel level, ServerPlayer player) {
		NpcWorldData data = NpcWorldData.get(level.getServer());
		if (data.isPlaced(NpcCharacter.FELIXSYN)) {
			return; // an older world met him already
		}
		BlockPos camp = data.position(NpcCharacter.HARLEY);
		BlockPos origin = camp != null ? camp : player.blockPosition();
		BlockPos spot = SafeSpots.find(level, origin, 2, 16, player.getRandom());
		NpcSpawns.place(level, NpcCharacter.FELIXSYN, spot != null ? spot : player.blockPosition(), player.getYRot() + 180.0F);
	}
}
