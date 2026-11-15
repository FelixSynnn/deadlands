package com.deadlands.client.achievement;

import com.deadlands.client.ui.DLTheme;
import com.deadlands.client.ui.widget.DLButton;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The Achievements screen (it replaces vanilla's advancements screen, opened from the pause menu):
 * every DEADLANDS achievement grouped by category (story, exploration, survival, combat, lore,
 * endings, horror, co-op), earned ones lit, the rest dim. A hidden achievement shows as "???" until
 * earned, so nothing is spoiled. Scroll with the mouse wheel.
 */
public class DLAchievementsScreen extends Screen implements ClientAdvancements.Listener {
	private static final int W = 360;
	private static final int H = 230;
	private static final int ROW = 24;

	private final @Nullable Screen parent;
	private final Map<AdvancementHolder, AdvancementProgress> progress = new HashMap<>();
	/** Hidden achievements the server told us exist (Minecraft only sends them once earned). */
	private static List<String> secretIds = List.of();

	public static void secrets(List<String> ids) {
		secretIds = List.copyOf(ids);
	}

	/** One row: a known achievement, or a secret not yet earned (holder null). */
	private record Row(String path, AdvancementHolder holder) {
	}

	private List<Row> rows() {
		List<Row> rows = new ArrayList<>();
		List<String> known = new ArrayList<>();
		for (AdvancementHolder holder : achievements) {
			rows.add(new Row(holder.id().getPath(), holder));
			known.add(holder.id().getPath());
		}
		for (String id : secretIds) {
			if (!known.contains(id)) {
				rows.add(new Row(id, null));
			}
		}
		rows.sort(Comparator.comparing(Row::path));
		return rows;
	}

	private static String category(String path) {
		int slash = path.indexOf('/');
		return slash < 0 ? "general" : path.substring(0, slash);
	}
	private final List<AdvancementHolder> achievements = new ArrayList<>();
	private double scroll;

	public DLAchievementsScreen(@Nullable Screen parent) {
		super(Component.translatable("achievement.deadlands.screen"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		if (minecraft.player != null) {
			minecraft.player.connection.getAdvancements().setListener(this);
		}
		addRenderableWidget(new DLButton((width + W) / 2 - 88, (height + H) / 2 - 26, 80, 18, Component.translatable("gui.back"), button -> onClose()));
	}

	@Override
	public void removed() {
		if (minecraft.player != null) {
			minecraft.player.connection.getAdvancements().setListener(null);
		}
	}

	private void add(AdvancementNode node) {
		AdvancementHolder holder = node.holder();
		if (holder.id().getNamespace().equals("deadlands") && holder.value().display().isPresent() && node.parent() != null && !achievements.contains(holder)) {
			achievements.add(holder);
			achievements.sort(Comparator.comparing(h -> h.id().getPath()));
		}
	}

	@Override
	public void onAddAdvancementRoot(AdvancementNode node) {
		add(node);
	}

	@Override
	public void onRemoveAdvancementRoot(AdvancementNode node) {
		achievements.remove(node.holder());
	}

	@Override
	public void onAddAdvancementTask(AdvancementNode node) {
		add(node);
	}

	@Override
	public void onRemoveAdvancementTask(AdvancementNode node) {
		achievements.remove(node.holder());
	}

	@Override
	public void onAdvancementsCleared() {
		achievements.clear();
		progress.clear();
	}

	@Override
	public void onUpdateAdvancementProgress(AdvancementNode node, AdvancementProgress value) {
		progress.put(node.holder(), value);
	}

	@Override
	public void onSelectedTabChanged(@Nullable AdvancementHolder holder) {
	}

	private boolean done(AdvancementHolder holder) {
		AdvancementProgress value = progress.get(holder);
		return value != null && value.isDone();
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		int visible = (H - 70) / ROW;
		List<Row> all = rows();
		int rows = all.size() + (int) all.stream().map(r -> category(r.path())).distinct().count();
		scroll = Math.max(0, Math.min(Math.max(0, rows - visible), scroll - scrollY));
		return true;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		int x = (width - W) / 2;
		int y = (height - H) / 2;
		int earned = 0;
		List<Row> rows = rows();
		for (AdvancementHolder holder : achievements) {
			earned += done(holder) ? 1 : 0;
		}
		graphics.drawString(font, title, x + 10, y + 10, DLTheme.ACCENT, false);
		Component count = Component.translatable("achievement.deadlands.count", earned, rows.size());
		graphics.drawString(font, count, x + W - 10 - font.width(count), y + 10, DLTheme.TEXT_DIM, false);
		graphics.enableScissor(x + 6, y + 26, x + W - 6, y + H - 32);
		int row = 0;
		String category = "";
		int top = y + 30;
		for (Row entry : rows) {
			AdvancementHolder holder = entry.holder();
			String next = category(entry.path());
			if (!next.equals(category)) {
				category = next;
				int ry = top + (int) ((row - scroll) * ROW);
				graphics.drawString(font, Component.translatable("achievement.deadlands.category." + category), x + 10, ry + 8, DLTheme.TEXT_DIM, false);
				row++;
			}
			int ry = top + (int) ((row - scroll) * ROW);
			DisplayInfo display = holder == null ? null : holder.value().display().orElseThrow();
			boolean has = holder != null && done(holder);
			boolean secret = holder == null || (display.isHidden() && !has);
			graphics.fill(x + 10, ry, x + W - 10, ry + ROW - 2, has ? 0x33C8902E : 0x22000000);
			if (!secret) {
				graphics.renderFakeItem(display.getIcon(), x + 14, ry + 3);
			}
			Component name = secret ? Component.translatable("achievement.deadlands.secret") : display.getTitle();
			Component description = secret ? Component.translatable("achievement.deadlands.secret.description") : display.getDescription();
			graphics.drawString(font, name, x + 36, ry + 3, has ? DLTheme.TEXT : DLTheme.TEXT_DIM, false);
			graphics.drawString(font, font.plainSubstrByWidth(description.getString(), W - 60), x + 36, ry + 13, DLTheme.TEXT_FAINT, false);
			row++;
		}
		graphics.disableScissor();
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.renderBackground(graphics, mouseX, mouseY, partialTick);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, DLTheme.PANEL, (width - W) / 2, (height - H) / 2, W, H);
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}
}
