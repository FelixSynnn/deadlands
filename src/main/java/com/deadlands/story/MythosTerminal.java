package com.deadlands.story;

import com.deadlands.Deadlands;
import com.deadlands.network.DialoguePayload;
import com.deadlands.registry.DLSounds;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * The hospital terminals: Unit 07, a sub-agent of Mythos that was quarantined in a Kessler Regional machine before the
 * breach and never went rogue, talks to whoever sits down at one. It tells them who Mythos is (Kessler's predictive
 * model of NR-7, lore option A), who made him, what the storms are, why it didn't turn, and how he can be beaten. The
 * first time someone asks how to stop him it gives them Mythos' file ({@code lore.deadlands.mythos_file}).
 *
 * <p>The conversation uses the survivors' dialogue screen, under a reserved id ({@link #ENTITY_ID}); replies come back
 * through {@code NpcDialogue}'s receiver, which hands them here.
 */
public final class MythosTerminal {
	/** The dialogue id the terminal's conversation goes by (no entity has it). */
	public static final int ENTITY_ID = -7007;
	private static final List<String> OPTIONS = List.of("term_who", "term_made", "term_storm", "term_you", "term_stop", "leave");
	private static final Map<UUID, BlockPos> OPEN = new HashMap<>();
	/** Whether a survivor has been given Mythos' file by a terminal. */
	public static final AttachmentType<Boolean> FILE_GIVEN = AttachmentRegistry.create(Deadlands.id("mythos_terminal_file"),
		builder -> builder.persistent(Codec.BOOL).initializer(() -> false).copyOnDeath());

	private MythosTerminal() {
	}

	public static void init() {
		// Class loading registers the attachment.
	}

	public static void open(ServerPlayer player, BlockPos pos) {
		OPEN.put(player.getUUID(), pos.immutable());
		player.level().playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.4F, 1.8F);
		send(player, "terminal.deadlands.unit07.hello");
	}

	/** A reply was picked on the terminal's screen. */
	public static void choose(ServerPlayer player, String option) {
		BlockPos pos = OPEN.get(player.getUUID());
		if (pos == null || player.blockPosition().distSqr(pos) > 8 * 8 || !OPTIONS.contains(option)) {
			return;
		}
		if (option.equals("leave")) {
			OPEN.remove(player.getUUID());
			return;
		}
		player.level().playSound(null, pos, DLSounds.MYTHOS_BLINK.value(), SoundSource.BLOCKS, 0.15F, 1.9F);
		send(player, "terminal.deadlands.unit07." + option);
		if (option.equals("term_stop") && !player.getAttachedOrCreate(FILE_GIVEN)) {
			player.setAttached(FILE_GIVEN, true);
			Quests.give(player, Lore.document("mythos_file"));
			player.displayClientMessage(Component.translatable("terminal.deadlands.unit07.file").withStyle(ChatFormatting.LIGHT_PURPLE), false);
		}
	}

	private static void send(ServerPlayer player, String textKey) {
		if (!ServerPlayNetworking.canSend(player, DialoguePayload.TYPE)) {
			return;
		}
		ServerPlayNetworking.send(player, new DialoguePayload(ENTITY_ID, Component.translatable("terminal.deadlands.unit07.name"),
			Component.translatable("terminal.deadlands.unit07.role").withStyle(ChatFormatting.LIGHT_PURPLE), Component.translatable(textKey), OPTIONS));
	}
}
