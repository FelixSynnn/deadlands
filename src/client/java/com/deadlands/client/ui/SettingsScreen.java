package com.deadlands.client.ui;

import com.deadlands.client.ui.widget.DLButton;
import com.deadlands.client.ui.widget.DLSlider;
import com.deadlands.client.ui.widget.DLToggle;
import com.deadlands.config.ConfigEntry;
import com.deadlands.config.ConfigManager;
import com.deadlands.config.DeadlandsConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * Every DEADLANDS setting, built from the {@link ConfigEntry} metadata. Edits go to a working copy
 * that is validated and saved when the screen closes. In multiplayer (when not the host) only the
 * [client] settings can be changed; the rest belong to the server.
 */
public class SettingsScreen extends Screen {
	private static final int ROW_H = 22;
	private static final int CONTROL_W = 104;
	private static int lastSection;

	private final @Nullable Screen parent;
	private final DeadlandsConfig working;
	private final boolean serverEditable;
	private final List<Row> rows = new ArrayList<>();
	private final List<DLButton> sectionButtons = new ArrayList<>();
	private int section;
	private float scroll;
	private boolean changed;
	private int panelX;
	private int panelY;
	private int panelW;
	private int panelH;
	private int listX;
	private int listY;
	private int listW;
	private int listH;

	private record Row(ConfigManager.Entry entry, AbstractWidget control, int offset) {
	}

	public SettingsScreen(@Nullable Screen parent) {
		super(Component.translatable("screen.deadlands.settings"));
		this.parent = parent;
		this.working = ConfigManager.copy(ConfigManager.get());
		Minecraft minecraft = Minecraft.getInstance();
		this.serverEditable = minecraft.level == null || minecraft.hasSingleplayerServer();
	}

	@Override
	protected void init() {
		List<ConfigManager.Section> sections = ConfigManager.layout();
		section = Mth.clamp(lastSection, 0, sections.size() - 1);
		panelW = Math.min(width - 12, 460);
		panelH = Math.min(height - 12, 280);
		panelX = (width - panelW) / 2;
		panelY = (height - panelH) / 2;

		int catX = panelX + 12;
		int catY = panelY + 36;
		int catW = 108;
		int catStep = Math.max(14, Math.min(17, (panelH - 72) / sections.size()));
		sectionButtons.clear();
		for (int i = 0; i < sections.size(); i++) {
			int index = i;
			sectionButtons.add(addRenderableWidget(new DLButton(catX, catY + i * catStep, catW, catStep - 1,
				Component.translatable(sections.get(i).translationKey()), b -> selectSection(index))));
		}

		listX = catX + catW + 10;
		listY = catY;
		listW = panelX + panelW - 12 - listX;
		listH = panelY + panelH - 32 - listY;

		int footerY = panelY + panelH - 26;
		addRenderableWidget(new DLButton(panelX + panelW - 12 - 80, footerY, 80, 18, Component.translatable("gui.done"), b -> onClose()));
		addRenderableWidget(new DLButton(panelX + panelW - 12 - 80 - 6 - 96, footerY, 96, 18,
			Component.translatable("screen.deadlands.settings.reset"), b -> resetSection()));
		addRenderableWidget(new DLButton(panelX + 12, footerY, 108, 18,
			Component.translatable("screen.deadlands.settings.open_file"), b -> Util.getPlatform().openPath(ConfigManager.path().getParent())));
		buildRows();
	}

	private void selectSection(int index) {
		if (index != section) {
			section = index;
			lastSection = index;
			buildRows();
		}
	}

	private void buildRows() {
		for (Row row : rows) {
			removeWidget(row.control());
		}
		rows.clear();
		scroll = 0;
		ConfigManager.Section current = ConfigManager.layout().get(section);
		Object instance = current.instance(working);
		int offset = 0;
		for (ConfigManager.Entry entry : current.entries()) {
			AbstractWidget control = createControl(entry, instance);
			control.active = isEditable(entry);
			rows.add(new Row(entry, addRenderableWidget(control), offset));
			offset += ROW_H;
		}
		for (int i = 0; i < sectionButtons.size(); i++) {
			sectionButtons.get(i).setMessage(Component.translatable(ConfigManager.layout().get(i).translationKey())
				.withStyle(i == section ? ChatFormatting.UNDERLINE : ChatFormatting.RESET));
		}
		layoutRows();
	}

	private boolean isEditable(ConfigManager.Entry entry) {
		return serverEditable || entry.meta().client();
	}

	private AbstractWidget createControl(ConfigManager.Entry entry, Object instance) {
		int x = listX + listW - CONTROL_W - 6;
		Class<?> type = entry.type();
		if (type == boolean.class) {
			return new DLToggle(x, 0, CONTROL_W, 18, (Boolean) entry.get(instance), value -> {
				entry.set(instance, value);
				changed = true;
			});
		}
		if (type == int.class || type == double.class) {
			return new ConfigSlider(x, 0, CONTROL_W, 18, entry, instance);
		}
		if (type.isEnum()) {
			return new DLButton(x, 0, CONTROL_W, 18, enumLabel(entry.get(instance)), button -> {
				Object[] values = type.getEnumConstants();
				Object next = values[(((Enum<?>) entry.get(instance)).ordinal() + 1) % values.length];
				entry.set(instance, next);
				button.setMessage(enumLabel(next));
				changed = true;
			});
		}
		return new DLButton(x, 0, CONTROL_W, 18, Component.literal(String.valueOf(entry.get(instance))), button -> {
		});
	}

	private static Component enumLabel(Object value) {
		return Component.translatable("config.deadlands.enum." + ((Enum<?>) value).name().toLowerCase(Locale.ROOT));
	}

	private void layoutRows() {
		for (Row row : rows) {
			int y = listY + 4 + row.offset() - Math.round(scroll);
			row.control().setY(y);
			row.control().visible = y >= listY && y + row.control().getHeight() <= listY + listH;
		}
	}

	private float maxScroll() {
		return Math.max(0, rows.size() * ROW_H + 8 - listH);
	}

	private void resetSection() {
		ConfigManager.Section current = ConfigManager.layout().get(section);
		Object defaults = current.instance(new DeadlandsConfig());
		Object instance = current.instance(working);
		for (ConfigManager.Entry entry : current.entries()) {
			if (isEditable(entry)) {
				entry.set(instance, entry.get(defaults));
			}
		}
		changed = true;
		buildRows();
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (mouseX >= listX && mouseX < listX + listW && mouseY >= listY && mouseY < listY + listH) {
			scroll = Mth.clamp(scroll - (float) scrollY * ROW_H, 0.0F, maxScroll());
			layoutRows();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public void onClose() {
		if (changed) {
			ConfigManager.replace(working);
		}
		minecraft.setScreen(parent);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		g.blitSprite(RenderPipelines.GUI_TEXTURED, DLTheme.PANEL, panelX, panelY, panelW, panelH);
		g.drawString(font, title, panelX + 14, panelY + 12, DLTheme.ACCENT, true);
		Component note = Component.translatable(serverEditable ? "screen.deadlands.settings.note.local" : "screen.deadlands.settings.note.remote");
		g.drawString(font, note, panelX + panelW - 14 - font.width(note), panelY + 12, DLTheme.TEXT_FAINT, false);

		g.blitSprite(RenderPipelines.GUI_TEXTURED, DLTheme.INSET, listX, listY, listW, listH);
		ConfigManager.Section current = ConfigManager.layout().get(section);
		boolean anyEditable = false;
		ConfigManager.Entry hoveredEntry = null;
		g.enableScissor(listX, listY, listX + listW, listY + listH);
		for (Row row : rows) {
			int y = listY + 4 + row.offset() - Math.round(scroll);
			if (y + ROW_H < listY || y > listY + listH) {
				continue;
			}
			anyEditable |= row.control().active;
			if ((row.offset() / ROW_H) % 2 == 1) {
				g.fill(listX + 2, y - 2, listX + listW - 2, y + ROW_H - 2, 0x18FFFFFF);
			}
			int labelColor = row.control().active ? DLTheme.TEXT : DLTheme.TEXT_FAINT;
			Component label = label(row.entry());
			int labelMax = listW - CONTROL_W - 24;
			List<FormattedCharSequence> lines = font.split(label, labelMax);
			g.drawString(font, lines.getFirst(), listX + 8, y + 5, labelColor, false);
			int tagX = listX + 8 + font.width(lines.getFirst()) + 4;
			if (row.entry().isPending()) {
				Component tag = Component.translatable("screen.deadlands.settings.tag.milestone", row.entry().meta().pendingMilestone());
				g.drawString(font, tag, Math.min(tagX, listX + listW - CONTROL_W - 12 - font.width(tag)), y + 5, DLTheme.ACCENT, false);
			}
			if (mouseX >= listX && mouseX < listX + listW - CONTROL_W - 8 && mouseY >= y && mouseY < y + ROW_H - 2
				&& mouseY >= listY && mouseY < listY + listH) {
				hoveredEntry = row.entry();
			}
		}
		g.disableScissor();
		if (!anyEditable && !rows.isEmpty() && !serverEditable) {
			g.drawCenteredString(font, Component.translatable("screen.deadlands.settings.server_controlled"),
				listX + listW / 2, listY + listH - 14, DLTheme.ACCENT);
		}
		if (maxScroll() > 0) {
			int barH = Math.max(16, (int) (listH * listH / (float) (rows.size() * ROW_H + 8)));
			int barY = listY + (int) ((listH - barH) * (scroll / maxScroll()));
			g.fill(listX + listW - 4, barY, listX + listW - 2, barY + barH, DLTheme.withAlpha(DLTheme.ACCENT, 0.7F));
		}

		super.render(g, mouseX, mouseY, partialTick);

		// Underline-free marker for the selected section.
		DLButton selected = sectionButtons.get(section);
		g.fill(selected.getX() - 4, selected.getY() + 2, selected.getX() - 2, selected.getY() + selected.getHeight() - 2, DLTheme.ACCENT);

		if (hoveredEntry != null) {
			g.setTooltipForNextFrame(font, tooltip(hoveredEntry), mouseX, mouseY);
		}
	}

	private Component label(ConfigManager.Entry entry) {
		String key = entry.translationKey();
		if (Language.getInstance().has(key)) {
			return Component.translatable(key);
		}
		// Fallback: "spawnDistanceMin" -> "Spawn distance min".
		String spaced = entry.key().replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT);
		return Component.literal(Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1));
	}

	private List<FormattedCharSequence> tooltip(ConfigManager.Entry entry) {
		List<FormattedCharSequence> lines = new ArrayList<>();
		ConfigEntry meta = entry.meta();
		lines.addAll(font.split(Component.literal(meta.comment()), 220));
		if (entry.type() == int.class) {
			lines.addAll(font.split(Component.translatable("screen.deadlands.settings.range",
				ConfigManager.plain(meta.min()), ConfigManager.plain(meta.max())).withStyle(ChatFormatting.GRAY), 220));
		} else if (entry.type() == double.class) {
			lines.addAll(font.split(Component.translatable("screen.deadlands.settings.range",
				ConfigManager.decimal(meta.min()), ConfigManager.decimal(meta.max())).withStyle(ChatFormatting.GRAY), 220));
		}
		if (meta.client()) {
			lines.addAll(font.split(Component.translatable("screen.deadlands.settings.client").withStyle(ChatFormatting.DARK_AQUA), 220));
		} else if (!serverEditable) {
			lines.addAll(font.split(Component.translatable("screen.deadlands.settings.server").withStyle(ChatFormatting.GRAY), 220));
		}
		if (entry.isPending()) {
			lines.addAll(font.split(Component.translatable("screen.deadlands.settings.pending", meta.pendingMilestone()).withStyle(ChatFormatting.GOLD), 220));
		}
		return lines;
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		if (minecraft.level == null) {
			TitleBackdrop.render(g, width, height, 0.0F, 0.0F, ConfigManager.get().ui.filmGrain);
			g.fill(0, 0, width, height, 0x99000000);
		} else {
			super.renderBackground(g, mouseX, mouseY, partialTick);
		}
	}

	/** Slider bound to an int or double setting, stepping sensibly for its range. */
	private final class ConfigSlider extends DLSlider {
		private final ConfigManager.Entry entry;
		private final Object instance;
		private final double min;
		private final double max;
		private final boolean integer;

		ConfigSlider(int x, int y, int width, int height, ConfigManager.Entry entry, Object instance) {
			super(x, y, width, height, Component.empty(), 0.0);
			this.entry = entry;
			this.instance = instance;
			this.min = entry.meta().min();
			this.max = entry.meta().max();
			this.integer = entry.type() == int.class;
			double current = ((Number) entry.get(instance)).doubleValue();
			this.value = max > min ? Mth.clamp((current - min) / (max - min), 0.0, 1.0) : 0.0;
			updateMessage();
		}

		private double current() {
			double raw = min + value * (max - min);
			if (integer) {
				return Math.round(raw);
			}
			double step = (max - min) <= 1.0 ? 0.01 : 0.05;
			return Math.round(raw / step) * step;
		}

		@Override
		protected void updateMessage() {
			double v = current();
			setMessage(Component.literal(integer ? Long.toString(Math.round(v)) : String.format(Locale.ROOT, "%.2f", v)));
		}

		@Override
		protected void applyValue() {
			double v = current();
			entry.set(instance, integer ? (Object) (int) Math.round(v) : (Object) v);
			changed = true;
		}
	}
}
