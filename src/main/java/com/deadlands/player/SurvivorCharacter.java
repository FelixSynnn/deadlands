package com.deadlands.player;

import com.deadlands.Deadlands;
import com.deadlands.network.CharacterPickPayload;
import com.deadlands.network.CharactersPayload;
import com.deadlands.network.OpenCharacterSelectPayload;
import com.deadlands.npc.NpcCharacter;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Phase 6 (multiplayer), step 1: who each survivor plays, one of the group of four (Itzbreadloaf, Imlilylol,
 * FelixSyn, Harley). They pick on the character select screen, shown when the opening ends (before Harley speaks) and on
 * joining for anyone who never chose; {@code /deadlands character <name>} ({@code none} gives it back) works too.
 *
 * <ul>
 *   <li><b>One of each:</b> no two survivors online are the same person. Asking for one that's taken is refused,
 *       naming who has it.</li>
 *   <li><b>It sticks:</b> the choice is saved with the player and kept through death and reconnecting. If someone
 *       else took that character while they were away, they come back as nobody and are told.</li>
 *   <li><b>Everyone sees it:</b> the picks of everyone online go to every player ({@link CharactersPayload}) when anyone
 *       joins, leaves or chooses, and each player is drawn in their character's skin.</li>
 * </ul>
 */
public final class SurvivorCharacter {
	/** The characters a player can be. */
	public static final List<NpcCharacter> PLAYABLE = List.of(NpcCharacter.ITZBREADLOAF, NpcCharacter.IMLILYLOL, NpcCharacter.FELIXSYN, NpcCharacter.HARLEY);

	public static final AttachmentType<String> CHOICE = AttachmentRegistry.create(Deadlands.id("character"),
		builder -> builder.persistent(Codec.STRING).copyOnDeath());

	private SurvivorCharacter() {
	}

	public static void init() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> {
			ServerPlayer player = handler.getPlayer();
			NpcCharacter mine = of(player);
			if (mine != null) {
				ServerPlayer holder = holder(server, mine, player);
				if (holder != null) {
					player.removeAttached(CHOICE); // taken while they were away
					player.sendSystemMessage(Component.translatable("character.deadlands.taken_away", mine.displayName(), holder.getDisplayName()));
				}
			}
			broadcast(server);
			// Anyone past the opening who has no character yet chooses now (new arrivals choose when the opening ends).
			if (of(player) == null && com.deadlands.cutscene.Cutscenes.seen(player, com.deadlands.cutscene.Cutscenes.OPENING)) {
				openSelect(player, false);
			}
		}));
		ServerPlayNetworking.registerGlobalReceiver(CharacterPickPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			if (payload.character().isEmpty()) {
				player.sendSystemMessage(Component.translatable("character.deadlands.none"));
				if (payload.afterIntro()) {
					com.deadlands.story.StoryIntro.harleySpeaks(player);
				}
				return;
			}
			NpcCharacter character = NpcCharacter.byId(payload.character());
			if (character != null && choose(player, character) && payload.afterIntro()) {
				com.deadlands.story.StoryIntro.harleySpeaks(player);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> server.execute(() -> broadcast(server)));
	}

	/** Show the character select screen; false if the client can't (no DEADLANDS client). */
	public static boolean openSelect(ServerPlayer player, boolean afterIntro) {
		if (!ServerPlayNetworking.canSend(player, OpenCharacterSelectPayload.TYPE)) {
			return false;
		}
		broadcast(player.level().getServer()); // fresh picks first, so the screen shows who is taken
		ServerPlayNetworking.send(player, new OpenCharacterSelectPayload(afterIntro));
		return true;
	}

	/** The character this survivor has chosen, or null. */
	public static @Nullable NpcCharacter of(ServerPlayer player) {
		NpcCharacter character = NpcCharacter.byId(player.getAttachedOrElse(CHOICE, ""));
		return character != null && PLAYABLE.contains(character) ? character : null; // List.of throws on contains(null)
	}

	/** Another survivor online playing {@code character}, or null. */
	public static @Nullable ServerPlayer holder(MinecraftServer server, NpcCharacter character, @Nullable ServerPlayer except) {
		for (ServerPlayer other : server.getPlayerList().getPlayers()) {
			if (other != except && of(other) == character) {
				return other;
			}
		}
		return null;
	}

	/** Pick a character (null to give it up). False, with a message to the player, when it isn't allowed. */
	public static boolean choose(ServerPlayer player, @Nullable NpcCharacter character) {
		MinecraftServer server = player.level().getServer();
		if (character == null) {
			player.removeAttached(CHOICE);
			player.sendSystemMessage(Component.translatable("character.deadlands.cleared"));
			broadcast(server);
			return true;
		}
		if (!PLAYABLE.contains(character)) {
			player.sendSystemMessage(Component.translatable("character.deadlands.not_playable", character.displayName()));
			return false;
		}
		ServerPlayer holder = holder(server, character, player);
		if (holder != null) {
			player.sendSystemMessage(Component.translatable("character.deadlands.taken", character.displayName(), holder.getDisplayName()));
			return false;
		}
		player.setAttached(CHOICE, character.id());
		for (ServerPlayer other : server.getPlayerList().getPlayers()) {
			other.sendSystemMessage(Component.translatable("character.deadlands.chosen", player.getDisplayName(), character.displayName()));
		}
		broadcast(server);
		return true;
	}

	/** Send everyone the picks of everyone online. */
	public static void broadcast(MinecraftServer server) {
		List<CharactersPayload.Entry> entries = new ArrayList<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			NpcCharacter character = of(player);
			if (character != null) {
				entries.add(new CharactersPayload.Entry(player.getUUID(), character.id()));
			}
		}
		CharactersPayload payload = new CharactersPayload(entries);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerPlayNetworking.send(player, payload);
		}
	}
}
