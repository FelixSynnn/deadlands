package com.deadlands.story;

import com.deadlands.achievement.Achievements;
import com.deadlands.npc.NpcCharacter;
import com.deadlands.npc.NpcEntity;
import com.deadlands.player.SurvivorCharacter;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * Phase 6: when a survivor plays Harley, they <i>are</i> Harley, so there aren't two of her.
 *
 * <ul>
 *   <li><b>The camp's Harley steps out:</b> while a Harley player is online, the Harley NPC is hidden (invisible, silent,
 *       untouchable) and comes back when no one is playing her.</li>
 *   <li><b>Her opening:</b> when the opening ends for the Harley player, instead of meeting herself she gets her own
 *       thoughts, the story counts her as met (so its clock starts as it would), and her questline begins.</li>
 *   <li><b>Her missions:</b> the other survivors bring her what she asks for by right-clicking the Harley player (the
 *       same as talking to the NPC: hand-ins, the next mission, Imlilylol's word). The Harley player's own deliveries
 *       complete by themselves once she carries what's needed, and her next mission starts when one is due.</li>
 *   <li><b>Anyone told to find Harley</b> after their opening is pointed to the player who is her.</li>
 * </ul>
 */
public final class HarleyRole {
	private HarleyRole() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 40 == 7) {
				boolean played = player(server) != null;
				for (ServerLevel level : server.getAllLevels()) {
					for (Entity entity : level.getAllEntities()) {
						if (entity instanceof NpcEntity npc && npc.character() == NpcCharacter.HARLEY) {
							hide(npc, played);
						}
					}
				}
			}
			if (server.getTickCount() % 100 == 31) {
				ServerPlayer harley = player(server);
				if (harley != null && harley.isAlive()) {
					HarleyQuest.selfCheck(server, harley);
				}
			}
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer survivor) || !(entity instanceof ServerPlayer other)
				|| SurvivorCharacter.of(other) != NpcCharacter.HARLEY) {
				return InteractionResult.PASS;
			}
			meet(survivor);
			HarleyQuest.talkTo(survivor.level().getServer(), survivor, other.getDisplayName().getString());
			return InteractionResult.SUCCESS;
		});
	}

	/** The survivor online who is Harley, or null. */
	public static @Nullable ServerPlayer player(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (SurvivorCharacter.of(player) == NpcCharacter.HARLEY) {
				return player;
			}
		}
		return null;
	}

	private static void hide(NpcEntity npc, boolean hidden) {
		if (npc.isInvisible() == hidden) {
			return;
		}
		npc.setInvisible(hidden);
		npc.setSilent(hidden);
		npc.setInvulnerable(hidden);
		npc.setCustomNameVisible(!hidden);
		npc.getNavigation().stop();
	}

	/** First time this survivor meets Harley (the NPC's own first meeting, as NpcDialogue does it). */
	static void meet(ServerPlayer player) {
		Achievements.award(player, "story/met_harley");
		StoryState story = StoryState.get(player.level().getServer());
		if (story.set(StoryState.MET + NpcCharacter.HARLEY.id())) {
			story.setHarleyMetDay(player.level().getDayTime() / 24000L + 1L);
		}
	}

	/** The opening is over and this survivor is Harley: her own thoughts, then her questline. */
	public static void begin(ServerPlayer player) {
		meet(player);
		for (int i = 1; i <= 3; i++) {
			player.sendSystemMessage(Component.translatable("story.deadlands.harley_self." + i).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		}
		HarleyQuest.selfCheck(player.level().getServer(), player);
	}

	/** Someone else, after their opening, while a player is Harley: where to find her. */
	public static void pointTo(ServerPlayer player, ServerPlayer harley) {
		player.sendSystemMessage(Component.translatable("story.deadlands.harley_is", harley.getDisplayName()).withStyle(ChatFormatting.YELLOW));
	}
}
