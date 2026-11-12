package com.deadlands.entity;

import com.deadlands.config.ConfigManager;
import com.deadlands.config.DeadlandsConfig;
import com.deadlands.npc.SafeSpots;
import com.deadlands.registry.DLGuest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Brings the Guest (the {@code guest} config section): at night, in the overworld, never on Peaceful.
 * Every 30 seconds each survivor is a chance ({@code spawnChance}); one who has sheltered in the same
 * spot for {@code campMinutes} is a certainty. At most {@code maxAlive} in the world, never while the
 * Aberrant or another Guest is within 96 blocks of them. It arrives 30 to 50 blocks off, out of sight,
 * and comes straight for them. Also tells nearby Guests when an infected dies (they feed).
 */
public final class GuestSpawner {
	private static final Map<UUID, BlockPos> ANCHOR = new HashMap<>();
	private static final Map<UUID, Integer> CAMPED = new HashMap<>();

	private GuestSpawner() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 600 == 451) {
				tick(server);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof InfectedEntity && entity.level() instanceof ServerLevel level) {
				for (GuestEntity guest : GuestEntity.near(level, entity.position(), 32.0)) {
					guest.bodyNear(entity.position());
				}
			}
		});
	}

	private static void tick(MinecraftServer server) {
		DeadlandsConfig.Guest config = ConfigManager.get().guest;
		ServerLevel level = server.overworld();
		if (!config.enabled || level.getDifficulty() == Difficulty.PEACEFUL) {
			return;
		}
		long time = Math.floorMod(level.getDayTime(), 24000L);
		boolean night = time >= 13000L && time <= 22500L;
		int alive = level.getEntities(DLGuest.GUEST, e -> e.isAlive()).size();
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || player.isCreative()) {
				continue;
			}
			UUID id = player.getUUID();
			BlockPos anchor = ANCHOR.get(id);
			if (anchor == null || anchor.distSqr(player.blockPosition()) > 24 * 24) {
				ANCHOR.put(id, player.blockPosition());
				CAMPED.put(id, 0);
			} else {
				CAMPED.merge(id, 600, Integer::sum);
			}
			if (config.nightOnly && !night || alive >= config.maxAlive) {
				continue;
			}
			if (!level.getEntitiesOfClass(AsesinoEntity.class, player.getBoundingBox().inflate(96.0), a -> a.isAlive()).isEmpty()
				|| !level.getEntitiesOfClass(GuestEntity.class, player.getBoundingBox().inflate(96.0), g -> g.isAlive()).isEmpty()) {
				continue;
			}
			boolean camping = CAMPED.getOrDefault(id, 0) >= config.campMinutes * 1200.0;
			if ((camping || level.getRandom().nextDouble() < config.spawnChance) && spawn(level, player, level.getRandom())) {
				alive++;
				CAMPED.put(id, 0);
			}
		}
	}

	private static boolean spawn(ServerLevel level, ServerPlayer player, RandomSource random) {
		for (int attempt = 0; attempt < 8; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double distance = 30.0 + random.nextDouble() * 20.0;
			BlockPos around = BlockPos.containing(player.getX() + Math.cos(angle) * distance, player.getY(), player.getZ() + Math.sin(angle) * distance);
			if (!level.isLoaded(around)) {
				continue;
			}
			around = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, around);
			BlockPos spot = SafeSpots.find(level, around, 0, 6, random);
			if (spot == null || seen(level, spot) || !roomFor(level, spot)) {
				continue;
			}
			GuestEntity guest = DLGuest.GUEST.create(level, EntitySpawnReason.NATURAL);
			if (guest == null) {
				return false;
			}
			guest.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
			level.addFreshEntity(guest);
			guest.hunt(level, player, true);
			return true;
		}
		return false;
	}

	private static boolean roomFor(ServerLevel level, BlockPos spot) {
		return level.noCollision(new AABB(spot).inflate(0.4, 0.0, 0.4).expandTowards(0.0, 3.3, 0.0));
	}

	private static boolean seen(ServerLevel level, BlockPos spot) {
		Vec3 head = Vec3.atBottomCenterOf(spot).add(0.0, 2.6, 0.0);
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(head) < 64.0 * 64.0 && level.clip(new ClipContext(player.getEyePosition(), head, ClipContext.Block.VISUAL,
				ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS) {
				return true;
			}
		}
		return false;
	}
}
