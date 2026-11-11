package com.deadlands.client.mission;

import com.deadlands.client.ui.DLTheme;
import com.deadlands.network.MissionSyncPayload;
import com.deadlands.registry.DLSounds;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * The mission tracker: top right, restrained. The current mission's name in small accent letters
 * and its current objective beneath, on a faint dark strip with a thin accent rule. When a mission
 * arrives or its objective changes, the tracker slides in from the edge with a quiet sound. Nothing
 * shows without an active mission.
 */
public final class MissionTrackerHud {
	private static final int WIDTH = 170;
	private static final int SLIDE_TICKS = 14;
	private static String shown = "";
	private static long changedAt = -1000L;

	private MissionTrackerHud() {
	}

	static void onUpdate(MissionSyncPayload payload) {
		String now = describe(payload.missions());
		if (!now.equals(shown)) {
			boolean appeared = !now.isEmpty();
			shown = now;
			Minecraft minecraft = Minecraft.getInstance();
			changedAt = minecraft.level == null ? 0L : minecraft.level.getGameTime();
			if (appeared) {
				DLTheme.playUi(DLSounds.UI_PAGE_TURN, 1.1F, 0.5F);
			}
		}
	}

	/** A key for "what the tracker shows": the first active mission and its first open objective. */
	private static String describe(List<MissionSyncPayload.Entry> missions) {
		for (MissionSyncPayload.Entry entry : missions) {
			if (entry.state() == MissionSyncPayload.ACTIVE) {
				for (MissionSyncPayload.Objective objective : entry.objectives()) {
					if (!objective.done()) {
						return entry.id() + "|" + objective.text() + "|" + objective.progress();
					}
				}
				return entry.id();
			}
		}
		return "";
	}

	public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.options.hideGui || minecraft.level == null) {
			return;
		}
		MissionSyncPayload.Entry active = null;
		for (MissionSyncPayload.Entry entry : ClientMissions.all()) {
			if (entry.state() == MissionSyncPayload.ACTIVE) {
				active = entry;
				break;
			}
		}
		if (active == null) {
			return;
		}
		MissionSyncPayload.Objective current = null;
		for (MissionSyncPayload.Objective objective : active.objectives()) {
			if (!objective.done()) {
				current = objective;
				break;
			}
		}
		Font font = minecraft.font;
		float age = (minecraft.level.getGameTime() - changedAt) + deltaTracker.getGameTimeDeltaPartialTick(false);
		float slide = Mth.clamp(age / SLIDE_TICKS, 0.0F, 1.0F);
		float eased = 1.0F - (1.0F - slide) * (1.0F - slide) * (1.0F - slide);
		int x = graphics.guiWidth() - WIDTH - 8 + Math.round((1.0F - eased) * (WIDTH + 12));
		int y = 8;
		Component title = Component.translatable(active.title());
		Component objectiveText = current == null ? Component.translatable("mission.deadlands.tracker.done")
			: current.count() > 1 ? Component.translatable("mission.deadlands.tracker.progress", Component.translatable(current.text()), current.progress(), current.count())
			: Component.translatable(current.text());
		List<FormattedCharSequence> lines = font.split(objectiveText, WIDTH - 12);
		int height = 16 + lines.size() * 10;
		graphics.fill(x, y, x + WIDTH, y + height, 0x88000000);
		graphics.fill(x, y, x + 1, y + height, DLTheme.ACCENT);
		graphics.drawString(font, title, x + 6, y + 4, DLTheme.ACCENT, false);
		int ty = y + 15;
		for (FormattedCharSequence line : lines) {
			graphics.drawString(font, line, x + 6, ty, DLTheme.TEXT, false);
			ty += 10;
		}
	}

	static void reset() {
		shown = "";
		changedAt = -1000L;
	}
}
