package com.deadlands.cutscene;

import com.deadlands.Deadlands;
import com.deadlands.network.CutscenePayload;
import com.mojang.serialization.Codec;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * The story's cinematic moments, decided on the server and played by the client
 * ({@code client.cutscene.CutscenePlayer}): the opening near Harley, the first boss, the research
 * discovery, the first horde coming, the cure, and the helicopter. Each plays once per survivor (the
 * ones seen are saved with them and kept through death); the client can turn them off and any key
 * skips one.
 */
public final class Cutscenes {
	public static final String OPENING = "opening";
	public static final String BOSS_REVEAL = "boss_reveal";
	public static final String RESEARCH = "research";
	public static final String HORDE = "horde";
	public static final String CURE = "cure";
	public static final String EXTRACTION = "extraction";
	/** Phase 5: FelixSyn and Harley find each other on day 10 ({@code story.StoryReunion}). */
	public static final String REUNION = "reunion";
	/** Phase 5 step 3: Adam's empty house ({@code story.FamilyHouse}). */
	public static final String FAMILY_HOUSE = "family_house";
	/** Phase 5 step 4: the arrival at Pinecrest ({@code story.Pinecrest}). */
	public static final String PINECREST = "pinecrest";

	public static final AttachmentType<List<String>> SEEN = AttachmentRegistry.create(Deadlands.id("cutscenes_seen"),
		builder -> builder.persistent(Codec.STRING.listOf()).initializer(List::of).copyOnDeath());

	private Cutscenes() {
	}

	public static void init() {
		// Class loading registers the attachment.
	}

	/** Play a cutscene for one survivor, unless they have seen it. Returns true if it was sent. */
	public static boolean play(ServerPlayer player, String id, Vec3 anchor, float yaw) {
		List<String> seen = player.getAttachedOrCreate(SEEN);
		if (seen.contains(id) || !ServerPlayNetworking.canSend(player, CutscenePayload.TYPE)) {
			return false;
		}
		Set<String> next = new HashSet<>(seen);
		next.add(id);
		player.setAttached(SEEN, List.copyOf(next));
		ServerPlayNetworking.send(player, new CutscenePayload(id, anchor.x, anchor.y, anchor.z, yaw));
		// No scares during the scene or for two minutes after it.
		com.deadlands.horror.HorrorDirector.grace(player, com.deadlands.horror.HorrorDirector.GRACE_TICKS + 600);
		return true;
	}

	/** Play a cutscene for every survivor within {@code radius} of {@code anchor}. */
	public static void playNear(ServerLevel level, String id, Vec3 anchor, float yaw, double radius) {
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && player.position().distanceToSqr(anchor) <= radius * radius) {
				play(player, id, anchor, yaw);
			}
		}
	}

	/** Whether a survivor has already seen a cutscene. */
	public static boolean seen(ServerPlayer player, String id) {
		return player.getAttachedOrCreate(SEEN).contains(id);
	}
}
