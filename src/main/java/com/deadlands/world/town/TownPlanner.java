package com.deadlands.world.town;

import com.deadlands.world.Hashing;
import com.deadlands.world.WorldgenView;
import com.deadlands.world.road.RoadEdge;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * Lays out a town: streets, gates and buildings.
 *
 * <p>Main streets run from the centre to the gates, straight along the compass axes, so a road
 * from another town arrives exactly at the end of one (see {@code RoadEdge}). Settlements build
 * only the arms they need; towns build all four; large towns add two parallel side streets and
 * cities a grid of them, with traffic lights and zebra crossings at the intersections. Plots
 * line both sides of every street; each gets a building template that fits, chosen by how close
 * it is to the centre (shops and civic buildings in the middle, houses further out, farms and
 * workshops on the edge), turned to face its street. Plots on steep, flooded or cut-off ground stay
 * empty.
 */
final class TownPlanner {
	private static final int PLOT_FRONT = 15;
	private static final int PLOT_DEPTH = 15;
	private static final int PLOT_GAP = 2;
	/** From the street centre line to the first block of a plot (half street + verge). */
	private static final int PLOT_OFFSET = TownStreetPiece.HALF_WIDTH + 2;
	private static final int SIDE_STREET_OFFSET = 36;
	private static final int[] TOWN_LINES = {-SIDE_STREET_OFFSET, SIDE_STREET_OFFSET};
	private static final int[] CITY_LINES = {-2 * SIDE_STREET_OFFSET, -SIDE_STREET_OFFSET, SIDE_STREET_OFFSET, 2 * SIDE_STREET_OFFSET};

	/** A crossing of two streets, where a {@link TownCrossingPiece} goes. */
	private record Crossing(int x, int y, int z) {
	}

	private record Street(Direction.Axis axis, int fixed, int from, int to, int[] heights) {
		int height(int at) {
			return heights[Math.max(0, Math.min(heights.length - 1, at - from))];
		}

		Rect rect() {
			int w = TownStreetPiece.HALF_WIDTH;
			return axis == Direction.Axis.X ? new Rect(from, fixed - w, to, fixed + w) : new Rect(fixed - w, from, fixed + w, to);
		}
	}

	private record Rect(int minX, int minZ, int maxX, int maxZ) {
		boolean overlaps(Rect o, int margin) {
			return minX - margin <= o.maxX && maxX + margin >= o.minX && minZ - margin <= o.maxZ && maxZ + margin >= o.minZ;
		}
	}

	/** A lot along a street: {@code t} is where its frontage starts, {@code side} which side of the street it is on. */
	private record Plot(Street street, int t, int side, Rect rect, double distance) {
	}

	private TownPlanner() {
	}

	static void plan(StructurePiecesBuilder builder, StructureTemplateManager templates, WorldgenView view, TownSite town, List<RoadEdge> roads,
		Map<String, List<Identifier>> buildings) {
		long seed = Hashing.of(view.seed(), town.chunkX(), town.chunkZ(), 0x7041L);
		RandomSource random = RandomSource.create(seed);
		Set<Direction> gates = EnumSet.noneOf(Direction.class);
		for (RoadEdge road : roads) {
			gates.add(road.sideAt(town));
		}

		// Main streets: arms from the centre to the gates.
		Map<Direction, Street> arms = new LinkedHashMap<>();
		for (Direction side : armsFor(town, gates, seed)) {
			arms.put(side, arm(view, town, side));
		}
		List<Street> streets = new ArrayList<>(arms.values());
		List<Crossing> crossings = new ArrayList<>();
		if (arms.size() == 4 && town.size() != TownSize.SETTLEMENT) {
			crossings.add(new Crossing(town.centerX(), town.centerY(), town.centerZ()));
		}
		if (town.size().sideStreets() && arms.containsKey(Direction.NORTH) && arms.containsKey(Direction.SOUTH)) {
			boolean city = town.size() == TownSize.CITY;
			int reach = town.size().radius() - (city ? 8 : 12);
			int[] lines = city ? CITY_LINES : TOWN_LINES;
			// East-west side streets, each split where it crosses the north-south main street.
			Map<Integer, Street[]> xStreets = new LinkedHashMap<>();
			for (int offset : lines) {
				int z = town.centerZ() + offset;
				int crossing = (offset < 0 ? arms.get(Direction.NORTH) : arms.get(Direction.SOUTH)).height(z);
				int west = TownSites.gateHeight(view, town.centerX() - reach, z);
				int east = TownSites.gateHeight(view, town.centerX() + reach, z);
				Street w = street(view, Direction.Axis.X, z, town.centerX() - reach, town.centerX(), west, crossing);
				Street e = street(view, Direction.Axis.X, z, town.centerX(), town.centerX() + reach, crossing, east);
				streets.add(w);
				streets.add(e);
				xStreets.put(offset, new Street[] {w, e});
				crossings.add(new Crossing(town.centerX(), crossing, z));
			}
			// Cities: north-south side streets too, in segments pinned to every street they cross,
			// so all crossings are level.
			if (city && arms.containsKey(Direction.EAST) && arms.containsKey(Direction.WEST)) {
				for (int offset : lines) {
					int x = town.centerX() + offset;
					Street eastWest = offset < 0 ? arms.get(Direction.WEST) : arms.get(Direction.EAST);
					List<int[]> stops = new ArrayList<>(); // {z, height}
					stops.add(new int[] {town.centerZ() - reach, TownSites.gateHeight(view, x, town.centerZ() - reach)});
					for (int line : lines) {
						if (line < 0) {
							Street[] pair = xStreets.get(line);
							stops.add(new int[] {town.centerZ() + line, (offset < 0 ? pair[0] : pair[1]).height(x)});
						}
					}
					stops.add(new int[] {town.centerZ(), eastWest.height(x)});
					for (int line : lines) {
						if (line > 0) {
							Street[] pair = xStreets.get(line);
							stops.add(new int[] {town.centerZ() + line, (offset < 0 ? pair[0] : pair[1]).height(x)});
						}
					}
					stops.add(new int[] {town.centerZ() + reach, TownSites.gateHeight(view, x, town.centerZ() + reach)});
					for (int i = 0; i + 1 < stops.size(); i++) {
						int[] a = stops.get(i);
						int[] b = stops.get(i + 1);
						if (b[0] - a[0] >= 4) {
							streets.add(street(view, Direction.Axis.Z, x, a[0], b[0], a[1], b[1]));
						}
						if (i > 0) {
							crossings.add(new Crossing(x, a[1], a[0]));
						}
					}
				}
			}
		}
		// Each street levels its verges, except where they would cut into another street.
		int[] keepOut = new int[streets.size() * 4];
		for (int i = 0; i < streets.size(); i++) {
			Rect r = streets.get(i).rect();
			keepOut[i * 4] = r.minX();
			keepOut[i * 4 + 1] = r.minZ();
			keepOut[i * 4 + 2] = r.maxX();
			keepOut[i * 4 + 3] = r.maxZ();
		}
		for (Street s : streets) {
			int startX = s.axis() == Direction.Axis.X ? s.from() : s.fixed();
			int startZ = s.axis() == Direction.Axis.X ? s.fixed() : s.from();
			builder.addPiece(new TownStreetPiece(startX, startZ, s.axis(), s.heights(), keepOut, town.condition(), seed));
		}

		for (Crossing c : crossings) {
			builder.addPiece(new TownCrossingPiece(c.x(), c.y(), c.z(), town.condition(), seed));
		}

		// Gates, where the condition demands them.
		if (town.condition().gate() != TownCondition.Gate.NONE) {
			for (Map.Entry<Direction, Street> arm : arms.entrySet()) {
				Direction side = arm.getKey();
				int gx = town.gateX(side) - side.getStepX();
				int gz = town.gateZ(side) - side.getStepZ();
				int gy = arm.getValue().height(side.getAxis() == Direction.Axis.X ? gx : gz);
				builder.addPiece(new TownGatePiece(gx, gy, gz, side, town.condition(), seed));
			}
		}

		// Buildings on plots along every street, the centre first, so the building cap trims the
		// outskirts rather than whole streets.
		List<Plot> plots = new ArrayList<>();
		for (Street s : streets) {
			for (int t = s.from() + 4; t + PLOT_FRONT - 1 <= s.to() - 2; t += PLOT_FRONT + PLOT_GAP) {
				for (int side : new int[] {-1, 1}) {
					Rect rect = plotRect(s, t, side);
					if (!outsideTown(town, rect)) {
						double distance = Math.hypot((rect.minX() + rect.maxX()) / 2.0 - town.centerX(), (rect.minZ() + rect.maxZ()) / 2.0 - town.centerZ());
						plots.add(new Plot(s, t, side, rect, distance));
					}
				}
			}
		}
		plots.sort(Comparator.comparingDouble(Plot::distance));
		List<Rect> taken = new ArrayList<>();
		for (Street s : streets) {
			taken.add(s.rect());
		}
		int built = 0;
		int cap = town.size().maxBuildings();
		boolean western = view.biome(town.centerX(), town.centerY(), town.centerZ()).is(com.deadlands.world.biome.DLBiomes.DRY_WASTELAND);
		double vacancy = town.condition() == TownCondition.OVERRUN ? 0.15 : town.condition() == TownCondition.ABANDONED ? 0.1 : 0.05;
		for (Plot plot : plots) {
			if (built >= cap) {
				return;
			}
			if (overlapsAny(plot.rect(), taken)) {
				continue;
			}
			if (random.nextDouble() < vacancy) {
				taken.add(plot.rect()); // an empty lot
				continue;
			}
			if (placeBuilding(builder, templates, view, town, buildings, random, plot.street(), plot.t(), plot.side(), plot.rect(), plot.distance(), seed,
				western)) {
				taken.add(plot.rect());
				built++;
			}
		}
	}

	private static Set<Direction> armsFor(TownSite town, Set<Direction> gates, long seed) {
		if (town.size() != TownSize.SETTLEMENT) {
			return EnumSet.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);
		}
		long eastWest = gates.stream().filter(d -> d.getAxis() == Direction.Axis.X).count();
		long northSouth = gates.size() - eastWest;
		boolean alongX = eastWest > northSouth || eastWest == northSouth && (seed & 1L) == 0L;
		Set<Direction> arms = alongX ? EnumSet.of(Direction.EAST, Direction.WEST) : EnumSet.of(Direction.NORTH, Direction.SOUTH);
		arms.addAll(gates);
		return arms;
	}

	private static Street arm(WorldgenView view, TownSite town, Direction side) {
		int gate = town.size().gateRadius();
		int gateHeight = TownSites.gateHeight(view, town.gateX(side), town.gateZ(side));
		if (side.getAxis() == Direction.Axis.X) {
			int x0 = side == Direction.EAST ? town.centerX() : town.centerX() - gate;
			int x1 = side == Direction.EAST ? town.centerX() + gate : town.centerX();
			return street(view, Direction.Axis.X, town.centerZ(), x0, x1,
				side == Direction.EAST ? town.centerY() : gateHeight, side == Direction.EAST ? gateHeight : town.centerY());
		}
		int z0 = side == Direction.SOUTH ? town.centerZ() : town.centerZ() - gate;
		int z1 = side == Direction.SOUTH ? town.centerZ() + gate : town.centerZ();
		return street(view, Direction.Axis.Z, town.centerX(), z0, z1,
			side == Direction.SOUTH ? town.centerY() : gateHeight, side == Direction.SOUTH ? gateHeight : town.centerY());
	}

	/** A street with a smoothed terrain-following profile pinned to the given end heights. */
	private static Street street(WorldgenView view, Direction.Axis axis, int fixed, int from, int to, int startHeight, int endHeight) {
		int n = to - from + 1;
		int step = 4;
		int samples = (n - 1) / step + 2;
		float[] h = new float[samples];
		int floor = view.seaLevel();
		for (int k = 0; k < samples; k++) {
			int at = Math.min(from + k * step, to);
			h[k] = Math.max(axis == Direction.Axis.X ? view.surface(at, fixed) : view.surface(fixed, at), floor);
		}
		h[0] = startHeight;
		h[samples - 1] = endHeight;
		float[] tmp = new float[samples];
		for (int pass = 0; pass < 3; pass++) {
			tmp[0] = h[0];
			tmp[samples - 1] = h[samples - 1];
			for (int k = 1; k < samples - 1; k++) {
				float sum = 0;
				int c = 0;
				for (int d = -2; d <= 2; d++) {
					int q = k + d;
					if (q >= 0 && q < samples) {
						sum += h[q];
						c++;
					}
				}
				tmp[k] = sum / c;
			}
			System.arraycopy(tmp, 0, h, 0, samples);
		}
		for (int pass = 0; pass < 2; pass++) {
			for (int k = 1; k < samples - 1; k++) {
				h[k] = Math.max(h[k - 1] - 1, Math.min(h[k - 1] + 1, h[k]));
			}
			for (int k = samples - 2; k > 0; k--) {
				h[k] = Math.max(h[k + 1] - 1, Math.min(h[k + 1] + 1, h[k]));
			}
		}
		// Level the first and last few blocks, so crossings and gates are flat.
		if (samples >= 4) {
			h[1] = h[0];
			h[samples - 2] = h[samples - 1];
		}
		int[] heights = new int[n];
		for (int i = 0; i < n; i++) {
			int k0 = Math.min(i / step, samples - 2);
			int p0 = Math.min(k0 * step, n - 1);
			int p1 = Math.min((k0 + 1) * step, n - 1);
			float f = p1 > p0 ? Math.min(1.0F, (float) (i - p0) / (p1 - p0)) : 1.0F;
			heights[i] = Math.round(h[k0] + (h[k0 + 1] - h[k0]) * f);
		}
		return new Street(axis, fixed, from, to, heights);
	}

	private static Rect plotRect(Street s, int t, int side) {
		int near = s.fixed() + side * PLOT_OFFSET;
		int far = s.fixed() + side * (PLOT_OFFSET + PLOT_DEPTH - 1);
		int lo = Math.min(near, far);
		int hi = Math.max(near, far);
		return s.axis() == Direction.Axis.X ? new Rect(t, lo, t + PLOT_FRONT - 1, hi) : new Rect(lo, t, hi, t + PLOT_FRONT - 1);
	}

	private static boolean outsideTown(TownSite town, Rect plot) {
		int limit = town.size().radius() + PLOT_OFFSET + PLOT_DEPTH;
		return Math.max(Math.abs(plot.minX() - town.centerX()), Math.abs(plot.maxX() - town.centerX())) > limit
			|| Math.max(Math.abs(plot.minZ() - town.centerZ()), Math.abs(plot.maxZ() - town.centerZ())) > limit;
	}

	private static boolean overlapsAny(Rect plot, List<Rect> taken) {
		for (Rect r : taken) {
			if (plot.overlaps(r, 1)) {
				return true;
			}
		}
		return false;
	}

	private static boolean placeBuilding(StructurePiecesBuilder builder, StructureTemplateManager templates, WorldgenView view, TownSite town,
		Map<String, List<Identifier>> buildings, RandomSource random, Street s, int t, int side, Rect plot, double distance, long seed,
		boolean western) {
		String category = pickCategory(distance / town.size().radius(), town.size(), western, buildings, random);
		Identifier template = pickTemplate(templates, buildings, category, random);
		if (template == null) {
			return false;
		}
		Vec3i size = templates.getOrCreate(template).getSize();
		int sx = size.getX();
		int sz = size.getZ();
		int frontMid = t + PLOT_FRONT / 2;
		int floor = s.height(frontMid);
		int setback = 1;
		// The template's front (its z = 0 edge, facing north) turns to face the street.
		Rotation rotation;
		int fx;
		int fz;
		int along = t + (PLOT_FRONT - sx) / 2;
		if (s.axis() == Direction.Axis.X) {
			fx = along;
			if (side > 0) {
				rotation = Rotation.NONE;
				fz = s.fixed() + PLOT_OFFSET + setback;
			} else {
				rotation = Rotation.CLOCKWISE_180;
				fz = s.fixed() - PLOT_OFFSET - setback - sz + 1;
			}
		} else {
			fz = along;
			if (side > 0) {
				rotation = Rotation.COUNTERCLOCKWISE_90;
				fx = s.fixed() + PLOT_OFFSET + setback;
			} else {
				rotation = Rotation.CLOCKWISE_90;
				fx = s.fixed() - PLOT_OFFSET - setback - sz + 1;
			}
		}
		int footX = s.axis() == Direction.Axis.X ? sx : sz;
		int footZ = s.axis() == Direction.Axis.X ? sz : sx;
		if (!buildable(view, fx, fz, fx + footX - 1, fz + footZ - 1, floor)) {
			return false;
		}
		BlockPos origin = switch (rotation) {
			case NONE -> new BlockPos(fx, floor, fz);
			case CLOCKWISE_90 -> new BlockPos(fx + sz - 1, floor, fz);
			case CLOCKWISE_180 -> new BlockPos(fx + sx - 1, floor, fz + sz - 1);
			case COUNTERCLOCKWISE_90 -> new BlockPos(fx, floor, fz + sx - 1);
		};
		builder.addPiece(new TownBuildingPiece(templates, template, origin, rotation, town.condition(), Hashing.mix(seed ^ origin.asLong())));
		return true;
	}

	/** Dry, not too steep, and not buried deep in a hill or perched far above the ground. */
	private static boolean buildable(WorldgenView view, int x0, int z0, int x1, int z1, int floor) {
		int[] xs = {x0, x1, x0, x1, (x0 + x1) / 2};
		int[] zs = {z0, z0, z1, z1, (z0 + z1) / 2};
		int lo = Integer.MAX_VALUE;
		int hi = Integer.MIN_VALUE;
		for (int i = 0; i < xs.length; i++) {
			int y = view.surface(xs[i], zs[i]);
			if (y < view.seaLevel()) {
				return false;
			}
			lo = Math.min(lo, y);
			hi = Math.max(hi, y);
		}
		return hi - lo <= 9 && hi - floor <= 10 && floor - lo <= 8;
	}

	private static String pickCategory(double rel, TownSize size, boolean western, Map<String, List<Identifier>> buildings, RandomSource random) {
		Map<String, Integer> weights = new LinkedHashMap<>();
		if (size == TownSize.CITY) {
			if (rel < 0.45) {
				weights.put("city", 45);
				weights.put("commercial", 25);
				weights.put("civic", 15);
				weights.put("medical", 15);
			} else if (rel < 0.8) {
				weights.put("city", 30);
				weights.put("residential", 35);
				weights.put("commercial", 20);
				weights.put("civic", 8);
				weights.put("medical", 7);
			} else {
				weights.put("residential", 45);
				weights.put("industrial", 30);
				weights.put("commercial", 15);
				weights.put("city", 10);
			}
		} else if (rel < 0.4) {
			weights.put("commercial", 40);
			weights.put("civic", 25);
			weights.put("medical", 15);
			weights.put("residential", 20);
		} else if (rel < 0.75) {
			weights.put("residential", 55);
			weights.put("commercial", 25);
			weights.put("civic", 8);
			weights.put("medical", 7);
			weights.put("industrial", 5);
		} else {
			weights.put("residential", 55);
			weights.put("rural", 20);
			weights.put("industrial", 15);
			weights.put("commercial", 10);
		}
		if (size == TownSize.LARGE_TOWN && rel < 0.75) {
			weights.put("city", 6);
		}
		if (western) {
			// Desert towns: false-front shops and wooden houses replace much of the rest.
			int moved = weights.getOrDefault("residential", 0) / 2 + weights.getOrDefault("commercial", 0) * 2 / 5;
			weights.computeIfPresent("residential", (k, v) -> v - v / 2);
			weights.computeIfPresent("commercial", (k, v) -> v - v * 2 / 5);
			weights.put("western", moved);
		}
		weights.keySet().removeIf(category -> buildings.getOrDefault(category, List.of()).isEmpty());
		int total = weights.values().stream().mapToInt(Integer::intValue).sum();
		if (total <= 0) {
			return "residential";
		}
		int roll = random.nextInt(total);
		for (Map.Entry<String, Integer> entry : weights.entrySet()) {
			roll -= entry.getValue();
			if (roll < 0) {
				return entry.getKey();
			}
		}
		return "residential";
	}

	private static Identifier pickTemplate(StructureTemplateManager templates, Map<String, List<Identifier>> buildings, String category, RandomSource random) {
		for (String c : new String[] {category, "residential"}) {
			List<Identifier> pool = new ArrayList<>(buildings.getOrDefault(c, List.of()));
			for (int i = pool.size() - 1; i > 0; i--) {
				int j = random.nextInt(i + 1);
				Identifier tmp = pool.get(i);
				pool.set(i, pool.get(j));
				pool.set(j, tmp);
			}
			for (Identifier id : pool) {
				Vec3i size = templates.getOrCreate(id).getSize();
				if (size.getX() > 0 && size.getX() <= PLOT_FRONT - 1 && size.getZ() <= PLOT_DEPTH - 1) {
					return id;
				}
			}
		}
		return null;
	}
}
