package com.deadlands.story;

import com.deadlands.network.EndingChoicePayload;
import com.deadlands.network.EndingChosenPayload;
import com.deadlands.network.EndingPayload;
import com.deadlands.npc.NpcCharacter;
import com.deadlands.npc.NpcEntity;
import com.deadlands.npc.Settlements;
import com.deadlands.registry.DLStory;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * How it ends. Four endings, decided by the story:
 *
 * <ul>
 *   <li><b>Extraction</b> (the main ending): the cure research is done and the helicopter flies;
 *       everyone leaves together, Harley with the cure.</li>
 *   <li><b>Rescue</b>: the helicopter flies before the cure is finished; Harley and the survivors
 *       get out, the research unfinished.</li>
 *   <li><b>Staying behind</b>: the cure is done; the helicopter takes Harley and the others, and the
 *       survivor stays to carry the cure to the settlements (every settlement is restocked and
 *       heartened).</li>
 *   <li><b>Patient Zero</b> (secret): Patient Zero has been put down, his blood brought to Harley,
 *       and all six of his records read. Told what was found, Harley understands that her brother
 *       held the infection back for weeks: from his blood she can make something that brings the
 *       newly turned back. No helicopter; they stay, and begin.</li>
 * </ul>
 *
 * Who is aboard (Harley and whichever of the three were helped with their own stories) is named in
 * the ending. The world goes on afterwards; the journal records the ending.
 */
public final class Endings {
	public static final String EXTRACTION = "extraction";
	public static final String RESCUE = "rescue";
	public static final String STAY = "stay";
	public static final String PATIENT_ZERO = "patient_zero";
	public static final String LEAVE = "leave";
	public static final String NOT_YET = "not_yet";

	private Endings() {
	}

	public static void init() {
		ServerPlayNetworking.registerGlobalReceiver(EndingChosenPayload.TYPE, (payload, context) -> choose(context.player(), payload.entityId(), payload.option()));
	}

	/** The helicopter is ready and a survivor used it: ask what to do. */
	public static void offer(ServerPlayer player, HelicopterEntity helicopter) {
		StoryState story = StoryState.get(player.level().getServer());
		List<String> options = new ArrayList<>();
		options.add(LEAVE);
		if (story.has(StoryState.CURE_DONE)) {
			options.add(STAY);
		}
		options.add(NOT_YET);
		if (ServerPlayNetworking.canSend(player, EndingChoicePayload.TYPE)) {
			ServerPlayNetworking.send(player, new EndingChoicePayload(helicopter.getId(), story.has(StoryState.CURE_DONE), options));
		}
	}

	private static void choose(ServerPlayer player, int entityId, String option) {
		ServerLevel level = player.level();
		StoryState story = StoryState.get(level.getServer());
		if (!(level.getEntity(entityId) instanceof HelicopterEntity helicopter) || helicopter.distanceToSqr(player) > 144.0 || !story.helicopterReady()
			|| !story.ending().isEmpty() || helicopter.lift() >= 0) {
			return;
		}
		switch (option) {
			case LEAVE -> helicopter.beginExtraction(level, story.has(StoryState.CURE_DONE) ? EXTRACTION : RESCUE, true);
			case STAY -> {
				if (story.has(StoryState.CURE_DONE)) {
					helicopter.beginExtraction(level, STAY, false);
				}
			}
			default -> {
			}
		}
	}

	/** Harley and the characters who joined, wherever they are loaded, brought to the helicopter. */
	static List<Entity> gatherCompanions(ServerLevel level, HelicopterEntity helicopter) {
		StoryState story = StoryState.get(level.getServer());
		List<Entity> companions = new ArrayList<>();
		for (Entity entity : level.getAllEntities()) {
			if (entity instanceof NpcEntity npc && npc.isAlive() && npc.character() != null && aboard(story, npc.character())) {
				npc.teleportTo(helicopter.getX() + 2.0, helicopter.getY(), helicopter.getZ() + 2.0);
				companions.add(npc);
			}
		}
		return companions;
	}

	/** Who leaves on the helicopter: Harley, and those whose own stories were seen through. */
	static boolean aboard(StoryState story, NpcCharacter character) {
		return character == NpcCharacter.HARLEY || story.has(StoryState.JOINED + character.id());
	}

	/** The extraction is over (or the secret ending reached): record it and show everyone. */
	public static void finish(ServerLevel level, String ending, List<ServerPlayer> aboard) {
		StoryState story = StoryState.get(level.getServer());
		story.setEnding(ending);
		if (STAY.equals(ending)) {
			Settlements settlements = Settlements.get(level.getServer());
			for (Settlements.Settlement settlement : settlements.all()) {
				if (settlement.inhabited()) {
					settlements.give(settlement, 60, 20);
				}
			}
		}
		List<String> companions = new ArrayList<>();
		for (NpcCharacter character : NpcCharacter.values()) {
			if (!PATIENT_ZERO.equals(ending) && aboard(story, character)) {
				companions.add(character.id());
			}
		}
		for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
			if (ServerPlayNetworking.canSend(player, EndingPayload.TYPE)) {
				ServerPlayNetworking.send(player, new EndingPayload(ending, companions));
			}
			player.displayClientMessage(Component.translatable("message.deadlands.ending." + ending), false);
		}
		if (!aboard.isEmpty()) {
			Quests.chronicle(aboard.getFirst(), "chronicle.deadlands.ending." + ending);
		} else if (!level.getServer().getPlayerList().getPlayers().isEmpty()) {
			Quests.chronicle(level.getServer().getPlayerList().getPlayers().getFirst(), "chronicle.deadlands.ending." + ending);
		}
	}

	/** Whether Harley can be told about Elias: the secret ending's conditions. */
	public static boolean secretReady(StoryState story) {
		return story.ending().isEmpty() && story.has(StoryState.BOSS + "patient_zero") && story.delivered(DLStory.idOf(DLStory.PATIENT_ZERO_BLOOD))
			&& story.seriesRead("p0") >= 6;
	}

	/** Harley hears what was found about Elias. */
	public static Component secret(ServerPlayer player) {
		StoryState story = StoryState.get(player.level().getServer());
		if (!secretReady(story)) {
			return Component.translatable("quest.deadlands.harley.missing");
		}
		finish(player.level(), PATIENT_ZERO, List.of(player));
		return Component.translatable("quest.deadlands.harley.elias");
	}
}
