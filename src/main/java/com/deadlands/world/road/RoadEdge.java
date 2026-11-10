package com.deadlands.world.road;

import com.deadlands.world.Hashing;
import com.deadlands.world.WorldgenView;
import com.deadlands.world.town.TownSite;
import com.deadlands.world.town.TownSites;
import net.minecraft.core.Direction;

/**
 * One road between two towns, as a polyline of points one block apart with a surface height per
 * point. It leaves each town at the gate facing the other town, along that town's main street, then
 * bends smoothly across country (a cubic Bezier with a gentle meander). The height follows the
 * terrain, smoothed and limited to a 1-in-4 grade, and is pinned to both gate heights so it meets
 * the town streets exactly. Over water and valleys the profile stays high, which makes bridges.
 */
public final class RoadEdge {
	/** Samples of terrain height are taken every this many points, then interpolated. */
	private static final int SAMPLE_STEP = 8;
	/** Largest height change between two samples (SAMPLE_STEP blocks apart). */
	private static final float MAX_RISE = 2.0F;
	/** Corridor around the road that its chunks must cover: highway shoulders, signs and pylons. */
	private static final int MARGIN = 14;

	private final long key;
	private final TownSite from;
	private final TownSite to;
	private final Direction fromSide;
	private final Direction toSide;
	private final float[] xs;
	private final float[] zs;
	private final float[] ys;
	private final int minX;
	private final int minZ;
	private final int maxX;
	private final int maxZ;

	private RoadEdge(long key, TownSite from, TownSite to, Direction fromSide, Direction toSide, float[] xs, float[] zs, float[] ys) {
		this.key = key;
		this.from = from;
		this.to = to;
		this.fromSide = fromSide;
		this.toSide = toSide;
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

	static RoadEdge build(WorldgenView view, long key, TownSite from, TownSite to) {
		Direction fromSide = from.sideFacing(to);
		Direction toSide = to.sideFacing(from);
		double ax = from.gateX(fromSide) + 0.5;
		double az = from.gateZ(fromSide) + 0.5;
		double bx = to.gateX(toSide) + 0.5;
		double bz = to.gateZ(toSide) + 0.5;
		double length = Math.hypot(bx - ax, bz - az);
		double handle = length * 0.35;
		double p1x = ax + fromSide.getStepX() * handle;
		double p1z = az + fromSide.getStepZ() * handle;
		double p2x = bx + toSide.getStepX() * handle;
		double p2z = bz + toSide.getStepZ() * handle;

		// Dense samples of the curve, then resampled to one point per block of arc length.
		int dense = (int) Math.ceil(length * 3.0) + 16;
		double[] cx = new double[dense + 1];
		double[] cz = new double[dense + 1];
		double[] arc = new double[dense + 1];
		for (int i = 0; i <= dense; i++) {
			double t = (double) i / dense;
			double u = 1.0 - t;
			cx[i] = u * u * u * ax + 3 * u * u * t * p1x + 3 * u * t * t * p2x + t * t * t * bx;
			cz[i] = u * u * u * az + 3 * u * u * t * p1z + 3 * u * t * t * p2z + t * t * t * bz;
			if (i > 0) {
				arc[i] = arc[i - 1] + Math.hypot(cx[i] - cx[i - 1], cz[i] - cz[i - 1]);
			}
		}
		double total = arc[dense];
		int count = Math.max(2, (int) Math.floor(total) + 1);
		float[] xs = new float[count];
		float[] zs = new float[count];
		long hash = Hashing.mix(key ^ view.seed());
		double phaseA = Hashing.unit(hash) * Math.PI * 2;
		double phaseB = Hashing.unit(Hashing.mix(hash)) * Math.PI * 2;
		int j = 0;
		for (int i = 0; i < count; i++) {
			double s = Math.min(i, total);
			while (j < dense - 1 && arc[j + 1] < s) {
				j++;
			}
			double span = arc[j + 1] - arc[j];
			double f = span > 1e-9 ? (s - arc[j]) / span : 0.0;
			double x = cx[j] + (cx[j + 1] - cx[j]) * f;
			double z = cz[j] + (cz[j + 1] - cz[j]) * f;
			// Meander sideways, fading out near both gates so the road leaves each town straight.
			double tx = cx[j + 1] - cx[j];
			double tz = cz[j + 1] - cz[j];
			double norm = Math.hypot(tx, tz);
			if (norm > 1e-9) {
				double taper = smooth(Math.min(s, total - s) / 48.0);
				double offset = 7.0 * taper * (0.6 * Math.sin(s / 11.0 + phaseA) + 0.4 * Math.sin(s / 4.7 + phaseB));
				x += -tz / norm * offset;
				z += tx / norm * offset;
			}
			xs[i] = (float) x;
			zs[i] = (float) z;
		}
		return new RoadEdge(key, from, to, fromSide, toSide, xs, zs, profile(view, xs, zs, from, fromSide, to, toSide));
	}

	private static float[] profile(WorldgenView view, float[] xs, float[] zs, TownSite from, Direction fromSide, TownSite to, Direction toSide) {
		int count = xs.length;
		int samples = (count - 1) / SAMPLE_STEP + 2;
		float[] h = new float[samples];
		int floor = view.seaLevel();
		for (int k = 0; k < samples; k++) {
			int i = Math.min(k * SAMPLE_STEP, count - 1);
			h[k] = Math.max(view.surface(Math.round(xs[i]), Math.round(zs[i])), floor);
		}
		h[0] = TownSites.gateHeight(view, from.gateX(fromSide), from.gateZ(fromSide));
		h[samples - 1] = TownSites.gateHeight(view, to.gateX(toSide), to.gateZ(toSide));
		float[] tmp = new float[samples];
		for (int pass = 0; pass < 3; pass++) {
			tmp[0] = h[0];
			tmp[samples - 1] = h[samples - 1];
			for (int k = 1; k < samples - 1; k++) {
				float sum = 0;
				int n = 0;
				for (int d = -2; d <= 2; d++) {
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
		for (int pass = 0; pass < 2; pass++) {
			for (int k = 1; k < samples - 1; k++) {
				h[k] = clamp(h[k], h[k - 1] - MAX_RISE, h[k - 1] + MAX_RISE);
			}
			for (int k = samples - 2; k > 0; k--) {
				h[k] = clamp(h[k], h[k + 1] - MAX_RISE, h[k + 1] + MAX_RISE);
			}
		}
		// Sample k sits at point min(k * SAMPLE_STEP, last); interpolate by true positions so the
		// final point lands exactly on the far gate's height.
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

	private static float clamp(float v, float lo, float hi) {
		return v < lo ? lo : Math.min(v, hi);
	}

	private static double smooth(double t) {
		t = Math.max(0.0, Math.min(1.0, t));
		return t * t * (3 - 2 * t);
	}

	public long key() {
		return key;
	}

	/** Highway, road or dirt road, from the sizes of the two towns. */
	public RoadKind kind() {
		return RoadKind.between(from, to);
	}

	/**
	 * Half width of the paved part at point {@code i}: the kind's width out in the country,
	 * narrowing to an ordinary road's within 32 blocks of either gate.
	 */
	public float pavedAt(int i) {
		float base = RoadKind.ROAD.paved();
		float t = (float) smooth(Math.min(i, xs.length - 1 - i) / 32.0);
		return base + (kind().paved() - base) * t;
	}

	public float shoulderAt(int i) {
		RoadKind kind = kind();
		return pavedAt(i) + (kind.shoulder() - kind.paved());
	}

	public TownSite from() {
		return from;
	}

	public TownSite to() {
		return to;
	}

	public Direction fromSide() {
		return fromSide;
	}

	public Direction toSide() {
		return toSide;
	}

	/** The side of the given town this road leaves from. */
	public Direction sideAt(TownSite town) {
		return town.key() == from.key() ? fromSide : toSide;
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
