package com.deadlands.npc;

import com.deadlands.event.DayChangeCallback;
import com.deadlands.world.town.TownCondition;
import com.deadlands.world.town.TownLookup;
import com.deadlands.world.town.TownSite;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Inhabited towns as places that change ({@code <world>/data/deadlands_settlements.dat}).
 *
 * <p>A settlement is created the first time one of its residents appears, and from then on it has
 * a population, stores (supplies), defences and morale. Once a day, whether or not anyone is near,
 * each settlement is simulated in a few lines: it eats and forages, it may be attacked (stronger
 * attacks as the days go by, held off by defences and numbers), people may leave or starve when the
 * stores are empty, and newcomers arrive when it is doing well. This is the far-away simulation:
 * nothing is loaded, only these numbers change.
 *
 * <p>The numbers reach the world when a survivor returns. A resident lost while nobody was there
 * is removed as their chunk loads; a newcomer walks in near the middle of town the next time a
 * survivor is close; a settlement whose population reaches zero has fallen, and everyone left in
 * it is gone. Residents killed while loaded lower the population directly.
 *
 * <p>Survivors change the numbers too: food and medicine given to the leader fill the stores,
 * building materials raise the defences, and killing infected near residents during a horde
 * counts for defence and morale. A settlement short of supplies charges more.
 *
 * <p>Going through a settlement's containers where a resident can see is theft and costs
 * reputation with its faction.
 *
 * <p><b>Walls.</b> As a town's defences grow past 30, 60 and 90 its people build: barricades
 * flanking each of the four gates, longer and higher with each stage. Building happens while a
 * survivor is near enough for the gates to be loaded, one stage at a time.
 *
 * <p><b>Camps.</b> Survivor and research camps are settlements too, small ones (six people at
 * most) without walls, found by position instead of by town.
 */
public final class Settlements extends SavedData {
	/** One settlement's state. */
	public static final class Settlement {
		public static final Codec<Settlement> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.fieldOf("faction").forGetter(s -> s.faction),
			Codec.INT.fieldOf("x").forGetter(s -> s.x),
			Codec.INT.fieldOf("y").forGetter(s -> s.y),
			Codec.INT.fieldOf("z").forGetter(s -> s.z),
			Codec.INT.fieldOf("radius").forGetter(s -> s.radius),
			Codec.INT.fieldOf("population").forGetter(s -> s.population),
			Codec.INT.fieldOf("supplies").forGetter(s -> s.supplies),
			Codec.INT.fieldOf("defence").forGetter(s -> s.defence),
			Codec.INT.fieldOf("morale").forGetter(s -> s.morale),
			Codec.INT.fieldOf("losses").forGetter(s -> s.losses),
			Codec.INT.fieldOf("arrivals").forGetter(s -> s.arrivals),
			Codec.STRING.fieldOf("event").forGetter(s -> s.lastEvent),
			Codec.BOOL.fieldOf("fallen").forGetter(s -> s.fallen),
			Codec.INT.optionalFieldOf("walls", 0).forGetter(s -> s.walls),
			Codec.BOOL.optionalFieldOf("camp", false).forGetter(s -> s.camp)
		).apply(instance, Settlement::new));

		private int faction;
		/** Stages of wall built (0 to 3). */
		private int walls;
		private final boolean camp;
		private final int x;
		private final int y;
		private final int z;
		private final int radius;
		private int population;
		private int supplies;
		private int defence;
		private int morale;
		/** Residents lost while unloaded, still to be removed from the world. */
		private int losses;
		/** Newcomers still to walk in. */
		private int arrivals;
		private String lastEvent;
		private boolean fallen;

		private Settlement(int faction, int x, int y, int z, int radius, int population, int supplies, int defence, int morale, int losses, int arrivals,
			String lastEvent, boolean fallen, int walls, boolean camp) {
			this.faction = faction;
			this.x = x;
			this.y = y;
			this.z = z;
			this.radius = radius;
			this.population = population;
			this.supplies = supplies;
			this.defence = defence;
			this.morale = morale;
			this.losses = losses;
			this.arrivals = arrivals;
			this.lastEvent = lastEvent;
			this.fallen = fallen;
			this.walls = walls;
			this.camp = camp;
		}

		public boolean camp() {
			return camp;
		}

		public int walls() {
			return walls;
		}

		/** The wall stage this settlement's defences call for. */
		public int wallsWanted() {
			return camp ? 0 : defence >= 90 ? 3 : defence >= 60 ? 2 : defence >= 30 ? 1 : 0;
		}

		int maxPopulation() {
			return camp ? 6 : MAX_POPULATION;
		}

		public Faction faction() {
			return Faction.byIndex(faction);
		}

		public BlockPos center() {
			return new BlockPos(x, y, z);
		}

		public int radius() {
			return radius;
		}

		public int population() {
			return population;
		}

		public int supplies() {
			return supplies;
		}

		public int defence() {
			return defence;
		}

		public int morale() {
			return morale;
		}

		public String lastEvent() {
			return lastEvent;
		}

		public boolean fallen() {
			return fallen;
		}

		/** People live here still. */
		public boolean inhabited() {
			return !fallen && population > 0;
		}

		/** "plenty", "enough", "short" or "none": the stores per head. */
		public String suppliesLevel() {
			int perHead = supplies / Math.max(1, population);
			return supplies <= 0 ? "none" : perHead >= 4 ? "plenty" : perHead >= 2 ? "enough" : "short";
		}

		public String defenceLevel() {
			return defence >= 60 ? "strong" : defence >= 30 ? "fair" : "weak";
		}

		/** What a leader says of the place. */
		public Component status() {
			return Component.translatable("npc.deadlands.settlement.status", population,
				Component.translatable("npc.deadlands.settlement.supplies." + suppliesLevel()),
				Component.translatable("npc.deadlands.settlement.defence." + defenceLevel()),
				Component.translatable("npc.deadlands.settlement.event." + lastEvent));
		}

		/** One simulated day. */
		void day(RandomSource random, long dayNumber) {
			if (fallen) {
				return;
			}
			lastEvent = "quiet";
			supplies += Math.round(population * 0.9F) + random.nextInt(4) - population;
			if (supplies < 0) {
				supplies = 0;
				morale = Math.max(0, morale - 10);
				lastEvent = "hunger";
				if (random.nextFloat() < 0.3F) {
					lose(1);
				}
			}
			float attackChance = Math.min(0.5F, 0.15F + dayNumber * 0.002F);
			if (random.nextFloat() < attackChance) {
				int strength = random.nextInt(20 + (int) Math.min(dayNumber, 200L)) / (camp ? 2 : 1);
				if (strength > defence + population * 4) {
					lose(1 + random.nextInt(2));
					defence = Math.max(0, defence - 5);
					morale = Math.max(0, morale - 8);
					lastEvent = "attack_lost";
				} else {
					defence = Math.max(0, defence - 2);
					morale = Math.min(100, morale + 2);
					lastEvent = "attack_held";
				}
			}
			if (!fallen && supplies > population * 3 && morale > 50 && population + arrivals < maxPopulation() && random.nextFloat() < 0.25F) {
				arrivals = Math.min(3, arrivals + 1);
				lastEvent = "arrival";
			}
			supplies = Math.min(MAX_SUPPLIES, supplies);
		}

		private void lose(int count) {
			int lost = Math.min(count, population);
			population -= lost;
			losses += lost;
			if (population <= 0) {
				fallen = true;
				lastEvent = "fallen";
			}
		}
	}

	public static final int MAX_POPULATION = 16;
	public static final int MAX_SUPPLIES = 200;
	private static final long THEFT_COOLDOWN = 200L;
	private static final Codec<Settlements> CODEC = Codec.unboundedMap(Codec.STRING, Settlement.CODEC).fieldOf("settlements").codec()
		.xmap(Settlements::new, Settlements::saved);
	public static final SavedDataType<Settlements> TYPE = new SavedDataType<>("deadlands_settlements", Settlements::new, CODEC, null);
	private static final Map<UUID, Long> LAST_THEFT = new HashMap<>();

	private final Map<Long, Settlement> byTown = new HashMap<>();

	public Settlements() {
	}

	private Settlements(Map<String, Settlement> saved) {
		saved.forEach((key, settlement) -> {
			try {
				byTown.put(Long.parseLong(key), settlement);
			} catch (NumberFormatException ignored) {
				// A hand-edited file with a bad key: drop that entry.
			}
		});
	}

	private Map<String, Settlement> saved() {
		Map<String, Settlement> out = new HashMap<>();
		byTown.forEach((key, settlement) -> out.put(Long.toString(key), settlement));
		return out;
	}

	public static Settlements get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public static void init() {
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST_THEFT.remove(handler.getPlayer().getUUID()));
		DayChangeCallback.EVENT.register((server, previousDay, newDay, skippedDays) -> get(server).simulate(server, newDay, (int) Math.min(skippedDays + 1, 10L)));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			int interval = Math.max(20, com.deadlands.config.ConfigManager.get().performance.farSimulationInterval);
			if (server.getTickCount() % interval == 60 % interval) {
				get(server).arrivals(server);
				get(server).buildWalls(server);
			}
		});
	}

	/** Do people of a faction hold towns in this condition? */
	public static boolean settled(TownCondition condition) {
		return condition == TownCondition.SURVIVOR || condition == TownCondition.MILITARY || condition == TownCondition.BANDIT;
	}

	public static Faction factionOf(TownCondition condition) {
		return condition == TownCondition.MILITARY ? Faction.MILITARY : condition == TownCondition.BANDIT ? Faction.BANDITS : Faction.SETTLERS;
	}

	public @Nullable Settlement of(long townKey) {
		return byTown.get(townKey);
	}

	public java.util.Collection<Settlement> all() {
		return java.util.Collections.unmodifiableCollection(byTown.values());
	}

	public int count() {
		return byTown.size();
	}

	/** A resident has appeared in {@code town}: create the settlement if this is the first, and count them. */
	public Settlement register(TownSite town) {
		Settlement settlement = byTown.computeIfAbsent(town.key(), key -> {
			Faction faction = factionOf(town.condition());
			int defence = faction == Faction.MILITARY ? 45 : faction == Faction.BANDITS ? 30 : 20;
			return new Settlement(faction.ordinal(), town.centerX(), town.centerY(), town.centerZ(), town.size().radius(), 0, 30, defence, 60, 0, 0, "quiet", false, 0, false);
		});
		settlement.population = Math.min(MAX_POPULATION * 2, settlement.population + 1);
		settlement.supplies = Math.min(MAX_SUPPLIES, settlement.supplies + 3);
		setDirty();
		return settlement;
	}

	/**
	 * A resident has appeared in a camp (outside any town): join the camp within 24 blocks, or start one
	 * here. Returns its key. A researcher turns a camp into a research camp, and those already in it
	 * with it.
	 */
	public long registerCamp(ServerLevel level, BlockPos pos, Faction faction) {
		for (Map.Entry<Long, Settlement> entry : byTown.entrySet()) {
			Settlement settlement = entry.getValue();
			if (settlement.camp && !settlement.fallen && pos.distSqr(settlement.center()) <= 24.0 * 24.0) {
				if (faction == Faction.RESEARCH && settlement.faction() != Faction.RESEARCH) {
					Faction before = settlement.faction();
					settlement.faction = Faction.RESEARCH.ordinal();
					for (NpcEntity npc : level.getEntitiesOfClass(NpcEntity.class, new AABB(settlement.center()).inflate(32.0),
						npc -> npc.townKey() == entry.getKey() && npc.faction() == before)) {
						npc.setFaction(Faction.RESEARCH);
					}
				}
				settlement.population = Math.min(settlement.maxPopulation(), settlement.population + 1);
				setDirty();
				return entry.getKey();
			}
		}
		long key = pos.asLong();
		byTown.put(key, new Settlement(faction.ordinal(), pos.getX(), pos.getY(), pos.getZ(), 12, 1, 15, 10, 60, 0, 0, "quiet", false, 0, true));
		setDirty();
		return key;
	}

	/** The faction a camp's newcomer joins: the camp's own, if one is near. */
	public @Nullable Faction campFaction(BlockPos pos) {
		for (Settlement settlement : byTown.values()) {
			if (settlement.camp && !settlement.fallen && pos.distSqr(settlement.center()) <= 24.0 * 24.0) {
				return settlement.faction();
			}
		}
		return null;
	}

	/**
	 * Should a resident who has just been loaded be gone? Yes if the settlement fell, or if it lost
	 * people while unloaded and this one is not {@code essential} (leaders and the recurring
	 * characters are never taken this way).
	 */
	public boolean claimLoss(long townKey, boolean essential) {
		Settlement settlement = byTown.get(townKey);
		if (settlement == null) {
			return false;
		}
		if (settlement.fallen) {
			return true;
		}
		if (essential || settlement.losses <= 0) {
			return false;
		}
		settlement.losses--;
		setDirty();
		return true;
	}

	/** A resident died where someone could see it. */
	public void residentDied(long townKey) {
		Settlement settlement = byTown.get(townKey);
		if (settlement == null || settlement.fallen) {
			return;
		}
		settlement.population = Math.max(0, settlement.population - 1);
		settlement.morale = Math.max(0, settlement.morale - 5);
		if (settlement.population <= 0) {
			settlement.fallen = true;
			settlement.lastEvent = "fallen";
		}
		setDirty();
	}

	/** Infected killed near residents while a horde is on. */
	public void defended(long townKey) {
		Settlement settlement = byTown.get(townKey);
		if (settlement != null && !settlement.fallen) {
			settlement.defence = Math.min(100, settlement.defence + 1);
			settlement.morale = Math.min(100, settlement.morale + 1);
			setDirty();
		}
	}

	/** A gift to the stores or the walls. */
	public void give(Settlement settlement, int supplies, int defence) {
		settlement.supplies = Mth.clamp(settlement.supplies + supplies, 0, MAX_SUPPLIES);
		settlement.defence = Mth.clamp(settlement.defence + defence, 0, 100);
		settlement.morale = Math.min(100, settlement.morale + 2);
		setDirty();
	}

	/** What a settlement's state does to its traders' prices: dearer when the stores run low. */
	public float priceFactor(long townKey) {
		Settlement settlement = byTown.get(townKey);
		if (settlement == null) {
			return 1.0F;
		}
		return switch (settlement.suppliesLevel()) {
			case "none" -> 1.5F;
			case "short" -> 1.25F;
			case "plenty" -> 0.9F;
			default -> 1.0F;
		};
	}

	/** Run {@code days} simulated days for every settlement. */
	public void simulate(MinecraftServer server, long newDay, int days) {
		long seed = server.overworld().getSeed();
		ServerLevel overworld = server.overworld();
		double near = Math.max(2, com.deadlands.config.ConfigManager.get().performance.fullSimulationChunks) * 16.0;
		byTown.forEach((key, settlement) -> {
			// A settlement someone is standing in is fully simulated by its real residents: no abstract losses or arrivals.
			if (overworld.hasNearbyAlivePlayer(settlement.x, settlement.y, settlement.z, near)) {
				return;
			}
			for (int i = days - 1; i >= 0; i--) {
				long day = newDay - i;
				settlement.day(RandomSource.create(seed ^ key * 31L ^ day * 0x9E3779B97F4A7C15L), day);
			}
		});
		if (!byTown.isEmpty()) {
			setDirty();
		}
	}

	/** Newcomers walk in near the middle of town when a survivor is close enough to see the place. */
	private void arrivals(MinecraftServer server) {
		ServerLevel level = server.overworld();
		for (Map.Entry<Long, Settlement> entry : byTown.entrySet()) {
			Settlement settlement = entry.getValue();
			if (settlement.arrivals <= 0 || settlement.fallen || !level.isLoaded(settlement.center())
				|| !level.hasNearbyAlivePlayer(settlement.x, settlement.y, settlement.z, 128.0)) {
				continue;
			}
			RandomSource random = level.getRandom();
			int x = settlement.x + random.nextInt(13) - 6;
			int z = settlement.z + random.nextInt(13) - 6;
			BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
			if (!level.getFluidState(pos.below()).isEmpty()) {
				continue;
			}
			settlement.arrivals--;
			settlement.population++;
			setDirty();
			NpcRole role = settlement.faction() == Faction.MILITARY ? NpcRole.GUARD : settlement.faction() == Faction.BANDITS ? NpcRole.BANDIT : NpcRole.SETTLER;
			NpcEntity newcomer = NpcSpawns.create(level, role, settlement.faction(), null, pos, random.nextFloat() * 360.0F);
			if (newcomer != null) {
				newcomer.setTown(entry.getKey());
			}
		}
	}

	/** Build the next wall stage where defences call for it and the gates are loaded with a survivor near. */
	private void buildWalls(MinecraftServer server) {
		ServerLevel level = server.overworld();
		for (Settlement settlement : byTown.values()) {
			if (!settlement.inhabited() || settlement.walls >= settlement.wallsWanted()
				|| !level.hasNearbyAlivePlayer(settlement.x, settlement.y, settlement.z, 128.0)) {
				continue;
			}
			int reach = settlement.radius + 4;
			boolean loaded = true;
			for (net.minecraft.core.Direction side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
				loaded &= level.isLoaded(new BlockPos(settlement.x + side.getStepX() * reach, settlement.y, settlement.z + side.getStepZ() * reach));
			}
			if (!loaded) {
				continue;
			}
			settlement.walls++;
			for (net.minecraft.core.Direction side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
				wallStage(level, settlement, side, reach, settlement.walls);
			}
			setDirty();
		}
	}

	/** One stage of wall either side of one gate: from just beyond the gate's own barricade outward along the edge of town. */
	private static void wallStage(ServerLevel level, Settlement settlement, net.minecraft.core.Direction outward, int reach, int stage) {
		net.minecraft.core.Direction along = outward.getClockWise();
		int gateX = settlement.x + outward.getStepX() * reach - outward.getStepX();
		int gateZ = settlement.z + outward.getStepZ() * reach - outward.getStepZ();
		int from = stage == 1 ? 4 : stage == 2 ? 9 : 4;
		int to = stage == 1 ? 8 : stage == 2 ? 13 : 13;
		net.minecraft.world.level.block.state.BlockState barrier = com.deadlands.registry.DLDecor.get(com.deadlands.block.DecorKind.CONCRETE_BARRIER)
			.defaultBlockState().setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, outward);
		for (int sign = -1; sign <= 1; sign += 2) {
			for (int w = from; w <= to; w++) {
				int x = gateX + along.getStepX() * w * sign;
				int z = gateZ + along.getStepZ() * w * sign;
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				if (Math.abs(y - settlement.y) > 6) {
					continue; // a cliff or a ditch: leave it
				}
				for (int k = 0; k < (stage == 3 ? 3 : 2); k++) {
					BlockPos pos = new BlockPos(x, y + k, z);
					if (!level.getBlockState(pos).canBeReplaced()) {
						continue;
					}
					net.minecraft.world.level.block.state.BlockState state = stage == 2 && k == 0 ? barrier
						: (w + k) % 3 == 0 ? com.deadlands.registry.DLBlocks.CORRUGATED_METAL.defaultBlockState()
						: com.deadlands.registry.DLBlocks.BOARDED_PLANKS.defaultBlockState();
					level.setBlock(pos, state, net.minecraft.world.level.block.Block.UPDATE_ALL);
				}
			}
		}
	}

	/** The settlement a position is inside, if people live there. */
	public static @Nullable Settlement at(ServerLevel level, BlockPos pos) {
		Settlements all = get(level.getServer());
		for (Settlement camp : all.byTown.values()) {
			if (camp.camp && camp.inhabited() && pos.distSqr(camp.center()) <= (camp.radius + 4.0) * (camp.radius + 4.0)) {
				return camp;
			}
		}
		TownSite town = TownLookup.nearest(level, pos, 200.0).orElse(null);
		if (town == null) {
			return null;
		}
		Settlement settlement = all.of(town.key());
		if (settlement == null || !settlement.inhabited()) {
			return null;
		}
		double reach = settlement.radius + 8.0;
		double dx = pos.getX() - settlement.x;
		double dz = pos.getZ() - settlement.z;
		return dx * dx + dz * dz <= reach * reach ? settlement : null;
	}

	/** A survivor opened a container: if it is in a settlement and a resident can see them, that is theft. */
	public static void containerOpened(ServerPlayer player, BlockPos pos) {
		if (player.isCreative() || player.isSpectator()) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Long last = LAST_THEFT.get(player.getUUID());
		if (last != null && now - last < THEFT_COOLDOWN && now >= last) {
			return;
		}
		Settlement settlement = at(level, pos);
		if (settlement == null) {
			return;
		}
		for (NpcEntity witness : level.getEntitiesOfClass(NpcEntity.class, new AABB(pos).inflate(12.0),
			npc -> npc.isAlive() && npc.faction() == settlement.faction() && npc.hasLineOfSight(player))) {
			LAST_THEFT.put(player.getUUID(), now);
			Reputation.change(player, settlement.faction(), Reputation.THEFT);
			player.displayClientMessage(Component.translatable("message.deadlands.theft.seen", witness.getName()), false);
			witness.voice(com.deadlands.registry.DLSounds.NPC_ALERT.value());
			return;
		}
	}
}
