package com.deadlands.world.road;

import com.deadlands.world.Hashing;
import com.deadlands.world.WorldgenView;
import com.deadlands.world.town.TownSite;

/**
 * One railway line between two towns, from a station just outside one town to a station outside
 * the other. Stations sit off a corner of each town (diagonally from its centre, past the last
 * buildings), so a line never runs down a main street. The line is a gentle curve resampled to
 * one point per block, with a height profile that climbs and falls much more gently than a road.
 */
public final class RailEdge {
	private static final int SAMPLE_STEP = 8;
	private static final float MAX_RISE = 0.75F;
	private static final int MARGIN = 8;
	/** Distance from a town's gate radius out to its station, along each axis. */
	public static final int STATION_OFFSET = 26;

	private final long key;
	private final TownSite from;
	private final TownSite to;
	private final float[] xs;
	private final float[] zs;
	private final float[] ys;
	private final int minX;
	private final int minZ;
	private final int maxX;
	private final int maxZ;

	private RailEdge(long key, TownSite from, TownSite to, float[] xs, float[] zs, float[] ys) {
		this.key = key;
		this.from = from;
		this.to = to;
		this.xs = xs;
		this.zs = zs;
		this.ys = ys;
		float lx = Float.MAX_VALUE;
		float lz = Float.MAX_VALUE;
		float hx = -Float.MAX_VALUE;
		float hz = -Float.MAX_VALUE;
		for (int i = 0; i < xs.length; i++) {
			lx = Math.min(lx, xs[i]);
			lz = Math.min(lz, zs[i]);
			hx = Math.max(hx, xs[i]);
			hz = Math.max(hz, zs[i]);
		}
		this.minX = (int) Math.floor(lx) - MARGIN;
		this.minZ = (int) Math.floor(lz) - MARGIN;
		this.maxX = (int) Math.ceil(hx) + MARGIN;
		this.maxZ = (int) Math.ceil(hz) + MARGIN;
	}

	/** Where a town's station is, on the corner facing the other town. */
	static double[] station(TownSite town, TownSite other) {
		int sx = other.centerX() >= town.centerX() ? 1 : -1;
		int sz = other.centerZ() >= town.centerZ() ? 1 : -1;
		int d = town.size().gateRadius() + STATION_OFFSET;
		return new double[] {town.centerX() + sx * d + 0.5, town.centerZ() + sz * d + 0.5};
	}

	static RailEdge build(WorldgenView view, long key, TownSite from, TownSite to) {
		double[] a = station(from, to);
		double[] b = station(to, from);
		double length = Math.hypot(b[0] - a[0], b[1] - a[1]);
		// A gentle S: the handles lean sideways by a seeded amount.
		long hash = Hashing.mix(key ^ view.seed() ^ 0x4A11L);
		double bend = (Hashing.unit(hash) - 0.5) * 0.3 * length;
		double ux = (b[0] - a[0]) / Math.max(1e-6, length);
		double uz = (b[1] - a[1]) / Math.max(1e-6, length);
		double p1x = a[0] + ux * length / 3 - uz * bend;
		double p1z = a[1] + uz * length / 3 + ux * bend;
		double p2x = a[0] + ux * length * 2 / 3 + uz * bend;
		double p2z = a[1] + uz * length * 2 / 3 - ux * bend;
		int dense = (int) Math.ceil(length * 3.0) + 16;
		double[] cx = new double[dense + 1];
		double[] cz = new double[dense + 1];
		double[] arc = new double[dense + 1];
		for (int i = 0; i <= dense; i++) {
			double t = (double) i / dense;
			double u = 1.0 - t;
			cx[i] = u * u * u * a[0] + 3 * u * u * t * p1x + 3 * u * t * t * p2x + t * t * t * b[0];
			cz[i] = u * u * u * a[1] + 3 * u * u * t * p1z + 3 * u * t * t * p2z + t * t * t * b[1];
			if (i > 0) {
				arc[i] = arc[i - 1] + Math.hypot(cx[i] - cx[i - 1], cz[i] - cz[i - 1]);
			}
		}
		double total = arc[dense];
		int count = Math.max(2, (int) Math.floor(total) + 1);
		float[] xs = new float[count];
		float[] zs = new float[count];
		int j = 0;
		for (int i = 0; i < count; i++) {
			double s = Math.min(i, total);
			while (j < dense - 1 && arc[j + 1] < s) {
				j++;
			}
			double span = arc[j + 1] - arc[j];
			double f = span > 1e-9 ? (s - arc[j]) / span : 0.0;
			xs[i] = (float) (cx[j] + (cx[j + 1] - cx[j]) * f);
			zs[i] = (float) (cz[j] + (cz[j + 1] - cz[j]) * f);
		}
		return new RailEdge(key, from, to, xs, zs, profile(view, xs, zs));
	}

	private static float[] profile(WorldgenView view, float[] xs, float[] zs) {
		int count = xs.length;
		int samples = (count - 1) / SAMPLE_STEP + 2;
		float[] h = new float[samples];
		int floor = view.seaLevel() + 1;
		for (int k = 0; k < samples; k++) {
			int i = Math.min(k * SAMPLE_STEP, count - 1);
			h[k] = Math.max(view.surface(Math.round(xs[i]), Math.round(zs[i])), floor);
		}
		float[] tmp = new float[samples];
		for (int pass = 0; pass < 4; pass++) {
			tmp[0] = h[0];
			tmp[samples - 1] = h[samples - 1];
			for (int k = 1; k < samples - 1; k++) {
				float sum = 0;
				int n = 0;
				for (int d = -3; d <= 3; d++) {
					int q = k + d;
					if (q >= 0 && q < samples) {
						sum += h[q];
						n++;
					}
				}
				tmp[k] = sum / n;
			}
			System.arraycopy(tmp, 0, h, 0, samples);
		}
		for (int pass = 0; pass < 3; pass++) {
			for (int k = 1; k < samples; k++) {
				h[k] = Math.max(h[k - 1] - MAX_RISE, Math.min(h[k - 1] + MAX_RISE, h[k]));
			}
			for (int k = samples - 2; k >= 0; k--) {
				h[k] = Math.max(h[k + 1] - MAX_RISE, Math.min(h[k + 1] + MAX_RISE, h[k]));
			}
		}
		float[] ys = new float[count];
		for (int i = 0; i < count; i++) {
			int k0 = Math.min(i / SAMPLE_STEP, samples - 2);
			int p0 = Math.min(k0 * SAMPLE_STEP, count - 1);
			int p1 = Math.min((k0 + 1) * SAMPLE_STEP, count - 1);
			float f = p1 > p0 ? Math.min(1.0F, (float) (i - p0) / (p1 - p0)) : 1.0F;
			ys[i] = h[k0] + (h[k0 + 1] - h[k0]) * f;
		}
		return ys;
	}

	public long key() {
		return key;
	}

	public TownSite from() {
		return from;
	}

	public TownSite to() {
		return to;
	}

	public int size() {
		return xs.length;
	}

	public float x(int i) {
		return xs[i];
	}

	public float z(int i) {
		return zs[i];
	}

	public float y(int i) {
		return ys[i];
	}

	public boolean touchesChunk(int chunkX, int chunkZ) {
		int x0 = chunkX << 4;
		int z0 = chunkZ << 4;
		return maxX >= x0 && minX <= x0 + 15 && maxZ >= z0 && minZ <= z0 + 15;
	}
}
