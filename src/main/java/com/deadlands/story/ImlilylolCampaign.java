package com.deadlands.story;

import com.deadlands.Deadlands;
import com.deadlands.achievement.Achievements;
import com.deadlands.content.DLContent;
import com.deadlands.flags.StoryFlags;
import com.deadlands.mission.MissionDef;
import com.deadlands.mission.MissionManager;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Phase 5 step 5, Imlilylol's campaign, chapter one: "Open Road". It follows her radio quest ("Word on the
 * Road"); her letters already in the lore set it up (the clean north pumps, the broken east-road bridge, the
 * scientist she promised to find again).
 *
 * <ol>
 *   <li><b>The North Pumps</b>: a settlement is drinking bad water. She needs three purification tablets to
 *       trade them through to the clean pumps. Bring them to her.</li>
 *   <li><b>East Road</b>: with the bridge down, her trade has to go round by the evacuation camp. Scout it for
 *       her, then come back and tell her what is there.</li>
 *   <li><b>A Promise</b>: she promised the scientist she would find her again. She can't leave the road, so she
 *       sends her word with the survivor: talk to Harley. That ends the chapter.</li>
 * </ol>
 */
public final class ImlilylolCampaign {
	public static final Identifier PUMPS = Deadlands.id("imlilylol/north_pumps");
	public static final Identifier EAST_ROAD = Deadlands.id("imlilylol/east_road");
	public static final Identifier PROMISE = Deadlands.id("imlilylol/a_promise");
	public static final String PUMPS_DONE = "lily_pumps_done";
	public static final String EAST_TOLD = "lily_east_road_told";
	public static final String HARLEY_TOLD = "lily_word_to_harley";
	public static final String CHAPTER = "imlilylol_chapter_one";

	private ImlilylolCampaign() {
	}

	/** Imlilylol, after her radio quest: start, remind, or move her chapter on. False when there is nothing to say. */
	static boolean talked(MinecraftServer server, ServerPlayer player) {
		StoryFlags flags = StoryFlags.get(server);
		if (flags.has(CHAPTER)) {
			return false;
		}
		if (!flags.has(PUMPS_DONE)) {
			if (!MissionManager.isActive(server, PUMPS)) {
				if (MissionManager.start(server, PUMPS, player)) {
					lines(player, "north_pumps.ask", 3);
				}
				return true;
			}
			MissionDef def = DLContent.MISSIONS.get(PUMPS).orElse(null);
			if (def == null || !carries(player.getInventory(), def)) {
				lines(player, "north_pumps.reminder", 1);
				return true;
			}
			flags.set(PUMPS_DONE);
			MissionManager.complete(server, PUMPS); // rewards first: taking the items would undo "have the items"
			take(player.getInventory(), def);
			lines(player, "north_pumps.thanks", 2);
			if (MissionManager.start(server, EAST_ROAD, player)) {
				lines(player, "east_road.ask", 3);
			}
			return true;
		}
		if (!flags.has(EAST_TOLD)) {
			if (!MissionManager.isActive(server, EAST_ROAD)) {
				if (MissionManager.start(server, EAST_ROAD, player)) {
					lines(player, "east_road.ask", 3);
				}
				return true;
			}
			if (!MissionManager.objectiveDone(server, EAST_ROAD, "scout")) {
				lines(player, "east_road.reminder", 1);
				return true;
			}
			flags.set(EAST_TOLD); // the mission's last objective
			lines(player, "east_road.thanks", 2);
			if (MissionManager.start(server, PROMISE, player)) {
				lines(player, "a_promise.ask", 3);
			}
			return true;
		}
		if (!MissionManager.isActive(server, PROMISE) && !flags.has(HARLEY_TOLD)) {
			if (MissionManager.start(server, PROMISE, player)) {
				lines(player, "a_promise.ask", 3);
			}
			return true;
		}
		lines(player, "a_promise.reminder", 1);
		return true;
	}

	/** Harley, while the promise is being carried: she hears it. True if this conversation was that. */
	static boolean talkedToHarley(MinecraftServer server, ServerPlayer player) {
		StoryFlags flags = StoryFlags.get(server);
		if (!MissionManager.isActive(server, PROMISE) || flags.has(HARLEY_TOLD)) {
			return false;
		}
		flags.set(HARLEY_TOLD); // completes the mission on its next tick (reward flag: the chapter)
		HarleyQuest.say(player, "You", "mission.deadlands.imlilylol.a_promise.tell");
		for (int line = 1; line <= 3; line++) {
			HarleyQuest.say(player, "Harley", "mission.deadlands.imlilylol.a_promise.harley." + line);
		}
		Quests.chronicle(player, "chronicle.deadlands.story.imlilylol_chapter_one");
		Achievements.award(player, "story/imlilylol_chapter_one");
		return true;
	}

	private static void lines(ServerPlayer player, String key, int count) {
		for (int line = 1; line <= count; line++) {
			HarleyQuest.say(player, "Imlilylol", "mission.deadlands.imlilylol." + key + (count == 1 ? "" : "." + line));
		}
	}

	static boolean carries(Inventory inventory, MissionDef def) {
		for (MissionDef.Objective objective : def.objectives()) {
			if (objective.type().equals("have_item")) {
				Item item = objective.target().map(BuiltInRegistries.ITEM::getValue).orElse(null);
				if (item == null || inventory.countItem(item) < Math.max(1, objective.count())) {
					return false;
				}
			}
		}
		return true;
	}

	static void take(Inventory inventory, MissionDef def) {
		for (MissionDef.Objective objective : def.objectives()) {
			if (!objective.type().equals("have_item")) {
				continue;
			}
			Item item = objective.target().map(BuiltInRegistries.ITEM::getValue).orElse(null);
			int left = Math.max(1, objective.count());
			for (int slot = 0; slot < inventory.getContainerSize() && item != null && left > 0; slot++) {
				ItemStack stack = inventory.getItem(slot);
				if (stack.is(item)) {
					int taken = Math.min(left, stack.getCount());
					stack.shrink(taken);
					left -= taken;
				}
			}
		}
	}

	/** Every mission of the chapter (for checks). */
	public static List<Identifier> missions() {
		return List.of(PUMPS, EAST_ROAD, PROMISE);
	}
}
