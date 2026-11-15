package com.deadlands.story;

import com.deadlands.Deadlands;
import com.deadlands.achievement.Achievements;
import com.deadlands.cutscene.Cutscenes;
import com.deadlands.flags.StoryFlags;
import com.deadlands.mission.MissionManager;
import com.deadlands.npc.Faction;
import com.deadlands.npc.NpcCharacter;
import com.deadlands.npc.NpcEntity;
import com.deadlands.npc.NpcRole;
import com.deadlands.npc.NpcSpawns;
import com.deadlands.npc.NpcWorldData;
import com.deadlands.npc.SafeSpots;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Phase 5 step 4, chapter four "Pinecrest" (the bittersweet ending of Harley's story).
 *
 * <p>After the house, and once FelixSyn and Harley have found each other, Adam asks the survivor to take
 * them all north to the reservoir camp at Pinecrest. The camp is built once, 300 to 450 blocks from Adam: a
 * palisade with a gate and two watchtowers, tents, a medical tent, a fire, and the reservoir behind it; a
 * lean-to stands outside the gate. Its coordinates go on screen and in chat.
 *
 * <p>Reaching the gate plays the arrival: Adam's wife and their two kids run out to him; the guard checks
 * everyone, and Harley's arm has a bite she hid since the bridge. The camp cannot take her in. She stays
 * outside, in the lean-to, and gives Adam her share of the work: if there is a cure, it is in the camp now.
 * FelixSyn turns to the survivor: what would you do? The survivor's answer (two buttons in chat) decides
 * whether FelixSyn stays outside the fence with Harley or goes in. Either way the chapter ends; the world
 * goes on, with Adam's family inside the camp and Harley at the lean-to.
 */
public final class Pinecrest {
	public static final Identifier MISSION = Deadlands.id("story/pinecrest");
	public static final String REACHED = "pinecrest_reached";
	public static final String CHOSEN = "pinecrest_choice_made";
	public static final String SETTLED = "pinecrest_settled";
	public static final String STAYED = "felixsyn_stayed_with_harley";
	public static final String WENT = "felixsyn_went_inside";
	public static final String HARLEY_INFECTED = "harley_infected";
	public static final String CHAPTER = "chapter_four_pinecrest";
	public static final String STAY = "pinecrest_stay";
	public static final String GO = "pinecrest_go";
	/** The arrival cutscene's length ({@code tools/assetgen/gen_story_ch4.py}). */
	public static final int CUTSCENE_TICKS = 50 * 20;
	/** Half the palisade's side. */
	private static final int R = 13;

	/** Where the camp's middle is, once built ({@code deadlands_pinecrest}). */
	public static final class State extends SavedData {
		public static final Codec<State> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			BlockPos.CODEC.optionalFieldOf("camp").forGetter(s -> Optional.ofNullable(s.camp))
		).apply(instance, camp -> {
			State state = new State();
			state.camp = camp.orElse(null);
			return state;
		}));
		public static final SavedDataType<State> TYPE = new SavedDataType<>("deadlands_pinecrest", State::new, CODEC, null);

		@Nullable BlockPos camp;
	}

	/** Survivors who saw the arrival begin, and the server tick when it is over (the choice follows). */
	private static final Map<UUID, Long> PENDING = new HashMap<>();

	private Pinecrest() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 == 13) {
				tick(server);
			}
		});
	}

	private static State state(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(State.TYPE);
	}

	/** Adam, after the house: wait for the reunion, ask, wait for the road, or (after) be home. */
	static void talkedToAdam(MinecraftServer server, ServerPlayer player) {
		StoryFlags flags = StoryFlags.get(server);
		if (flags.has(CHAPTER) || flags.has(CHOSEN)) {
			HarleyQuest.say(player, "Adam", "mission.deadlands.story.pinecrest.after." + player.getRandom().nextInt(3));
			return;
		}
		if (flags.has(REACHED)) {
			offer(player);
			return;
		}
		if (!flags.has(StoryReunion.FLAG)) {
			HarleyQuest.say(player, "Adam", "mission.deadlands.story.pinecrest.not_yet");
			return;
		}
		if (!MissionManager.isActive(server, MISSION)) {
			if (MissionManager.start(server, MISSION, player)) {
				for (int line = 1; line <= 3; line++) {
					HarleyQuest.say(player, "Adam", "mission.deadlands.story.pinecrest.ask." + line);
				}
				announce(player, build(player));
			}
			return;
		}
		HarleyQuest.say(player, "Adam", "mission.deadlands.story.pinecrest.waiting");
		BlockPos camp = state(server).camp;
		if (camp != null) {
			announce(player, camp);
		}
	}

	private static void announce(ServerPlayer player, BlockPos camp) {
		BlockPos gate = camp.offset(0, 0, R);
		Component coords = Component.translatable("mission.deadlands.story.pinecrest.where", gate.getX(), gate.getY(), gate.getZ());
		player.sendSystemMessage(coords.copy().withStyle(ChatFormatting.GOLD));
		player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 140, 30));
		player.connection.send(new ClientboundSetSubtitleTextPacket(coords));
		player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("mission.deadlands.story.pinecrest.where_title")));
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		StoryFlags flags = StoryFlags.get(server);
		BlockPos camp = state(server).camp;
		if (camp == null) {
			return;
		}
		Vec3 gate = Vec3.atBottomCenterOf(camp.offset(0, 0, R + 1));
		if (!flags.has(REACHED) && MissionManager.isActive(server, MISSION)) {
			for (ServerPlayer player : level.players()) {
				if (!player.isSpectator() && player.isAlive() && player.position().distanceToSqr(gate) <= 26.0 * 26.0) {
					arrive(level, player, gate);
					break;
				}
			}
		}
		long now = server.getTickCount();
		boolean due = false;
		for (Iterator<Map.Entry<UUID, Long>> it = PENDING.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Long> entry = it.next();
			if (now < entry.getValue()) {
				continue;
			}
			it.remove();
			due = true;
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player != null && !flags.has(CHOSEN)) {
				offer(player);
			}
		}
		// After the scene (or straight away after a restart mid-scene): everyone where they belong.
		if (flags.has(REACHED) && !flags.has(SETTLED) && (due || PENDING.isEmpty())) {
			settle(level, camp);
		}
	}

	/** The gate is reached: the scene plays, and the choice comes after it. */
	private static void arrive(ServerLevel level, ServerPlayer player, Vec3 gate) {
		StoryFlags flags = StoryFlags.get(level.getServer());
		flags.set(REACHED); // the mission's first objective
		flags.set(HARLEY_INFECTED);
		Cutscenes.playNear(level, Cutscenes.PINECREST, gate, 0.0F, 48.0);
		long when = level.getServer().getTickCount() + CUTSCENE_TICKS + 20;
		for (ServerPlayer near : level.players()) {
			if (!near.isSpectator() && near.position().distanceToSqr(gate) <= 48.0 * 48.0) {
				PENDING.put(near.getUUID(), Cutscenes.seen(near, Cutscenes.PINECREST) ? when : level.getServer().getTickCount() + 20);
			}
		}
		player.displayClientMessage(Component.translatable("mission.deadlands.story.pinecrest.arrived").withStyle(ChatFormatting.GOLD), false);
	}

	/** FelixSyn asks the survivor what they would do: two buttons in chat. */
	private static void offer(ServerPlayer player) {
		HarleyQuest.say(player, "FelixSyn", "mission.deadlands.story.pinecrest.question");
		MutableComponent stay = button("mission.deadlands.story.pinecrest.choice.stay", STAY, ChatFormatting.GREEN);
		MutableComponent go = button("mission.deadlands.story.pinecrest.choice.go", GO, ChatFormatting.AQUA);
		player.sendSystemMessage(Component.literal("   ").append(stay).append(Component.literal("   ")).append(go));
	}

	private static MutableComponent button(String key, String option, ChatFormatting color) {
		return Component.literal("[").append(Component.translatable(key)).append("]").withStyle(style -> style.withColor(color).withBold(true)
			.withClickEvent(new ClickEvent.RunCommand("/deadlands choose " + option))
			.withHoverEvent(new HoverEvent.ShowText(Component.translatable(key + ".hover"))));
	}

	/** The survivor's answer ({@code /deadlands choose}); returns false if there is nothing to choose. */
	public static boolean choose(ServerPlayer player, String option) {
		MinecraftServer server = player.level().getServer();
		StoryFlags flags = StoryFlags.get(server);
		BlockPos camp = state(server).camp;
		boolean stay = STAY.equals(option);
		if (camp == null || !flags.has(REACHED) || flags.has(CHOSEN) || !(stay || GO.equals(option))) {
			return false;
		}
		ServerLevel level = player.level();
		if (!flags.has(SETTLED)) {
			settle(level, camp);
		}
		flags.set(stay ? STAYED : WENT);
		flags.set(CHOSEN); // the mission's last objective: it completes on the next mission tick
		BlockPos felix = stay ? shelter(camp).offset(2, 0, 1) : camp.offset(-4, 0, 3);
		place(level, NpcCharacter.FELIXSYN, felix, stay ? 90.0F : 0.0F);
		String path = stay ? "stay" : "go";
		HarleyQuest.say(player, "FelixSyn", "mission.deadlands.story.pinecrest." + path + ".felix");
		HarleyQuest.say(player, "Harley", "mission.deadlands.story.pinecrest." + path + ".harley");
		player.displayClientMessage(Component.translatable("mission.deadlands.story.pinecrest." + path + ".end").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), false);
		Quests.chronicle(player, "chronicle.deadlands.story.pinecrest." + path);
		Achievements.award(player, "story/pinecrest");
		return true;
	}

	/** Adam and his family inside the camp; Harley (and, until the choice, FelixSyn) at the lean-to. */
	private static void settle(ServerLevel level, BlockPos camp) {
		StoryFlags flags = StoryFlags.get(level.getServer());
		if (!flags.set(SETTLED)) {
			return;
		}
		place(level, NpcCharacter.ADAM, camp.offset(5, 0, -3), 180.0F);
		BlockPos shelter = shelter(camp);
		place(level, NpcCharacter.HARLEY, shelter.offset(1, 0, 1), 90.0F);
		if (!flags.has(CHOSEN)) {
			place(level, NpcCharacter.FELIXSYN, shelter.offset(2, 0, 1), 90.0F);
		}
		// Adam's family: his wife and their two kids, by the medical tent.
		NpcEntity wife = family(level, camp.offset(6, 0, -2), "npc.deadlands.adams_wife", 1.0F);
		NpcEntity eldest = family(level, camp.offset(4, 0, -2), "npc.deadlands.adams_eldest", 0.7F);
		NpcEntity youngest = family(level, camp.offset(5, 0, -1), "npc.deadlands.adams_youngest", 0.55F);
		if (wife != null && eldest != null && youngest != null) {
			NpcEntity.link(wife, eldest, 1);
			NpcEntity.link(wife, youngest, 1);
			wife.setCustomName(Component.translatable("npc.deadlands.adams_wife"));
			eldest.setCustomName(Component.translatable("npc.deadlands.adams_eldest"));
			youngest.setCustomName(Component.translatable("npc.deadlands.adams_youngest"));
		}
	}

	/** A story character moved to {@code pos}: a fresh copy is recorded and any old one leaves the world. */
	private static void place(ServerLevel level, NpcCharacter character, BlockPos pos, float yaw) {
		BlockPos spot = SafeSpots.safe(level, pos) ? pos : SafeSpots.find(level, pos, 0, 6, level.getRandom());
		NpcSpawns.place(level, character, spot != null ? spot : pos, yaw);
	}

	private static @Nullable NpcEntity family(ServerLevel level, BlockPos pos, String name, float scale) {
		BlockPos spot = SafeSpots.safe(level, pos) ? pos : SafeSpots.find(level, pos, 0, 6, level.getRandom());
		NpcEntity npc = NpcSpawns.create(level, NpcRole.SETTLER, Faction.SETTLERS, null, spot != null ? spot : pos, 180.0F);
		if (npc != null) {
			npc.setCustomName(Component.translatable(name));
			if (scale != 1.0F && npc.getAttribute(Attributes.SCALE) != null) {
				npc.getAttribute(Attributes.SCALE).setBaseValue(scale);
			}
			npc.setPersistenceRequired();
		}
		return npc;
	}

	/** The lean-to outside the gate, to the east of the road. */
	private static BlockPos shelter(BlockPos camp) {
		return camp.offset(R + 6, 0, R + 4);
	}

	/** Build the camp once, 300 to 450 blocks from Adam, and return its middle. */
	private static BlockPos build(ServerPlayer player) {
		ServerLevel level = player.level();
		State state = state(level.getServer());
		if (state.camp != null) {
			return state.camp;
		}
		BlockPos adam = NpcWorldData.get(level.getServer()).position(NpcCharacter.ADAM);
		BlockPos from = adam != null ? adam : player.blockPosition();
		double angle = player.getRandom().nextDouble() * Math.PI * 2.0;
		double distance = 300.0 + player.getRandom().nextDouble() * 150.0;
		BlockPos around = from.offset((int) (Math.cos(angle) * distance), 0, (int) (Math.sin(angle) * distance));
		level.getChunk(around); // load it so the ground is known
		around = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, around);
		BlockPos spot = SafeSpots.find(level, around, 0, 32, player.getRandom());
		BlockPos camp = (spot != null ? spot : around).immutable();
		construct(level, camp);
		state.camp = camp;
		state.setDirty();
		return camp;
	}

	private static void set(ServerLevel level, BlockPos pos, BlockState block) {
		level.setBlock(pos, block, 2);
	}

	/** The camp itself, around {@code c} (its middle, at ground level); the gate faces +Z. */
	private static void construct(ServerLevel level, BlockPos c) {
		int reach = R + 12;
		// A level footing: clear above, fill below, packed earth inside, grass around.
		for (int x = -reach; x <= reach; x++) {
			for (int z = -R - 13; z <= reach; z++) {
				level.getChunk(c.offset(x, 0, z));
				for (int y = 0; y < 14; y++) {
					set(level, c.offset(x, y, z), Blocks.AIR.defaultBlockState());
				}
				for (int y = -2; y > -10; y--) {
					BlockPos below = c.offset(x, y, z);
					if (level.getBlockState(below).canBeReplaced()) {
						set(level, below, Blocks.DIRT.defaultBlockState());
					}
				}
				boolean inside = Math.abs(x) < R && Math.abs(z) < R;
				boolean road = Math.abs(x) <= 1 && z >= R;
				set(level, c.offset(x, -1, z), (inside || road ? Blocks.COARSE_DIRT : Blocks.GRASS_BLOCK).defaultBlockState());
				if (inside && level.getRandom().nextInt(5) == 0) {
					set(level, c.offset(x, -1, z), Blocks.DIRT_PATH.defaultBlockState());
				}
			}
		}
		// The reservoir behind the camp.
		for (int x = -9; x <= 9; x++) {
			for (int z = -R - 12; z <= -R - 3; z++) {
				double edge = Math.pow(x / 9.5, 2) + Math.pow((z + R + 7.5) / 5.5, 2);
				if (edge <= 1.0) {
					set(level, c.offset(x, -1, z), Blocks.WATER.defaultBlockState());
					set(level, c.offset(x, -2, z), Blocks.WATER.defaultBlockState());
					set(level, c.offset(x, -3, z), Blocks.GRAVEL.defaultBlockState());
				} else if (edge <= 1.25) {
					set(level, c.offset(x, -1, z), Blocks.SAND.defaultBlockState());
				}
			}
		}
		// The palisade: sharpened spruce logs, four high, a three-wide gate in the +Z side.
		for (int i = -R; i <= R; i++) {
			for (int[] side : new int[][] {{i, -R}, {i, R}, {-R, i}, {R, i}}) {
				if (side[1] == R && Math.abs(side[0]) <= 1) {
					continue; // the gate
				}
				int height = 4 + (Math.floorMod(side[0] * 7 + side[1] * 3, 3) == 0 ? 1 : 0);
				for (int y = 0; y < height; y++) {
					set(level, c.offset(side[0], y, side[1]), Blocks.SPRUCE_LOG.defaultBlockState());
				}
				set(level, c.offset(side[0], height, side[1]), Blocks.SPRUCE_FENCE.defaultBlockState());
			}
		}
		// Watchtowers either side of the gate, a lantern on each.
		for (int sx : new int[] {-3, 2}) {
			for (int dx = 0; dx < 2; dx++) {
				for (int dz = 0; dz < 2; dz++) {
					for (int y = 0; y < 7; y++) {
						set(level, c.offset(sx + dx, y, R - 1 + dz), Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
					}
				}
			}
			for (int dx = -1; dx <= 2; dx++) {
				for (int dz = -1; dz <= 2; dz++) {
					set(level, c.offset(sx + dx, 7, R - 1 + dz), Blocks.SPRUCE_PLANKS.defaultBlockState());
					if (dx == -1 || dx == 2 || dz == -1 || dz == 2) {
						set(level, c.offset(sx + dx, 8, R - 1 + dz), Blocks.SPRUCE_FENCE.defaultBlockState());
					}
				}
			}
			set(level, c.offset(sx, 9, R - 1), Blocks.LANTERN.defaultBlockState());
		}
		// Tents along the sides, the medical tent (a red cross) where Adam's family waits.
		tent(level, c.offset(-8, 0, -7), Blocks.WHITE_WOOL.defaultBlockState(), false);
		tent(level, c.offset(-8, 0, 1), Blocks.LIGHT_GRAY_WOOL.defaultBlockState(), false);
		tent(level, c.offset(-1, 0, -8), Blocks.GREEN_WOOL.defaultBlockState(), false);
		tent(level, c.offset(7, 0, -7), Blocks.WHITE_WOOL.defaultBlockState(), true);
		tent(level, c.offset(8, 0, 3), Blocks.BROWN_WOOL.defaultBlockState(), false);
		// The fire in the middle, logs to sit on, barrels and lanterns.
		set(level, c, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
		for (int[] seat : new int[][] {{2, 0}, {-2, 0}, {0, 2}, {0, -2}}) {
			set(level, c.offset(seat[0], 0, seat[1]), Blocks.SPRUCE_LOG.defaultBlockState()
				.setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, seat[0] != 0 ? Direction.Axis.Z : Direction.Axis.X));
		}
		for (int[] barrel : new int[][] {{-11, 10}, {-11, 9}, {-10, 10}, {11, -11}, {10, -11}, {11, 10}}) {
			set(level, c.offset(barrel[0], 0, barrel[1]), Blocks.BARREL.defaultBlockState());
		}
		for (int[] post : new int[][] {{-5, 5}, {5, 5}, {-5, -4}, {5, -4}}) {
			set(level, c.offset(post[0], 0, post[1]), Blocks.SPRUCE_FENCE.defaultBlockState());
			set(level, c.offset(post[0], 1, post[1]), Blocks.SPRUCE_FENCE.defaultBlockState());
			set(level, c.offset(post[0], 2, post[1]), Blocks.LANTERN.defaultBlockState());
		}
		set(level, c.offset(2, 0, R + 1), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
		shelter(level, shelter(c));
	}

	/** A ridge tent five long, its open end facing +Z (or -X when {@code medical}, a red cross on its side). */
	private static void tent(ServerLevel level, BlockPos at, BlockState cloth, boolean medical) {
		for (int along = 0; along < 5; along++) {
			for (int across = -2; across <= 2; across++) {
				int height = 2 - Math.abs(across);
				BlockPos column = at.offset(across, 0, along);
				set(level, column.above(height), cloth);
				if (along == 0 && Math.abs(across) == 2) {
					set(level, column, Blocks.SPRUCE_FENCE.defaultBlockState());
				}
				if (along == 4 && height > 0) {
					for (int y = 0; y < height; y++) {
						set(level, column.above(y), cloth);
					}
				}
			}
		}
		set(level, at.offset(-1, 0, 3), Blocks.RED_BED.defaultBlockState()
			.setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.SOUTH)
			.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
		set(level, at.offset(-1, 0, 2), Blocks.RED_BED.defaultBlockState()
			.setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.SOUTH)
			.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT));
		if (medical) {
			BlockState red = Blocks.RED_WOOL.defaultBlockState();
			set(level, at.offset(0, 2, 2), red);
			set(level, at.offset(0, 2, 1), red);
			set(level, at.offset(0, 2, 3), red);
			set(level, at.offset(1, 1, 2), red);
			set(level, at.offset(-1, 1, 2), red);
			set(level, at.offset(1, 0, 1), Blocks.BREWING_STAND.defaultBlockState());
		}
	}

	/** Harley's lean-to: a plank floor, a sloped roof on four posts, a fire and a bedroll. */
	private static void shelter(ServerLevel level, BlockPos s) {
		for (int x = 0; x < 4; x++) {
			for (int z = 0; z < 3; z++) {
				set(level, s.offset(x, -1, z), Blocks.SPRUCE_PLANKS.defaultBlockState());
				set(level, s.offset(x, x < 2 ? 3 : 2, z), Blocks.SPRUCE_SLAB.defaultBlockState());
			}
		}
		for (int[] post : new int[][] {{0, 0}, {0, 2}, {3, 0}, {3, 2}}) {
			for (int y = 0; y < (post[0] == 0 ? 3 : 2); y++) {
				set(level, s.offset(post[0], y, post[1]), Blocks.SPRUCE_FENCE.defaultBlockState());
			}
		}
		set(level, s.offset(-2, 0, 1), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
		set(level, s.offset(1, 0, 2), Blocks.BROWN_CARPET.defaultBlockState());
		set(level, s.offset(2, 0, 2), Blocks.BROWN_CARPET.defaultBlockState());
		set(level, s.offset(3, 0, 1), Blocks.BARREL.defaultBlockState());
	}
}
