package com.deadlands.story;

import com.deadlands.cutscene.Cutscenes;
import com.deadlands.flags.StoryFlags;
import com.deadlands.mission.MissionManager;
import com.deadlands.network.CutscenePayload;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Phase 5 step 6 (started): the story so far and the Cinematic Archive.
 *
 * <ul>
 *   <li>{@code /deadlands story}: every chapter, in order, with where it stands (done, under way, not yet), and
 *       the ending reached, if any.</li>
 *   <li>{@code /deadlands cinematics}: the Cinematic Archive: every cutscene, those seen with a button to watch
 *       again ({@code /deadlands watch <id>}), the rest as "not yet seen" (their names hidden until then). A
 *       replay plays around where the survivor stands, facing the way they face.</li>
 * </ul>
 */
public final class StoryArchive {
	/** A chapter: its name key, the flag that marks it done, and the missions that mean it is under way. */
	private record Chapter(String key, String doneFlag, List<Identifier> missions) {
	}

	private static final List<Chapter> CHAPTERS = List.of(
		new Chapter("one", "chapter_one_case_delivered", List.of(HarleyQuest.STATIC, HarleyQuest.COLD_CHAIN, HarleyQuest.THE_CASE)),
		new Chapter("two", "chapter_two_notebook", List.of(HarleyQuest.ADAMS_LAB)),
		new Chapter("three", "chapter_three_house", List.of(FamilyHouse.MISSION)),
		new Chapter("four", "chapter_four_pinecrest", List.of(Pinecrest.MISSION)),
		new Chapter("imlilylol_one", ImlilylolCampaign.CHAPTER, ImlilylolCampaign.missions()),
		new Chapter("felixsyn_one", FelixsynCampaign.CHAPTER, List.of(FelixsynCampaign.OKAFOR, FelixsynCampaign.TWO_HUNDRED, FelixsynCampaign.HALE)));

	/** The cutscenes in story order. */
	private static final List<String> CINEMATICS = List.of(Cutscenes.OPENING, Cutscenes.BOSS_REVEAL, Cutscenes.RESEARCH, Cutscenes.HORDE,
		Cutscenes.REUNION, Cutscenes.FAMILY_HOUSE, Cutscenes.PINECREST, Cutscenes.CURE, Cutscenes.EXTRACTION);

	private StoryArchive() {
	}

	public static void init() {
		ServerPlayNetworking.registerGlobalReceiver(com.deadlands.network.StoryRequestPayload.TYPE,
			(payload, context) -> context.server().execute(() -> sendOverview(context.player())));
	}

	/** The journal's Story page: every chapter's status, every cinematic seen or not, the ending. */
	public static void sendOverview(ServerPlayer player) {
		if (!ServerPlayNetworking.canSend(player, com.deadlands.network.StoryPayload.TYPE)) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		java.util.List<String> chapters = new java.util.ArrayList<>();
		for (Chapter chapter : CHAPTERS) {
			chapters.add(chapter.key() + "=" + status(server, chapter));
		}
		java.util.List<String> cinematics = new java.util.ArrayList<>();
		for (String id : CINEMATICS) {
			cinematics.add(id + "=" + (Cutscenes.seen(player, id) ? "seen" : "unseen"));
		}
		ServerPlayNetworking.send(player, new com.deadlands.network.StoryPayload(chapters, cinematics, StoryState.get(server).ending()));
	}

	private static String status(MinecraftServer server, Chapter chapter) {
		if (StoryFlags.get(server).has(chapter.doneFlag())) {
			return "done";
		}
		return chapter.missions().stream().anyMatch(id -> MissionManager.isActive(server, id)) ? "active" : "locked";
	}

	public static int story(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		StoryFlags flags = StoryFlags.get(server);
		player.sendSystemMessage(Component.translatable("story.deadlands.archive.header").withStyle(ChatFormatting.GOLD));
		for (Chapter chapter : CHAPTERS) {
			String status;
			ChatFormatting colour;
			if (flags.has(chapter.doneFlag())) {
				status = "done";
				colour = ChatFormatting.GREEN;
			} else if (chapter.missions().stream().anyMatch(id -> MissionManager.isActive(server, id))) {
				status = "active";
				colour = ChatFormatting.YELLOW;
			} else {
				status = "locked";
				colour = ChatFormatting.DARK_GRAY;
			}
			player.sendSystemMessage(Component.literal("  ")
				.append(Component.translatable("story.deadlands.chapter." + chapter.key()).withStyle(colour))
				.append(Component.literal("  "))
				.append(Component.translatable("story.deadlands.status." + status).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
		}
		String ending = StoryState.get(server).ending();
		player.sendSystemMessage(ending.isEmpty()
			? Component.translatable("story.deadlands.archive.no_ending").withStyle(ChatFormatting.GRAY)
			: Component.translatable("story.deadlands.archive.ending", Component.translatable("ending.deadlands.name." + ending)).withStyle(ChatFormatting.GOLD));
		return 1;
	}

	public static int cinematics(ServerPlayer player) {
		player.sendSystemMessage(Component.translatable("story.deadlands.cinematics.header").withStyle(ChatFormatting.GOLD));
		int seen = 0;
		for (String id : CINEMATICS) {
			MutableComponent line = Component.literal("  ");
			if (Cutscenes.seen(player, id)) {
				seen++;
				line.append(Component.translatable("cinematic.deadlands." + id).withStyle(ChatFormatting.WHITE)).append(Component.literal("  "))
					.append(Component.translatable("story.deadlands.cinematics.watch").withStyle(style -> style.withColor(ChatFormatting.AQUA)
						.withClickEvent(new ClickEvent.RunCommand("/deadlands watch " + id))
						.withHoverEvent(new HoverEvent.ShowText(Component.translatable("story.deadlands.cinematics.watch.hover")))));
			} else {
				line.append(Component.translatable("story.deadlands.cinematics.unseen").withStyle(ChatFormatting.DARK_GRAY));
			}
			player.sendSystemMessage(line);
		}
		final int count = seen;
		player.sendSystemMessage(Component.translatable("story.deadlands.cinematics.count", count, CINEMATICS.size()).withStyle(ChatFormatting.GRAY));
		return count;
	}

	/** Replay a cutscene the survivor has seen, around where they stand. False if not seen (or unknown). */
	public static boolean watch(ServerPlayer player, String id) {
		if (!CINEMATICS.contains(id) || !Cutscenes.seen(player, id) || !ServerPlayNetworking.canSend(player, CutscenePayload.TYPE)) {
			return false;
		}
		ServerPlayNetworking.send(player, new CutscenePayload(id, player.getX(), player.getY(), player.getZ(), player.getYRot()));
		return true;
	}
}
