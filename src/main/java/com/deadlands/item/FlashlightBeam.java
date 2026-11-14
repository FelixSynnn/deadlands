package com.deadlands.item;

import com.deadlands.config.ConfigManager;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The flashlight really lights the world, with or without Sodium or a shader pack ({@code horror.flashlightLightsBlocks}).
 *
 * <p>The cone drawn by DEADLANDS' shaders is lost when Sodium (or a shader pack) replaces them, and the
 * on-screen beam can't brighten a dark block. So, four times a second, the server follows each shining
 * flashlight's beam: where it lands (up to {@link #REACH} blocks) an invisible light block goes in the air
 * just in front of the surface, brighter the closer it is, and for a long throw a dimmer one halfway along.
 * They move with the beam and are taken away the moment it turns off, runs flat, is put away, or its holder
 * leaves or dies. Lights only go into empty air, and every one placed is remembered in the world's saved data
 * ({@code deadlands_flashlight_lights}), so any left by a crash are removed when the world next starts.
 */
public final class FlashlightBeam {
	private static final double REACH = 24.0;
	private static final int EVERY = 5;

	/** The lights placed, saved so a crash can't leave any behind. */
	public static final class State extends SavedData {
		public static final Codec<State> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			BlockPos.CODEC.listOf().optionalFieldOf("lights", List.of()).forGetter(s -> List.copyOf(s.lights))
		).apply(instance, lights -> {
			State state = new State();
			state.lights.addAll(lights);
			return state;
		}));
		public static final SavedDataType<State> TYPE = new SavedDataType<>("deadlands_flashlight_lights", State::new, CODEC, null);

		final Set<BlockPos> lights = new HashSet<>();
	}

	/** Each survivor's lights right now. */
	private static final Map<UUID, List<BlockPos>> PLACED = new HashMap<>();

	private FlashlightBeam() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % EVERY == 2) {
				tick(server);
			}
		});
		ServerLifecycleEvents.SERVER_STARTED.register(FlashlightBeam::clearAll); // anything a crash left
		ServerLifecycleEvents.SERVER_STOPPING.register(FlashlightBeam::clearAll);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> clear(server.overworld(), handler.getPlayer().getUUID()));
	}

	private static State state(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(State.TYPE);
	}

	private static void tick(MinecraftServer server) {
		boolean enabled = ConfigManager.get().horror.flashlightLightsBlocks;
		ServerLevel level = server.overworld(); // the lights live in the overworld only
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			float strength = enabled && player.isAlive() && !player.isSpectator() && player.level() == level ? strength(player) : 0.0F;
			List<BlockPos> want = strength > 0.0F ? beam(level, player, strength) : List.of();
			List<BlockPos> had = PLACED.getOrDefault(player.getUUID(), List.of());
			if (want.equals(had)) {
				continue;
			}
			// Light the new spots first, then put out the old ones, so the beam never blinks.
			List<BlockPos> placed = new ArrayList<>();
			for (BlockPos pos : want) {
				if (had.contains(pos) || place(level, pos, levelFor(player, pos, strength))) {
					placed.add(pos);
				}
			}
			for (BlockPos pos : had) {
				if (!placed.contains(pos)) {
					remove(level, pos);
				}
			}
			if (placed.isEmpty()) {
				PLACED.remove(player.getUUID());
			} else {
				PLACED.put(player.getUUID(), placed);
			}
		}
	}

	/** 0 when no flashlight is shining in either hand; dimmer as the battery runs down. */
	private static float strength(ServerPlayer player) {
		ItemStack stack = FlashlightItem.shining(player.getMainHandItem()) ? player.getMainHandItem()
			: FlashlightItem.shining(player.getOffhandItem()) ? player.getOffhandItem() : ItemStack.EMPTY;
		if (stack.isEmpty()) {
			return 0.0F;
		}
		float charge = FlashlightItem.charge(stack) / (float) FlashlightItem.MAX_CHARGE;
		return charge >= 0.1F ? 1.0F : 0.5F + charge * 5.0F;
	}

	/** Where the lights go: just in front of whatever the beam hits, and halfway along a long throw. */
	private static List<BlockPos> beam(ServerLevel level, ServerPlayer player, float strength) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getViewVector(1.0F).scale(REACH));
		BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
		Vec3 at = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
		List<BlockPos> out = new ArrayList<>(2);
		BlockPos front = hit.getType() == HitResult.Type.MISS ? BlockPos.containing(at) : hit.getBlockPos().relative(hit.getDirection());
		if (eye.distanceTo(at) > 2.0) {
			out.add(front.immutable());
		}
		if (eye.distanceTo(at) > 10.0) {
			BlockPos middle = BlockPos.containing(eye.lerp(at, 0.5));
			if (!middle.equals(front)) {
				out.add(middle);
			}
		}
		return out;
	}

	/** Brighter close up, dimmer far off, and dimmer still on a failing battery. */
	private static int levelFor(ServerPlayer player, BlockPos pos, float strength) {
		double distance = Math.sqrt(pos.distToCenterSqr(player.getEyePosition()));
		return Mth.clamp(Math.round((float) (14.0 - distance * 0.25) * strength), 4, 14);
	}

	private static boolean place(ServerLevel level, BlockPos pos, int light) {
		if (!level.isLoaded(pos) || !level.getBlockState(pos).isAir()) {
			return false;
		}
		BlockState state = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, light);
		if (!level.setBlock(pos, state, 3)) {
			return false;
		}
		State saved = state(level.getServer());
		saved.lights.add(pos.immutable());
		saved.setDirty();
		return true;
	}

	private static void remove(ServerLevel level, BlockPos pos) {
		if (level.isLoaded(pos) && level.getBlockState(pos).is(Blocks.LIGHT)) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
		}
		State saved = state(level.getServer());
		if (saved.lights.remove(pos)) {
			saved.setDirty();
		}
	}

	private static void clear(ServerLevel level, UUID player) {
		List<BlockPos> had = PLACED.remove(player);
		if (had != null) {
			for (BlockPos pos : had) {
				remove(level, pos);
			}
		}
	}

	private static void clearAll(MinecraftServer server) {
		ServerLevel level = server.overworld();
		PLACED.clear();
		State saved = state(server);
		for (BlockPos pos : List.copyOf(saved.lights)) {
			if (level.getBlockState(pos).is(Blocks.LIGHT)) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
			}
		}
		saved.lights.clear();
		saved.setDirty();
	}
}
