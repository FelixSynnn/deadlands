package com.deadlands.client.mission;

import com.deadlands.client.ui.DLTheme;
import com.deadlands.client.ui.widget.DLButton;
import com.deadlands.network.MissionSyncPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * The pause screen's Missions tab: active, completed and failed missions on the left; the chosen
 * one on the right with who gave it, what it is about, each objective (done or not, with progress)
 * and the lore entries that relate to it.
 */
public class MissionsScreen extends Screen {
	private static final int W = 380;
	private static final int H = 220;
	private static final String[] TABS = {"mission.deadlands.tab.active", "mission.deadlands.tab.completed", "mission.deadlands.tab.failed"};

	private final Screen parent;
	private int tab;
	private int selected;

	public MissionsScreen(Screen parent) {
		super(Component.translatable("mission.deadlands.screen"));
		this.parent = parent;
	}

	private List<MissionSyncPayload.Entry> shown() {
		List<MissionSyncPayload.Entry> list = new ArrayList<>();
		for (MissionSyncPayload.Entry entry : ClientMissions.all()) {
			if (entry.state() == tab) {
				list.add(entry);
			}
		}
		return list;
	}

	@Override
	protected void init() {
		int x = (width - W) / 2;
		int y = (height - H) / 2;
		for (int i = 0; i < TABS.length; i++) {
			int index = i;
			addRenderableWidget(new DLButton(x + 8 + i * 80, y + 8, 76, 18, Component.translatable(TABS[i]), button -> {
				tab = index;
				selected = 0;
			}));
		}
		addRenderableWidget(new DLButton(x + W - 88, y + H - 26, 80, 18, Component.translatable("gui.back"), button -> onClose()));
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int x = (width - W) / 2;
		int y = (height - H) / 2 + 34;
		List<MissionSyncPayload.Entry> list = shown();
		if (event.x() >= x + 8 && event.x() < x + 140) {
			int row = (int) ((event.y() - y) / 14);
			if (row >= 0 && row < list.size()) {
				selected = row;
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		int x = (width - W) / 2;
		int y = (height - H) / 2;
		List<MissionSyncPayload.Entry> list = shown();
		int ly = y + 34;
		for (int i = 0; i < list.size(); i++) {
			int color = i == selected ? DLTheme.ACCENT_LIGHT : DLTheme.TEXT;
			graphics.drawString(font, font.plainSubstrByWidth(Component.translatable(list.get(i).title()).getString(), 128), x + 10, ly, color, false);
			ly += 14;
		}
		if (list.isEmpty()) {
			graphics.drawString(font, Component.translatable("mission.deadlands.none"), x + 10, ly, DLTheme.TEXT_FAINT, false);
			return;
		}
		MissionSyncPayload.Entry entry = list.get(Math.min(selected, list.size() - 1));
		int dx = x + 150;
		int dy = y + 34;
		int dw = W - 160;
		graphics.drawString(font, Component.translatable(entry.title()), dx, dy, DLTheme.ACCENT, false);
		dy += 12;
		if (!entry.giver().isEmpty()) {
			graphics.drawString(font, Component.translatable("mission.deadlands.giver", Component.translatable("mission.deadlands.giver." + entry.giver())), dx, dy, DLTheme.TEXT_DIM, false);
			dy += 12;
		}
		for (FormattedCharSequence line : font.split(Component.translatable(entry.description()), dw)) {
			graphics.drawString(font, line, dx, dy, DLTheme.TEXT, false);
			dy += 10;
		}
		dy += 6;
		for (MissionSyncPayload.Objective objective : entry.objectives()) {
			Component text = objective.count() > 1
				? Component.translatable("mission.deadlands.tracker.progress", Component.translatable(objective.text()), objective.progress(), objective.count())
				: Component.translatable(objective.text());
			graphics.drawString(font, objective.done() ? "✓" : "•", dx, dy, objective.done() ? DLTheme.ACCENT : DLTheme.TEXT_DIM, false);
			for (FormattedCharSequence line : font.split(text, dw - 10)) {
				graphics.drawString(font, line, dx + 10, dy, objective.done() ? DLTheme.TEXT_FAINT : DLTheme.TEXT, false);
				dy += 10;
			}
		}
		if (!entry.lore().isEmpty()) {
			dy += 6;
			graphics.drawString(font, Component.translatable("mission.deadlands.lore"), dx, dy, DLTheme.TEXT_DIM, false);
			dy += 10;
			for (String lore : entry.lore()) {
				graphics.drawString(font, Component.translatable("lore.deadlands." + lore + ".title"), dx + 6, dy, DLTheme.TEXT, false);
				dy += 10;
			}
		}
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.renderBackground(graphics, mouseX, mouseY, partialTick);
		int x = (width - W) / 2;
		int y = (height - H) / 2;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, DLTheme.PANEL, x, y, W, H);
		graphics.fill(x + 144, y + 32, x + 145, y + H - 32, 0x44000000);
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}
}
