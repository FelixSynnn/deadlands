package com.deadlands.story;

import com.deadlands.npc.NpcCharacter;
import com.deadlands.npc.NpcSpawns;
import com.deadlands.npc.NpcWorldData;
import com.deadlands.npc.Settlements;
import com.deadlands.skill.Skill;
import com.deadlands.skill.Skills;
import com.deadlands.world.town.TownLookup;
import com.deadlands.world.town.TownSite;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * Keeps the story moving while survivors play.
 *
 * <ul>
 *   <li><b>Investigating.</b> Every two seconds each survivor's position is checked against the
 *       story places; walking into one for the first time marks it investigated.</li>
 *   <li><b>Finding places.</b> Once Harley has been met, the story places are looked up one at a
 *       time (it is expensive), from Harley's camp, and kept, so the journal can say which way.</li>
 *   <li><b>Characters who cannot be lost.</b> Two days after Harley is met, any of the other three
 *       nobody has come across yet is given a place to be, chosen by the story: FelixSyn near a
 *       hospital (he went looking for a doctor), Itzbreadloaf in the nearest held town (parts),
 *       Imlilylol in the next one along (trade). Trail notes point there. They appear when a
 *       survivor comes within 48 blocks. Characters cannot die, so they cannot be lost.</li>
 *   <li><b>The cure.</b> A day after Harley starts work, the cure research is done.</li>
 * </ul>
 */
public final class StoryDirector {
	private StoryDirector() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			int tick = server.getTickCount();
			if (tick % 40 == 21) {
				investigate(server);
			}
			if (tick % 200 == 77) {
				slowTick(server);
			}
		});
	}

	private static void investigate(MinecraftServer server) {
		ServerLevel level = server.overworld();
		StoryState story = StoryState.get(server);
		if (!story.has(StoryState.MET + "harley")) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) {
				continue;
			}
			StoryPlaces.Place place = StoryPlaces.at(level, player.blockPosition());
			if (place != null && story.set(StoryState.INVESTIGATED + place.id())) {
				if (story.place(place) == null) {
					story.setPlace(place, player.blockPosition());
				}
				for (ServerPlayer everyone : server.getPlayerList().getPlayers()) {
					everyone.displayClientMessage(Component.translatable("message.deadlands.story.investigated", place.displayName(), player.getName()), false);
				}
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.5F, 1.2F);
				Skills.award(player, Skill.SCAVENGER, 50);
				Quests.chronicle(player, "chronicle.deadlands.story.investigated." + place.id());
			}
		}
	}

	private static void slowTick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		StoryState story = StoryState.get(server);
		if (!story.has(StoryState.MET + "harley")) {
			return;
		}
		long day = level.getDayTime() / 24000L + 1L;
		BlockPos camp = harleyCamp(level);
		// Look up one place per pass.
		for (StoryPlaces.Place place : StoryPlaces.Place.values()) {
			if (!story.placeLooked(place)) {
				story.setPlace(place, StoryPlaces.locate(level, place, camp));
				break;
			}
		}
		if (day >= story.harleyMetDay() + 2) {
			for (NpcCharacter character : List.of(NpcCharacter.FELIXSYN, NpcCharacter.ITZBREADLOAF, NpcCharacter.IMLILYLOL)) {
				planFallback(level, character);
			}
		}
		appear(level);
		if (story.has(StoryState.CURE_STARTED) && !story.has(StoryState.CURE_DONE) && day >= story.cureReadyDay()) {
			story.set(StoryState.CURE_DONE);
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				player.displayClientMessage(Component.translatable("message.deadlands.story.cure_done"), false);
			}
			com.deadlands.cutscene.Cutscenes.playNear(level, com.deadlands.cutscene.Cutscenes.CURE, net.minecraft.world.phys.Vec3.atBottomCenterOf(camp), 0.0F, 64.0);
		}
	}

	/** Where Harley is (or her camp was): the story measures from there. */
	static BlockPos harleyCamp(ServerLevel level) {
		BlockPos harley = NpcWorldData.get(level.getServer()).position(NpcCharacter.HARLEY);
		return harley != null ? harley : level.getRespawnData().pos();
	}

	/**
	 * Give a character a place to be, if they have none yet: chosen by the story, see the class
	 * description. Returns where they are or will be.
	 */
	public static @Nullable BlockPos planFallback(ServerLevel level, NpcCharacter character) {
		NpcWorldData data = NpcWorldData.get(level.getServer());
		BlockPos existing = data.position(character);
		if (existing != null) {
			return existing;
		}
		StoryState story = StoryState.get(level.getServer());
		BlockPos camp = harleyCamp(level);
		List<TownSite> held = new ArrayList<>();
		for (TownSite town : TownLookup.around(level, camp, 2)) {
			if (Settlements.settled(town.condition())) {
				held.add(town);
			}
		}
		held.sort(Comparator.comparingDouble(town -> town.center().distSqr(camp)));
		BlockPos target = switch (character) {
			case FELIXSYN -> story.place(StoryPlaces.Place.HOSPITAL) != null ? story.place(StoryPlaces.Place.HOSPITAL).offset(6, 0, 6)
				: !held.isEmpty() ? held.getFirst().center() : null;
			case ITZBREADLOAF -> !held.isEmpty() ? held.getFirst().center().offset(-5, 0, 3)
				: story.place(StoryPlaces.Place.EVACUATION_SITE);
			case IMLILYLOL -> held.size() > 1 ? held.get(1).center().offset(4, 0, -4)
				: story.place(StoryPlaces.Place.EXPEDITION_CAMP) != null ? story.place(StoryPlaces.Place.EXPEDITION_CAMP).offset(8, 0, 0) : null;
			default -> null;
		};
		if (target == null) {
			int offset = 40 + character.ordinal() * 25;
			target = camp.offset(offset, 0, character.ordinal() % 2 == 0 ? offset : -offset);
		}
		data.plan(character, target);
		return target;
	}

	/** Characters with a planned place appear there once a survivor is close and the ground is loaded. */
	private static void appear(ServerLevel level) {
		NpcWorldData data = NpcWorldData.get(level.getServer());
		for (NpcCharacter character : NpcCharacter.values()) {
			BlockPos planned = data.position(character);
			if (planned == null || data.isPlaced(character) || !level.isLoaded(planned)
				|| !level.hasNearbyAlivePlayer(planned.getX(), planned.getY(), planned.getZ(), 48.0)) {
				continue;
			}
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, planned.getX(), planned.getZ());
			NpcSpawns.place(level, character, new BlockPos(planned.getX(), y, planned.getZ()), level.getRandom().nextFloat() * 360.0F);
		}
	}
}
