package com.deadlands.client.hud;

import com.deadlands.client.ui.DLTheme;
import com.deadlands.config.ConfigManager;
import com.deadlands.entity.AsesinoEntity;
import com.deadlands.entity.DotEntity;
import com.deadlands.entity.GuestEntity;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * Two small readouts in the top left corner ({@code ui.awarenessHud}):
 *
 * <ul>
 *   <li><b>The noise meter:</b> how loud you are right now, from the server ({@code NoiseMeter}). A bar that stays
 *       dark and thin while you creep, hums low as you walk, and jumps with a sprint, a door, a swing or a gunshot,
 *       going from pale to red as it nears what the infected and the killers can hear.</li>
 *   <li><b>The eye:</b> shut and almost invisible while nothing has noticed you. When a killer is after you (the
 *       Aberrant stalking, searching for or chasing you; the Guest hunting; D.O.T. coming for you) it opens, a red
 *       pupil looking out of the corner, and pulses faster the closer that killer is.</li>
 * </ul>
 */
public final class AwarenessHud {
	private static final double SEEN_WITHIN = 140.0;
	private static final int BAR_W = 46;
	/** The open eye, 15 by 7: '#' outline, '+' white, 'o' iris, '@' pupil. */
	private static final String[] EYE = {
		"....#######....",
		"..###+++++###..",
		".#+++ooooo+++#.",
		"#+++oo@@@oo+++#",
		".#+++ooooo+++#.",
		"..###+++++###..",
		"....#######....",
	};
	private static final String[] SHUT = {
		"...............",
		"...............",
		"#####.....#####",
		"..#####.#####..",
		".....#####.....",
		"...............",
		"...............",
	};

	private static float noise;
	private static float shownNoise;
	private static float open;

	private AwarenessHud() {
	}

	public static void setNoise(float level) {
		noise = Mth.clamp(level, 0.0F, 1.0F);
	}

	public static void reset() {
		noise = 0.0F;
		shownNoise = 0.0F;
		open = 0.0F;
	}

	/** How close the nearest killer after this survivor is (0 right on them, 1 at the edge of sight), or -1 when none is. */
	public static double watcher(Minecraft minecraft, LocalPlayer player) {
		double best = Double.MAX_VALUE;
		var box = player.getBoundingBox().inflate(SEEN_WITHIN);
		for (AsesinoEntity a : minecraft.level.getEntitiesOfClass(AsesinoEntity.class, box, AsesinoEntity::isAlive)) {
			if (a.chasingId() == player.getId() || a.stalkingId() == player.getId()) {
				best = Math.min(best, a.distanceTo(player));
			}
		}
		for (GuestEntity g : minecraft.level.getEntitiesOfClass(GuestEntity.class, box, GuestEntity::isAlive)) {
			if (g.huntingId() == player.getId()) {
				best = Math.min(best, g.distanceTo(player));
			}
		}
		for (DotEntity d : minecraft.level.getEntitiesOfClass(DotEntity.class, box, DotEntity::isAlive)) {
			if (d.preyId() == player.getId()) {
				best = Math.min(best, d.distanceTo(player));
			}
		}
		return best == Double.MAX_VALUE ? -1.0 : Math.min(1.0, best / SEEN_WITHIN);
	}

	public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null || minecraft.options.hideGui || !ConfigManager.get().ui.awarenessHud
			|| player.isSpectator() || player.isCreative()) {
			return;
		}
		float dt = deltaTracker.getGameTimeDeltaTicks();
		shownNoise += (noise - shownNoise) * Math.min(1.0F, (noise > shownNoise ? 0.5F : 0.12F) * dt);
		double near = watcher(minecraft, player);
		float target = near >= 0.0 ? 1.0F : 0.0F;
		open += (target - open) * Math.min(1.0F, (target > open ? 0.2F : 0.05F) * dt);
		int x = 6;
		int y = 6;
		eye(graphics, x, y, near, minecraft.level.getGameTime() + deltaTracker.getGameTimeDeltaPartialTick(false));
		meter(graphics, x + 20, y + 2);
	}

	private static void eye(GuiGraphics graphics, int x, int y, double near, float time) {
		boolean watched = open > 0.5F;
		String[] grid = watched ? EYE : SHUT;
		// A beat that quickens as the killer nears: every 1.6 seconds from afar, every 0.5 right on top of you.
		float period = near < 0.0 ? 40.0F : (float) Mth.lerp(near, 10.0, 32.0);
		float pulse = watched ? 0.65F + 0.35F * Mth.sin(time * Mth.TWO_PI / period) : 1.0F;
		float alpha = Mth.clamp(0.28F + 0.72F * open, 0.0F, 1.0F) * pulse;
		int outline = DLTheme.withAlpha(watched ? 0xFF3A0B0B : 0xFF8A8F94, alpha);
		int white = DLTheme.withAlpha(0xFFD9D4C8, alpha);
		int iris = DLTheme.withAlpha(0xFFB02A1E, alpha);
		int pupil = DLTheme.withAlpha(0xFF050505, alpha);
		if (watched) {
			graphics.fill(x - 2, y - 2, x + 17, y + 9, DLTheme.withAlpha(0xFF000000, 0.4F * open));
		}
		for (int row = 0; row < grid.length; row++) {
			String line = grid[row];
			for (int col = 0; col < line.length() && col < 15; col++) {
				int colour = switch (line.charAt(col)) {
					case '#' -> outline;
					case '+' -> white;
					case 'o' -> iris;
					case '@' -> pupil;
					default -> 0;
				};
				if (colour != 0) {
					graphics.fill(x + col, y + row, x + col + 1, y + row + 1, colour);
				}
			}
		}
	}

	/** The bar: its fill shifts from a dull grey through amber to red; a faint tick marks "heard from a distance". */
	private static void meter(GuiGraphics graphics, int x, int y) {
		float level = shownNoise;
		boolean lit = level > 0.04F;
		float alpha = lit ? 1.0F : 0.45F;
		graphics.fill(x - 1, y - 1, x + BAR_W + 1, y + 6, DLTheme.withAlpha(0xFF000000, 0.55F * alpha + 0.2F));
		int filled = Math.round(BAR_W * level);
		int colour = level < 0.3F ? 0xFF9AA4AC : level < 0.6F ? 0xFFD9A441 : DLTheme.DANGER;
		graphics.fill(x, y, x + filled, y + 5, DLTheme.withAlpha(colour, alpha));
		graphics.fill(x + Math.round(BAR_W * 0.6F), y - 1, x + Math.round(BAR_W * 0.6F) + 1, y + 6, DLTheme.withAlpha(DLTheme.TEXT, 0.35F * alpha));
	}
}
