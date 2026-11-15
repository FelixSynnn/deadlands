package com.deadlands.horror;

import com.deadlands.block.EmergencyLightBlock;
import com.deadlands.block.FluorescentLightBlock;
import com.deadlands.world.weather.WeatherDirector;
import java.util.ArrayList;
import java.util.AbstractMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Phase 4: place conditions for the jumpscares ({@code requires} in a scare's data) and their
 * server-side effects on the world (fixtures dying toward the survivor, emergency lights strobing).
 */
final class ScareConditions {
	private ScareConditions() {
	}

	static boolean met(ServerPlayer player, String requirement, boolean outdoors) {
		ServerLevel level = player.level();
		BlockPos pos = player.blockPosition();
		return switch (requirement) {
			case "fixtures" -> litFluorescents(level, pos, 12).size() >= 3;
			case "emergency_light" -> !lit(level, pos, 10, EmergencyLightBlock.class).isEmpty();
			case "heavy_rain" -> outdoors && level.isRaining() && WeatherDirector.state(level.getServer()).kind() == WeatherDirector.Kind.HEAVY_RAIN;
			case "window" -> windowAhead(player);
			case "open_ground" -> openAhead(player);
			default -> false;
		};
	}

	/** The lit fluorescents within {@code radius}, farthest first: they go out one after another toward the survivor. */
	static List<Map.Entry<Integer, Consumer<ServerPlayer>>> fixturesOut(ServerPlayer player, int ticks) {
		List<BlockPos> lights = litFluorescents(player.level(), player.blockPosition(), 12);
		lights.sort(Comparator.comparingDouble((BlockPos p) -> p.distSqr(player.blockPosition())).reversed());
		List<Map.Entry<Integer, Consumer<ServerPlayer>>> out = new ArrayList<>();
		for (int i = 0; i < lights.size(); i++) {
			BlockPos at = lights.get(i);
			int when = lights.size() == 1 ? 0 : i * ticks / (lights.size() - 1);
			out.add(new AbstractMap.SimpleEntry<>(when, p -> {
				BlockState state = p.level().getBlockState(at);
				if (state.getBlock() instanceof FluorescentLightBlock && state.getValue(FluorescentLightBlock.LIT)) {
					p.level().setBlock(at, state.setValue(FluorescentLightBlock.LIT, false), Block.UPDATE_CLIENTS);
				}
			}));
		}
		return out;
	}

	/** Nearby emergency lights pulse every three ticks for {@code ticks} (their own slow pulse resumes after). */
	static List<Map.Entry<Integer, Consumer<ServerPlayer>>> strobe(ServerPlayer player, int ticks) {
		List<BlockPos> lights = lit(player.level(), player.blockPosition(), 10, EmergencyLightBlock.class);
		List<Map.Entry<Integer, Consumer<ServerPlayer>>> out = new ArrayList<>();
		for (int t = 0; t < ticks; t += 3) {
			out.add(new AbstractMap.SimpleEntry<>(t, p -> {
				for (BlockPos at : lights) {
					BlockState state = p.level().getBlockState(at);
					if (state.getBlock() instanceof EmergencyLightBlock) {
						p.level().setBlock(at, state.setValue(EmergencyLightBlock.PULSE, !state.getValue(EmergencyLightBlock.PULSE)), Block.UPDATE_CLIENTS);
					}
				}
			}));
		}
		return out;
	}

	private static List<BlockPos> litFluorescents(ServerLevel level, BlockPos center, int radius) {
		return lit(level, center, radius, FluorescentLightBlock.class);
	}

	private static List<BlockPos> lit(ServerLevel level, BlockPos center, int radius, Class<? extends Block> type) {
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(center.offset(-radius, -3, -radius), center.offset(radius, 5, radius))) {
			BlockState state = level.getBlockState(p);
			if (type.isInstance(state.getBlock()) && state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)
				&& state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)) {
				out.add(p.immutable());
			}
		}
		return out;
	}

	/** Glass within two blocks straight ahead at eye height. */
	private static boolean windowAhead(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		Vec3 flat = new Vec3(look.x, 0.0, look.z);
		if (flat.lengthSqr() < 1.0E-4) {
			return false;
		}
		flat = flat.normalize();
		for (double d = 0.8; d <= 2.2; d += 0.35) {
			BlockState state = player.level().getBlockState(BlockPos.containing(eye.add(flat.scale(d))));
			if (net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().contains("glass")) {
				return true;
			}
		}
		return false;
	}

	/** Nothing in the way for six to eighteen blocks ahead at eye height. */
	private static boolean openAhead(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		Vec3 flat = new Vec3(look.x, 0.0, look.z);
		if (flat.lengthSqr() < 1.0E-4) {
			return false;
		}
		flat = flat.normalize();
		for (double d = 2.0; d <= 18.0; d += 1.0) {
			if (!player.level().getBlockState(BlockPos.containing(eye.add(flat.scale(d)))).isAir()) {
				return false;
			}
		}
		return true;
	}
}
