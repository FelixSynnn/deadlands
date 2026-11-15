package com.deadlands.story;

import com.deadlands.Deadlands;
import com.deadlands.achievement.Achievements;
import com.deadlands.content.DLContent;
import com.deadlands.flags.StoryFlags;
import com.deadlands.mission.MissionDef;
import com.deadlands.mission.MissionManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Phase 5 step 5, FelixSyn's campaign, chapter one: "The East Gate". It follows his own quest (the
 * evacuation orders and the Warden), and his field notes in the lore set it up: the night at Camp Meridian
 * when he and Okafor put their rifles down and walked out through the east gate, and the two hundred people
 * left outside the wire.
 *
 * <ol>
 *   <li><b>Okafor</b>: they split up on the road. Her last call came from a military post. Scout one, then tell
 *       him what you found.</li>
 *   <li><b>The Two Hundred</b>: the people outside the wire who made it now camp on their own. Bring him four
 *       cans of beans and three bandages to take to them.</li>
 *   <li><b>Hale's Trail</b>: Colonel Hale's research files, wherever they ended up. Bring him one; that ends
 *       the chapter.</li>
 * </ol>
 */
public final class FelixsynCampaign {
	public static final Identifier OKAFOR = Deadlands.id("felixsyn/okafor");
	public static final Identifier TWO_HUNDRED = Deadlands.id("felixsyn/the_two_hundred");
	public static final Identifier HALE = Deadlands.id("felixsyn/hales_trail");
	public static final String OKAFOR_TOLD = "felix_okafor_told";
	public static final String SUPPLIES_DONE = "felix_supplies_done";
	public static final String HALE_DONE = "felix_hale_files";
	public static final String CHAPTER = "felixsyn_chapter_one";

	private FelixsynCampaign() {
	}

	/** FelixSyn, after his own quest: start, remind, or move his chapter on. False when there is nothing to say. */
	static boolean talked(MinecraftServer server, ServerPlayer player) {
		StoryFlags flags = StoryFlags.get(server);
		if (flags.has(CHAPTER)) {
			return false;
		}
		if (!flags.has(OKAFOR_TOLD)) {
			if (!MissionManager.isActive(server, OKAFOR)) {
				if (MissionManager.start(server, OKAFOR, player)) {
					lines(player, "okafor.ask", 3);
				}
				return true;
			}
			if (!MissionManager.objectiveDone(server, OKAFOR, "scout")) {
				lines(player, "okafor.reminder", 1);
				return true;
			}
			flags.set(OKAFOR_TOLD); // the mission's last objective
			lines(player, "okafor.thanks", 3);
			startNext(server, player, TWO_HUNDRED, "the_two_hundred.ask");
			return true;
		}
		if (!flags.has(SUPPLIES_DONE)) {
			if (deliver(server, player, TWO_HUNDRED, "the_two_hundred", SUPPLIES_DONE)) {
				startNext(server, player, HALE, "hales_trail.ask");
			}
			return true;
		}
		if (deliver(server, player, HALE, "hales_trail", HALE_DONE)) {
			Quests.chronicle(player, "chronicle.deadlands.story.felixsyn_chapter_one");
			Achievements.award(player, "story/felixsyn_chapter_one");
		}
		return true;
	}

	/** A bring-these mission: start it, remind, or take the items and finish. True when it finished now. */
	private static boolean deliver(MinecraftServer server, ServerPlayer player, Identifier id, String key, String flag) {
		if (!MissionManager.isActive(server, id)) {
			startNext(server, player, id, key + ".ask");
			return false;
		}
		MissionDef def = DLContent.MISSIONS.get(id).orElse(null);
		if (def == null || !ImlilylolCampaign.carries(player.getInventory(), def)) {
			lines(player, key + ".reminder", 1);
			return false;
		}
		StoryFlags.get(server).set(flag);
		MissionManager.complete(server, id); // rewards first: taking the items would undo "have the items"
		ImlilylolCampaign.take(player.getInventory(), def);
		lines(player, key + ".thanks", 3);
		return true;
	}

	private static void startNext(MinecraftServer server, ServerPlayer player, Identifier id, String askKey) {
		if (MissionManager.start(server, id, player)) {
			lines(player, askKey, 3);
		}
	}

	private static void lines(ServerPlayer player, String key, int count) {
		for (int line = 1; line <= count; line++) {
			HarleyQuest.say(player, "FelixSyn", "mission.deadlands.felixsyn." + key + (count == 1 ? "" : "." + line));
		}
	}
}
