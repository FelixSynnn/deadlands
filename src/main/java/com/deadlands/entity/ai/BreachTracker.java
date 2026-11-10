package com.deadlands.entity.ai;

import com.deadlands.entity.InfectedEntity;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Damage the infected have done to barricades, shared between them: a crowd bashing the same
 * door breaks it much faster than one infected alone. A barricade left alone for ten seconds
 * loses its damage. The crack overlay uses one id per block, so every survivor sees the same cracks.
 */
public final class BreachTracker {
	private static final int FORGET_AFTER_TICKS = 200;
	private static final Map<Key, Progress> PROGRESS = new HashMap<>();

	private record Key(ResourceKey<Level> dimension, long pos) {
	}

	private static final class Progress {
		float work;
		long lastHit;
		int stage = -1;
	}

	private BreachTracker() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 100 == 0) {
				forgetIdle(server);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> PROGRESS.clear());
	}

	/** Work needed to break a block, in Walker-ticks. */
	static float requiredWork(ServerLevel level, BlockPos pos, BlockState state) {
		return 60.0F + Math.max(0.2F, state.getDestroySpeed(level, pos)) * 60.0F;
	}

	/** One tick of bashing by {@code mob}. Returns true when the block broke. */
	public static boolean hit(ServerLevel level, BlockPos pos, InfectedEntity mob) {
		BlockState state = level.getBlockState(pos);
		Key key = new Key(level.dimension(), pos.asLong());
		Progress progress = PROGRESS.computeIfAbsent(key, unused -> new Progress());
		progress.work += mob.kind().breachPower();
		progress.lastHit = level.getGameTime();
		float required = requiredWork(level, pos, state);
		int stage = Math.min(9, (int) (progress.work / required * 10.0F));
		if (stage != progress.stage) {
			progress.stage = stage;
			level.destroyBlockProgress(crackId(pos), pos, stage);
		}
		if (progress.work >= required) {
			PROGRESS.remove(key);
			level.destroyBlockProgress(crackId(pos), pos, -1);
			level.destroyBlock(pos, true, mob);
			return true;
		}
		return false;
	}

	private static void forgetIdle(MinecraftServer server) {
		Iterator<Map.Entry<Key, Progress>> it = PROGRESS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Key, Progress> entry = it.next();
			ServerLevel level = server.getLevel(entry.getKey().dimension());
			if (level == null) {
				it.remove();
				continue;
			}
			if (level.getGameTime() - entry.getValue().lastHit > FORGET_AFTER_TICKS) {
				BlockPos pos = BlockPos.of(entry.getKey().pos());
				level.destroyBlockProgress(crackId(pos), pos, -1);
				it.remove();
			}
		}
	}

	/** A stable negative id per block, so it never collides with an entity id. */
	private static int crackId(BlockPos pos) {
		return -1 - (Long.hashCode(pos.asLong()) & 0x3FFFFFFF);
	}
}
