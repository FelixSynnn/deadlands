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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The flashlight really lights the world, with or without Sodium or a shader pack ({@code horror.flashlightLightsBlocks}).
 *
 * <p>The cone drawn by DEADLANDS' shaders is lost when Sodium (or a shader pack) replaces them, and the on-screen
 * beam can't brighten a dark block, so the server lights the beam with invisible light blocks. To keep it steady:
 *
 * <ul>
 *   <li><b>Where it lands:</b> the beam is followed ten times a second. The light goes in the nearest empty
 *       space to the spot it hits (searching a few blocks around it), so plants, grass, rubble and decals on the
 *       ground don't stop it; in water it goes in as a waterlogged light.</li>
 *   <li><b>It doesn't jump about:</b> a light stays put while the beam's spot is within {@link #STICK} blocks of
 *       it, so small movements change nothing.</li>
 *   <li><b>No dark gaps:</b> when it does move, the new light goes in first and the old one stays a moment
 *       ({@link #OVERLAP} ticks) while the lighting catches up.</li>
 *   <li><b>Never pitch black around you:</b> a soft light (level 8) goes with the holder too, and halfway along a
 *       long throw a second beam light fills the gap.</li>
 * </ul>
 * Lights only replace empty air (or water). Every one placed is remembered in the world's saved data
 * ({@code deadlands_flashlight_lights}), so any left behind by a crash are removed when the world next starts.
 */
public final class FlashlightBeam {
	private static final double REACH = 36.0;
	private static final int EVERY = 2;
	/** A light is kept while its target is this close to it (blocks). */
	private static final double STICK = 2.5;
	/** Ticks an old light stays after its replacement went in. */
	private static final int OVERLAP = 8;
	private static final int HOLDER_LIGHT = 10;

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

	/** One light in the world: its slot (0 holder, 1 beam end, 2 halfway), where, and when it is to go out (-1: in use). */
	private static final class Light {
		final int slot;
		final BlockPos pos;
		long expires = -1L;

		Light(int slot, BlockPos pos) {
			this.slot = slot;
			this.pos = pos;
		}
	}

	private static final Map<UUID, List<Light>> PLACED = new HashMap<>();

	private FlashlightBeam() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % EVERY == 0) {
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
		long now = level.getGameTime();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			float strength = enabled && player.isAlive() && !player.isSpectator() && player.level() == level ? strength(player) : 0.0F;
			List<Light> lights = PLACED.computeIfAbsent(player.getUUID(), k -> new ArrayList<>());
			BlockPos[] targets = strength > 0.0F ? targets(level, player) : new BlockPos[3];
			for (int slot = 0; slot < targets.length; slot++) {
				update(level, player, lights, slot, targets[slot], strength, now);
			}
			// Old lights whose replacement has had time to light up go out.
			lights.removeIf(light -> {
				if (light.expires >= 0 && now >= light.expires) {
					remove(level, light.pos);
					return true;
				}
				return false;
			});
			if (lights.isEmpty()) {
				PLACED.remove(player.getUUID());
			}
		}
	}

	/** Keep, move or put out the light in one slot. */
	private static void update(ServerLevel level, ServerPlayer player, List<Light> lights, int slot, @Nullable BlockPos target, float strength, long now) {
		Light current = null;
		for (Light light : lights) {
			if (light.slot == slot && light.expires < 0) {
				current = light;
			}
		}
		if (target == null) {
			if (current != null) {
				current.expires = now; // off: out at once
			}
			return;
		}
		double stick = slot == 0 ? 1.5 : STICK;
		if (current != null && current.pos.distSqr(target) <= stick * stick && level.getBlockState(current.pos).is(Blocks.LIGHT)) {
			return; // close enough: it stays put
		}
		BlockPos spot = freeNear(level, target, current == null ? null : current.pos);
		if (spot == null) {
			return; // nowhere free this time: keep what there is
		}
		int brightness = slot == 0 ? HOLDER_LIGHT : levelFor(player, spot, strength, slot);
		if (!place(level, spot, brightness)) {
			return;
		}
		if (current != null) {
			current.expires = now + OVERLAP; // the old one stays a moment while the lighting catches up
		}
		lights.add(new Light(slot, spot));
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

	/** The holder's own spot, where the beam lands, and halfway along a long throw (null where not needed). */
	private static BlockPos[] targets(ServerLevel level, ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getViewVector(1.0F).scale(REACH));
		BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		Vec3 at = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
		// A little back from the surface, toward the holder: the face it hits gets the light.
		Vec3 back = at.subtract(player.getViewVector(1.0F).scale(0.6));
		BlockPos[] out = new BlockPos[3];
		out[0] = BlockPos.containing(eye);
		double distance = eye.distanceTo(at);
		if (distance > 2.5) {
			out[1] = BlockPos.containing(back);
		}
		if (distance > 9.0) {
			out[2] = BlockPos.containing(eye.lerp(at, 0.5));
		}
		return out;
	}

	/** The free space (air, or water for a waterlogged light) nearest {@code target}, within two blocks; or the light already there. */
	private static @Nullable BlockPos freeNear(ServerLevel level, BlockPos target, @Nullable BlockPos keep) {
		BlockPos best = null;
		int bestScore = Integer.MAX_VALUE;
		BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
		for (int dx = -2; dx <= 2; dx++) {
			for (int dy = -1; dy <= 2; dy++) {
				for (int dz = -2; dz <= 2; dz++) {
					probe.setWithOffset(target, dx, dy, dz);
					if (!level.isLoaded(probe) || !free(level, probe) && !probe.equals(keep)) {
						continue;
					}
					int score = dx * dx + dy * dy + dz * dz + (dy < 0 ? 1 : 0);
					if (score < bestScore) {
						bestScore = score;
						best = probe.immutable();
					}
				}
			}
		}
		return best;
	}

	private static boolean free(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.isAir() || state.is(Blocks.WATER) && state.getFluidState().isSource();
	}

	/** Bright close up, dimmer far off, dimmer still on a failing battery; the halfway light is softer. */
	private static int levelFor(ServerPlayer player, BlockPos pos, float strength, int slot) {
		double distance = Math.sqrt(pos.distToCenterSqr(player.getEyePosition()));
		float base = (float) (15.0 - distance * 0.14) - (slot == 2 ? 3.0F : 0.0F);
		return Mth.clamp(Math.round(base * strength), 5, 15);
	}

	private static boolean place(ServerLevel level, BlockPos pos, int light) {
		BlockState here = level.getBlockState(pos);
		if (here.is(Blocks.LIGHT)) {
			return true; // already lit (a light we placed)
		}
		if (!free(level, pos)) {
			return false;
		}
		BlockState state = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, light)
			.setValue(BlockStateProperties.WATERLOGGED, here.is(Blocks.WATER));
		if (!level.setBlock(pos, state, 3)) {
			return false;
		}
		State saved = state(level.getServer());
		saved.lights.add(pos.immutable());
		saved.setDirty();
		return true;
	}

	private static void remove(ServerLevel level, BlockPos pos) {
		// Another of this survivor's (or anyone's) lights may share the block: only clear it if none still uses it.
		for (List<Light> lights : PLACED.values()) {
			for (Light light : lights) {
				if (light.expires < 0 && light.pos.equals(pos)) {
					return;
				}
			}
		}
		BlockState state = level.isLoaded(pos) ? level.getBlockState(pos) : null;
		if (state != null && state.is(Blocks.LIGHT)) {
			boolean wet = state.getValue(BlockStateProperties.WATERLOGGED);
			level.setBlock(pos, wet ? Fluids.WATER.defaultFluidState().createLegacyBlock() : Blocks.AIR.defaultBlockState(), 3);
		}
		State saved = state(level.getServer());
		if (saved.lights.remove(pos)) {
			saved.setDirty();
		}
	}

	private static void clear(ServerLevel level, UUID player) {
		List<Light> had = PLACED.remove(player);
		if (had != null) {
			for (Light light : had) {
				remove(level, light.pos);
			}
		}
	}

	private static void clearAll(MinecraftServer server) {
		ServerLevel level = server.overworld();
		PLACED.clear();
		State saved = state(server);
		for (BlockPos pos : List.copyOf(saved.lights)) {
			BlockState state = level.getBlockState(pos);
			if (state.is(Blocks.LIGHT)) {
				boolean wet = state.getValue(BlockStateProperties.WATERLOGGED);
				level.setBlock(pos, wet ? Fluids.WATER.defaultFluidState().createLegacyBlock() : Blocks.AIR.defaultBlockState(), 3);
			}
		}
		saved.lights.clear();
		saved.setDirty();
	}
}
