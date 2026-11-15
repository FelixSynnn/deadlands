package com.deadlands.story;

import com.deadlands.Deadlands;
import com.deadlands.achievement.Achievements;
import com.deadlands.cutscene.Cutscenes;
import com.deadlands.flags.StoryFlags;
import com.deadlands.mission.MissionManager;
import com.deadlands.npc.NpcCharacter;
import com.deadlands.npc.NpcWorldData;
import com.deadlands.npc.SafeSpots;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Phase 5 step 3, chapter three "The House" (the family mystery, bittersweet): after the notebook, Adam
 * asks the survivor to look in on his family's house. It is placed once, 150 to 250 blocks from Adam
 * (one of the town's wooden house templates, on a levelled footing), and its coordinates go on screen
 * and in chat. Arriving there plays the empty-house cutscene and gives the letter his wife left: they got
 * out and went north to the reservoir camp at Pinecrest. Bringing the news back to Adam ends the chapter.
 */
public final class FamilyHouse {
	public static final Identifier MISSION = Deadlands.id("story/the_house");
	public static final String FOUND = "family_house_found";
	public static final String TOLD = "adam_heard_news";
	private static final Identifier TEMPLATE = Deadlands.id("town/wooden_house_a");
	private static final int SIZE = 11;

	/** Where the house is, once placed ({@code deadlands_family_house}). */
	public static final class State extends SavedData {
		public static final Codec<State> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			BlockPos.CODEC.optionalFieldOf("house").forGetter(s -> Optional.ofNullable(s.house))
		).apply(instance, house -> {
			State state = new State();
			state.house = house.orElse(null);
			return state;
		}));
		public static final SavedDataType<State> TYPE = new SavedDataType<>("deadlands_family_house", State::new, CODEC, null);

		@Nullable BlockPos house;
	}

	private FamilyHouse() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 == 17) {
				tick(server);
			}
		});
	}

	private static State state(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(State.TYPE);
	}

	/** Adam, after the notebook: offer the house, wait for news, or hear it. */
	static void talkedToAdam(MinecraftServer server, ServerPlayer player) {
		StoryFlags flags = StoryFlags.get(server);
		if (flags.has(TOLD)) {
			Pinecrest.talkedToAdam(server, player); // chapter four
			return;
		}
		if (!MissionManager.isActive(server, MISSION)) {
			if (MissionManager.start(server, MISSION, player)) {
				for (int line = 1; line <= 3; line++) {
					HarleyQuest.say(player, "Adam", "mission.deadlands.story.the_house.ask." + line);
				}
				BlockPos house = place(player);
				announce(player, house);
			}
			return;
		}
		if (!flags.has(FOUND)) {
			HarleyQuest.say(player, "Adam", "mission.deadlands.story.the_house.waiting");
			BlockPos house = state(server).house;
			if (house != null) {
				announce(player, house);
			}
			return;
		}
		flags.set(TOLD); // the mission's last objective: it completes on the next mission tick
		for (int line = 1; line <= 3; line++) {
			HarleyQuest.say(player, "Adam", "mission.deadlands.story.the_house.adam." + line);
		}
		Achievements.award(player, "story/the_house");
	}

	/** Place the house once, 150 to 250 blocks from Adam, and return its middle. */
	private static BlockPos place(ServerPlayer player) {
		ServerLevel level = player.level();
		State state = state(level.getServer());
		if (state.house != null) {
			return state.house;
		}
		BlockPos adam = NpcWorldData.get(level.getServer()).position(NpcCharacter.ADAM);
		BlockPos from = adam != null ? adam : player.blockPosition();
		double angle = player.getRandom().nextDouble() * Math.PI * 2.0;
		double distance = 150.0 + player.getRandom().nextDouble() * 100.0;
		BlockPos around = from.offset((int) (Math.cos(angle) * distance), 0, (int) (Math.sin(angle) * distance));
		level.getChunk(around); // load it so the ground is known
		around = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, around);
		BlockPos spot = SafeSpots.find(level, around, 0, 24, player.getRandom());
		BlockPos ground = spot != null ? spot : around;
		BlockPos corner = ground.offset(-SIZE / 2, 0, -SIZE / 2);
		Optional<StructureTemplate> template = level.getStructureManager().get(TEMPLATE);
		if (template.isPresent()) {
			// A level footing: clear the space, fill under it down to the ground.
			for (int x = -1; x <= SIZE; x++) {
				for (int z = -1; z <= SIZE; z++) {
					for (int y = 0; y < 12; y++) {
						level.setBlock(corner.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
					}
					for (int y = -1; y > -8; y--) {
						BlockPos below = corner.offset(x, y, z);
						if (level.getBlockState(below).canBeReplaced()) {
							level.setBlock(below, Blocks.DIRT.defaultBlockState(), 2);
						}
					}
				}
			}
			StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(Rotation.NONE).setMirror(Mirror.NONE);
			template.get().placeInWorld(level, corner, corner, settings, player.getRandom(), 2);
		} else {
			Deadlands.LOGGER.warn("DEADLANDS: the family house template {} is missing; the house is only a place", TEMPLATE);
		}
		state.house = ground.immutable();
		state.setDirty();
		return state.house;
	}

	private static void announce(ServerPlayer player, BlockPos house) {
		Component coords = Component.translatable("mission.deadlands.story.the_house.where", house.getX(), house.getY(), house.getZ());
		player.sendSystemMessage(coords.copy().withStyle(ChatFormatting.GOLD));
		player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 140, 30));
		player.connection.send(new ClientboundSetSubtitleTextPacket(coords));
		player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("mission.deadlands.story.the_house.where_title")));
	}

	/** Someone reached the house while the mission runs: the cutscene and the letter, once. */
	private static void tick(MinecraftServer server) {
		StoryFlags flags = StoryFlags.get(server);
		BlockPos house = state(server).house;
		if (house == null || flags.has(FOUND) || !MissionManager.isActive(server, MISSION)) {
			return;
		}
		ServerLevel level = server.overworld();
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || player.blockPosition().distSqr(house) > 12 * 12) {
				continue;
			}
			flags.set(FOUND);
			Cutscenes.playNear(level, Cutscenes.FAMILY_HOUSE, Vec3.atBottomCenterOf(house), 0.0F, 24.0);
			Quests.give(player, Lore.document("adam_family_letter"));
			player.displayClientMessage(Component.translatable("mission.deadlands.story.the_house.found").withStyle(ChatFormatting.GOLD), false);
			return;
		}
	}
}
