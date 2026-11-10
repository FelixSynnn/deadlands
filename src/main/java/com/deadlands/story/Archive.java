package com.deadlands.story;

import com.deadlands.Deadlands;
import com.deadlands.network.ArchivePayload;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * The survivor's discovery archive: every lore entry they have read, with the day they read it.
 * Saved with the player and kept through death. Reading something new adds it, says so, tells the
 * story ({@link StoryState#discover}) and sends the journal a fresh copy; a trail note also
 * remembers the words written in it.
 */
public final class Archive {
	/** Entry id to the day it was read. */
	public static final AttachmentType<Map<String, Long>> DISCOVERED = AttachmentRegistry.create(Deadlands.id("archive"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG)).initializer(() -> Map.of()).copyOnDeath());
	/** The words of the trail notes read (entry id to its words), so the archive can show them again. */
	public static final AttachmentType<Map<String, List<String>>> TRAIL_WORDS = AttachmentRegistry.create(Deadlands.id("archive_words"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf())).initializer(() -> Map.of()).copyOnDeath());

	private Archive() {
	}

	public static void init() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> sync(handler.getPlayer())));
	}

	public static Map<String, Long> discovered(ServerPlayer player) {
		return player.getAttachedOrCreate(DISCOVERED);
	}

	/** Add an entry to the archive (no-op for an unknown id). Returns true if it was new to this survivor. */
	public static boolean discover(ServerPlayer player, String id, List<String> args) {
		LoreEntry entry = Lore.get(id);
		if (entry == null) {
			return false;
		}
		if (!args.isEmpty()) {
			Map<String, List<String>> words = new HashMap<>(player.getAttachedOrCreate(TRAIL_WORDS));
			words.put(id, List.copyOf(args));
			player.setAttached(TRAIL_WORDS, words);
		}
		Map<String, Long> saved = discovered(player);
		if (saved.containsKey(id)) {
			return false;
		}
		Map<String, Long> next = new HashMap<>(saved);
		next.put(id, player.level().getDayTime() / 24000L + 1L);
		player.setAttached(DISCOVERED, next);
		player.displayClientMessage(Component.translatable("message.deadlands.archive.added", Component.translatable(Lore.titleKey(id)),
			Component.translatable(entry.category().translationKey())), false);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 1.0F);
		StoryState.get(player.level().getServer()).discover(player, id);
		sync(player);
		return true;
	}

	public static void sync(ServerPlayer player) {
		if (!ServerPlayNetworking.canSend(player, ArchivePayload.TYPE)) {
			return;
		}
		Map<String, List<String>> words = player.getAttachedOrCreate(TRAIL_WORDS);
		List<String> ids = new ArrayList<>(discovered(player).keySet());
		List<List<String>> args = new ArrayList<>();
		for (String id : ids) {
			args.add(words.getOrDefault(id, List.of()));
		}
		ServerPlayNetworking.send(player, new ArchivePayload(ids, args));
	}
}
