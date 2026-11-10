package com.deadlands.npc;

import com.deadlands.registry.DLMarkers;
import com.deadlands.world.town.TownCondition;
import com.deadlands.world.town.TownLookup;
import com.deadlands.world.town.TownSite;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * Where survivors come from.
 *
 * <ul>
 *   <li><b>Settlements.</b> Buildings in survivor holdouts, military checkpoints and bandit towns
 *       hold spawn markers; each becomes a resident whose faction follows the town (settlers,
 *       military, bandits) and whose home is where they first stood.</li>
 *   <li><b>Recurring characters.</b> Harley is placed near where the first survivor arrives. The
 *       other three are met on the road: every half minute, each survivor who has covered at least
 *       {@value #EXPLORED_BLOCKS} blocks since the last check (exploring, not standing about) may
 *       meet the next one, who appears 24 to 40 blocks away ({@link #encounterChance}); so they are
 *       met in a different order in every world. Each appears once per
 *       world, ever ({@link NpcWorldData}). If the road is unlucky, the story places those still
 *       missing near Harley's camp two days after she is met ({@code StoryDirector}).
 *       {@code /deadlands npc <name>} places one by hand only if they have not been placed yet or
 *       were lost.</li>
 *   <li><b>The road.</b> By day a travelling merchant now and then appears near a survivor who is
 *       out in the open, stays ten minutes, and moves on once nobody is near.</li>
 * </ul>
 */
public final class NpcSpawns {
	private static final int WANDERER_CHECK_TICKS = 2400;
	private static final long WANDERER_STAYS_TICKS = 12000L;
	/**
	 * The chance, at each check, that an exploring survivor meets the next recurring character. It
	 * holds for the first {@value #RISING_AFTER_CHECKS} exploring checks since anyone was last met
	 * (40 minutes), then rises evenly to certain at {@value #CERTAIN_AFTER_CHECKS} (an hour), so
	 * nobody waits for ever. With a check every half minute that is about 27 minutes of exploring
	 * per character on average: one in ten within 5, half within 29, nine in ten within 45, and never
	 * more than 60. The three met on the road take about 82 minutes (Harley is there from the start).
	 */
	public static final float CHARACTER_ENCOUNTER_CHANCE = 0.012F;
	private static final int RISING_AFTER_CHECKS = 80;
	private static final int CERTAIN_AFTER_CHECKS = 120;
	private static final int ENCOUNTER_CHECK_TICKS = 600;
	/** How far a survivor must have gone since the last check for it to count as exploring. */
	private static final double EXPLORED_BLOCKS = 48.0;
	private static final Map<UUID, BlockPos> LAST_CHECKED = new HashMap<>();

	private NpcSpawns() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % WANDERER_CHECK_TICKS == 1200) {
				wanderers(server);
			}
			if (server.getTickCount() % ENCOUNTER_CHECK_TICKS == 300) {
				encounters(server);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST_CHECKED.remove(handler.getPlayer().getUUID()));
	}

	/** A resident for a settlement, from a spawn marker. */
	public static @Nullable NpcEntity spawn(ServerLevel level, NpcRole role, BlockPos pos, float yaw, RandomSource random) {
		TownSite town = TownLookup.nearest(level, pos, 170.0)
			.filter(site -> Math.hypot(site.centerX() - pos.getX(), site.centerZ() - pos.getZ()) <= site.size().gateRadius() + 16)
			.orElse(null);
		Settlements settlements = Settlements.get(level.getServer());
		long townKey = 0L;
		if (town != null && Settlements.settled(town.condition())) {
			Settlements.Settlement existing = settlements.of(town.key());
			if (existing != null && existing.fallen()) {
				return null; // nobody is left to live here
			}
			townKey = town.key();
		}
		Faction faction = role == NpcRole.BANDIT ? Faction.BANDITS
			: role == NpcRole.RESEARCHER ? Faction.RESEARCH
			: town == null ? (role == NpcRole.GUARD ? Faction.MILITARY : Faction.DRIFTERS)
			: town.condition() == TownCondition.MILITARY ? Faction.MILITARY
			: town.condition() == TownCondition.BANDIT ? Faction.BANDITS : Faction.SETTLERS;
		boolean camp = town == null && role != NpcRole.BANDIT;
		if (camp && role != NpcRole.RESEARCHER) {
			Faction joined = settlements.campFaction(pos);
			if (joined != null) {
				faction = joined;
			}
		}
		NpcEntity npc = create(level, role, faction, null, pos, yaw);
		if (npc != null && townKey != 0L) {
			settlements.register(town);
			npc.setTown(townKey);
		} else if (npc != null && camp) {
			npc.setTown(settlements.registerCamp(level, pos, npc.faction()));
		}
		return npc;
	}

	/**
	 * Put a recurring character at a position and record them as placed. Callers check first that
	 * they have not been placed (or were lost); this entity becomes the one copy of them.
	 */
	public static @Nullable NpcEntity place(ServerLevel level, NpcCharacter character, BlockPos pos, float yaw) {
		NpcWorldData data = NpcWorldData.get(level.getServer());
		data.markPlaced(character, pos);
		NpcEntity npc = create(level, character.role(), character.faction(), character, pos, yaw);
		if (npc != null) {
			data.own(character, npc.getUUID());
		}
		return npc;
	}

	/** Every half minute: may an exploring survivor meet the next recurring character? */
	private static void encounters(MinecraftServer server) {
		ServerLevel level = server.overworld();
		NpcWorldData data = NpcWorldData.get(server);
		for (ServerPlayer player : level.players()) {
			BlockPos now = player.blockPosition();
			BlockPos before = LAST_CHECKED.put(player.getUUID(), now);
			if (before == null || player.isSpectator()
				|| Math.hypot(now.getX() - before.getX(), now.getZ() - before.getZ()) < EXPLORED_BLOCKS
				|| !level.canSeeSky(now)) {
				continue;
			}
			NpcCharacter next = data.nextUnplaced(player.getRandom());
			if (next == null) {
				return; // everyone has been met
			}
			if (player.getRandom().nextFloat() >= encounterChance(data.countExploring())) {
				continue;
			}
			if (meet(level, player, next)) {
				return; // one encounter at a time
			}
		}
	}

	/** The encounter chance at the given exploring check since a character was last placed (see {@link #CHARACTER_ENCOUNTER_CHANCE}). */
	public static float encounterChance(int checks) {
		if (checks <= RISING_AFTER_CHECKS) {
			return CHARACTER_ENCOUNTER_CHANCE;
		}
		float rise = Math.min(1.0F, (checks - RISING_AFTER_CHECKS) / (float) (CERTAIN_AFTER_CHECKS - RISING_AFTER_CHECKS));
		return CHARACTER_ENCOUNTER_CHANCE + rise * (1.0F - CHARACTER_ENCOUNTER_CHANCE);
	}

	/** Put a character on open ground 24 to 40 blocks from a survivor, facing them. */
	private static boolean meet(ServerLevel level, ServerPlayer player, NpcCharacter character) {
		RandomSource random = player.getRandom();
		for (int attempt = 0; attempt < 8; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double distance = 24.0 + random.nextDouble() * 16.0;
			int x = player.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
			int z = player.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
			BlockPos column = new BlockPos(x, player.getBlockY(), z);
			if (!level.isLoaded(column)) {
				continue;
			}
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos pos = new BlockPos(x, y, z);
			if (Math.abs(y - player.getBlockY()) > 10 || !level.getFluidState(pos.below()).isEmpty()
				|| !level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()) {
				continue;
			}
			float yaw = (float) Math.toDegrees(Math.atan2(-(player.getX() - (x + 0.5)), player.getZ() - (z + 0.5)));
			NpcEntity npc = place(level, character, pos, yaw);
			if (npc != null) {
				npc.voice(com.deadlands.registry.DLSounds.NPC_GREET.value());
				return true;
			}
		}
		return false;
	}

	public static @Nullable NpcEntity create(ServerLevel level, NpcRole role, Faction faction, @Nullable NpcCharacter character, BlockPos pos, float yaw) {
		NpcEntity npc = DLMarkers.SURVIVOR.create(level, EntitySpawnReason.STRUCTURE);
		if (npc == null) {
			return null;
		}
		npc.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0.0F);
		npc.assign(role, faction, character, pos);
		level.addFreshEntity(npc);
		relate(level, npc);
		return npc;
	}

	/** Half of all survivors have someone among the neighbours already there. */
	private static void relate(ServerLevel level, NpcEntity npc) {
		if (npc.character() != null || npc.getRandom().nextFloat() >= 0.5F) {
			return;
		}
		for (NpcEntity other : level.getEntitiesOfClass(NpcEntity.class, npc.getBoundingBox().inflate(40.0),
			o -> o != npc && o.isAlive() && o.relation() == 0 && o.character() == null && o.faction() == npc.faction())) {
			NpcEntity.link(npc, other, 1 + npc.getRandom().nextInt(3));
			return;
		}
	}

	/** Harley's camp is where the story starts: she stands a few blocks from the first arrival. */
	public static void onFirstArrival(ServerPlayer player) {
		ServerLevel level = player.level();
		NpcWorldData data = NpcWorldData.get(level.getServer());
		if (data.isPlaced(NpcCharacter.HARLEY)) {
			return;
		}
		RandomSource random = player.getRandom();
		for (int attempt = 0; attempt < 12; attempt++) {
			int x = player.getBlockX() + random.nextInt(13) - 6;
			int z = player.getBlockZ() + random.nextInt(13) - 6;
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos pos = new BlockPos(x, y, z);
			if (Math.abs(y - player.getBlockY()) <= 4 && level.getBlockState(pos).isAir() && level.getFluidState(pos.below()).isEmpty()) {
				place(level, NpcCharacter.HARLEY, pos, random.nextFloat() * 360.0F);
				return;
			}
		}
	}

	private static void wanderers(MinecraftServer server) {
		ServerLevel level = server.overworld();
		if (!level.isBrightOutside()) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			RandomSource random = player.getRandom();
			if (player.isSpectator() || random.nextFloat() >= 0.12F || !level.canSeeSky(player.blockPosition())
				|| !level.getEntitiesOfClass(NpcEntity.class, player.getBoundingBox().inflate(64.0)).isEmpty()) {
				continue;
			}
			double angle = random.nextDouble() * Math.PI * 2.0;
			int x = player.getBlockX() + (int) (Math.cos(angle) * 30.0);
			int z = player.getBlockZ() + (int) (Math.sin(angle) * 30.0);
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos pos = new BlockPos(x, y, z);
			if (!level.getFluidState(pos.below()).isEmpty() || Math.abs(y - player.getBlockY()) > 12) {
				continue;
			}
			NpcEntity merchant = create(level, NpcRole.WANDERER, Faction.DRIFTERS, null, pos, random.nextFloat() * 360.0F);
			if (merchant != null) {
				merchant.setLeavesAt(level.getGameTime() + WANDERER_STAYS_TICKS);
			}
		}
	}
}
