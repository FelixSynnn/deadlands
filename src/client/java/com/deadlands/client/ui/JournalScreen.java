package com.deadlands.client.ui;

import com.deadlands.Deadlands;
import com.deadlands.client.ClientNetworking;
import com.deadlands.client.ClientState;
import com.deadlands.client.input.DLKeyMappings;
import com.deadlands.network.WorldStatusPayload;
import com.deadlands.persistence.ChronicleEntry;
import com.deadlands.progression.EncounterSchedule;
import com.deadlands.progression.TimeOfDay;
import com.deadlands.registry.DLSounds;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2fStack;

/**
 * The Survivor's Journal: a leather notebook with bookmark tabs. LOG shows the day, time, weather,
 * your survival record and the world chronicle; THREATS shows the horde and boss schedule the
 * server is running; SETTINGS opens the DEADLANDS settings.
 *
 * <p>Everything is laid out on a fixed 384x240 page grid and scaled to fit small windows.
 */
public class JournalScreen extends Screen {
	public static final Identifier BOOK = Deadlands.id("textures/gui/journal/book.png");
	private static final int BOOK_W = 384;
	private static final int BOOK_H = 240;
	private static final int TEX_W = 768;
	private static final int TEX_H = 480;
	private static final int TAB_X = 368;
	private static final int TAB_W = 32;
	private static final int TAB_H = 22;
	private static final int[] TAB_Y = {30, 56, 134, 82, 108, 160};
	private static final int STORY_ROW = 14;
	/** The story so far, from the server ({@code StoryPayload}); null until it arrives. */
	private static com.deadlands.network.@org.jspecify.annotations.Nullable StoryPayload story;
	private static int archiveCategory;
	private int archiveScroll;
	private static final int LEFT_X = 30;
	private static final int RIGHT_X = 206;
	private static final int PAGE_TOP = 24;
	private static final int PAGE_W = 148;
	private static final int PAGE_BOTTOM = 214;

	private enum Tab {
		LOG(DLTheme.ICON_LOG, "journal.deadlands.tab.log", 0xFF9A3A2E),
		THREATS(DLTheme.ICON_THREAT, "journal.deadlands.tab.threats", 0xFF6F7440),
		SETTINGS(DLTheme.ICON_SETTINGS, "journal.deadlands.tab.settings", 0xFF4E6070),
		// Last in the enum so the settings tab keeps index 2; drawn third, above settings.
		TASKS(DLTheme.ICON_TASKS, "journal.deadlands.tab.tasks", 0xFF7A5A32),
		ARCHIVE(DLTheme.ICON_ARCHIVE, "journal.deadlands.tab.archive", 0xFF3E5A44),
		// Phase 5 step 6: the story so far and the Cinematic Archive (drawn below settings).
		STORY(DLTheme.ICON_TASKS, "journal.deadlands.tab.story", 0xFF5A3A5E);

		final Identifier icon;
		final String key;
		final int tint;

		Tab(Identifier icon, String key, int tint) {
			this.icon = icon;
			this.key = key;
			this.tint = tint;
		}
	}

	private static Tab lastTab = Tab.LOG;

	private Tab tab = lastTab;
	private float scale = 1.0F;
	private float bookX;
	private float bookY;

	public JournalScreen() {
		super(Component.translatable("screen.deadlands.journal"));
		DLTheme.playUi(DLSounds.UI_JOURNAL_OPEN, 1.0F, 0.9F);
	}

	@Override
	protected void init() {
		scale = Math.min(1.0F, Math.min((width - 12) / (float) (BOOK_W + 24), (height - 12) / (float) BOOK_H));
		bookX = (width - (BOOK_W + 24) * scale) / 2.0F;
		bookY = (height - BOOK_H * scale) / 2.0F;
		ClientNetworking.requestStatus();
		if (net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(com.deadlands.network.StoryRequestPayload.TYPE)) {
			net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(com.deadlands.network.StoryRequestPayload.INSTANCE);
		}
	}

	/** The story so far arrived (ClientNetworking). */
	public static void story(com.deadlands.network.StoryPayload payload) {
		story = payload;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (DLKeyMappings.JOURNAL.matches(event)) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		DLTheme.playUi(DLSounds.UI_JOURNAL_CLOSE, 1.0F, 0.9F);
		super.onClose();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		float bx = (float) ((event.x() - bookX) / scale);
		float by = (float) ((event.y() - bookY) / scale);
		if (tab == Tab.ARCHIVE && archiveClick(bx, by)) {
			return true;
		}
		if (tab == Tab.STORY && storyClick(bx, by)) {
			return true;
		}
		for (Tab candidate : Tab.values()) {
			int y = TAB_Y[candidate.ordinal()];
			if (bx >= TAB_X && bx < TAB_X + TAB_W + 8 && by >= y && by < y + TAB_H) {
				selectTab(candidate);
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	/** Centre of a bookmark tab in GUI coordinates (0 = log, 1 = threats, 2 = settings). Used by the client game test. */
	public float[] tabCenter(int index) {
		float bx = TAB_X + TAB_W / 2.0F;
		float by = TAB_Y[index] + TAB_H / 2.0F;
		return new float[] {bookX + bx * scale, bookY + by * scale};
	}

	public String currentTabName() {
		return tab.name();
	}

	private void selectTab(Tab next) {
		if (next == Tab.SETTINGS) {
			DLTheme.click();
			minecraft.setScreen(new SettingsScreen(this));
			return;
		}
		if (next != tab) {
			tab = next;
			lastTab = next;
			DLTheme.playUi(DLSounds.UI_PAGE_TURN, 0.95F + (float) Math.random() * 0.1F, 0.8F);
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		float bx = (mouseX - bookX) / scale;
		float by = (mouseY - bookY) / scale;

		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(bookX, bookY);
		pose.scale(scale, scale);

		Tab hovered = null;
		for (Tab candidate : Tab.values()) {
			int y = TAB_Y[candidate.ordinal()];
			boolean hot = bx >= TAB_X && bx < TAB_X + TAB_W + 8 && by >= y && by < y + TAB_H;
			if (hot) {
				hovered = candidate;
			}
			boolean selected = candidate == tab;
			int w = TAB_W + (selected ? 6 : 0) + (hot ? 3 : 0);
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, selected ? DLTheme.TAB_SELECTED : DLTheme.TAB, TAB_X, y, w, TAB_H, candidate.tint);
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, candidate.icon, TAB_X + w - 19, y + 3, 16, 16, ARGB.white(selected || hot ? 1.0F : 0.75F));
		}
		graphics.blit(RenderPipelines.GUI_TEXTURED, BOOK, 0, 0, 0.0F, 0.0F, BOOK_W, BOOK_H, TEX_W, TEX_H, TEX_W, TEX_H);

		Optional<WorldStatusPayload> status = ClientState.status();
		switch (tab) {
			case LOG -> renderLog(graphics, status);
			case THREATS -> renderThreats(graphics, status);
			case TASKS -> renderTasks(graphics);
			case ARCHIVE -> renderArchive(graphics, bx, by);
			case STORY -> renderStory(graphics, bx, by);
			default -> {
			}
		}
		pose.popMatrix();

		if (hovered != null) {
			graphics.setTooltipForNextFrame(font, Component.translatable(hovered.key), mouseX, mouseY);
		}
	}

	// ------------------------------------------------------------------------------------ pages

	private void renderLog(GuiGraphics g, Optional<WorldStatusPayload> status) {
		header(g, Component.translatable("journal.deadlands.log.title"), LEFT_X, PAGE_TOP);
		header(g, Component.translatable("journal.deadlands.chronicle.title"), RIGHT_X, PAGE_TOP);
		if (status.isEmpty()) {
			paragraph(g, Component.translatable("journal.deadlands.no_status"), LEFT_X, PAGE_TOP + 20, DLTheme.INK_SOFT);
			return;
		}
		WorldStatusPayload s = status.get();
		ClientLevel level = minecraft.level;
		long dayTime = level != null ? level.getDayTime() : 0L;

		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(LEFT_X, PAGE_TOP + 17);
		pose.scale(2.0F, 2.0F);
		g.drawString(font, Component.translatable("hud.deadlands.day", s.day()), 0, 0, DLTheme.INK, false);
		pose.popMatrix();

		Component when = Component.translatable("journal.deadlands.log.time", TimeOfDay.clock(dayTime),
			Component.translatable(TimeOfDay.period(dayTime).translationKey()));
		g.drawString(font, when, LEFT_X, PAGE_TOP + 39, DLTheme.INK_SOFT, false);
		String weather = level == null ? "clear" : level.isThundering() ? "storm" : level.isRaining() ? "rain" : "clear";
		g.drawString(font, Component.translatable("journal.deadlands.log.weather", Component.translatable("journal.deadlands.weather." + weather)),
			LEFT_X, PAGE_TOP + 50, DLTheme.INK_SOFT, false);

		g.fill(LEFT_X, PAGE_TOP + 64, LEFT_X + PAGE_W, PAGE_TOP + 65, DLTheme.withAlpha(DLTheme.INK_SOFT, 0.5F));
		int y = PAGE_TOP + 72;
		y = row(g, Component.translatable("journal.deadlands.log.survived"), Component.literal(Long.toString(s.daysSurvived())), y);
		y = row(g, Component.translatable("journal.deadlands.log.deaths"), Component.literal(Integer.toString(s.deaths())), y);
		y = row(g, Component.translatable("journal.deadlands.log.killed"), Component.literal(Integer.toString(s.infectedKilled())), y);
		y = row(g, Component.translatable("journal.deadlands.log.arrived"), Component.translatable("journal.deadlands.day_short", s.firstJoinDay()), y);
		row(g, Component.translatable("journal.deadlands.log.last_death"), s.lastDeathDay() > 0
			? Component.translatable("journal.deadlands.day_short", s.lastDeathDay())
			: Component.translatable("journal.deadlands.log.never"), y);

		// Skills: skillLevel, and how far along the next one is.
		int sy = y + 6;
		g.drawString(font, Component.translatable("journal.deadlands.log.skills"), LEFT_X, sy, DLTheme.INK, false);
		sy += 11;
		com.deadlands.network.SkillsPayload skills = com.deadlands.client.ClientState.skills();
		for (com.deadlands.skill.Skill skill : com.deadlands.skill.Skill.values()) {
			int skillLevel = skills.level(skill);
			g.drawString(font, Component.translatable(skill.translationKey()), LEFT_X, sy, DLTheme.INK_SOFT, false);
			Component value = Component.literal(Integer.toString(skillLevel));
			g.drawString(font, value, LEFT_X + PAGE_W - font.width(value), sy, DLTheme.INK, false);
			if (skillLevel < com.deadlands.skill.Skill.MAX_LEVEL) {
				int from = com.deadlands.skill.Skill.xpFor(skillLevel);
				float progress = (skills.xp(skill) - from) / (float) (com.deadlands.skill.Skill.xpFor(skillLevel + 1) - from);
				int bx = LEFT_X + 62;
				g.fill(bx, sy + 3, bx + 60, sy + 5, DLTheme.withAlpha(DLTheme.INK_SOFT, 0.35F));
				g.fill(bx, sy + 3, bx + Math.round(60 * Math.max(0.0F, Math.min(1.0F, progress))), sy + 5, DLTheme.INK_RED);
			}
			sy += 10;
		}

		if (minecraft.player != null) {
			Component keeper = Component.translatable("journal.deadlands.log.keeper", minecraft.player.getName());
			g.drawString(font, keeper, LEFT_X, PAGE_BOTTOM - 4, DLTheme.INK_SOFT, false);
		}

		int cy = PAGE_TOP + 18;
		List<ChronicleEntry> entries = new ArrayList<>(s.chronicle());
		Collections.reverse(entries);
		if (entries.isEmpty()) {
			paragraph(g, Component.translatable("journal.deadlands.chronicle.empty"), RIGHT_X, cy, DLTheme.INK_SOFT);
			return;
		}
		for (ChronicleEntry entry : entries) {
			List<FormattedCharSequence> lines = font.split(entry.describe(), PAGE_W - 36);
			if (cy + lines.size() * 10 > PAGE_BOTTOM + 4) {
				break;
			}
			g.drawString(font, Component.translatable("journal.deadlands.day_short", entry.day()), RIGHT_X, cy, DLTheme.INK_RED, false);
			for (FormattedCharSequence line : lines) {
				g.drawString(font, line, RIGHT_X + 36, cy, DLTheme.INK, false);
				cy += 10;
			}
			cy += 4;
		}
	}

	/** The QUESTS page: the story's quests, the vehicle being restored and the factions' regard, flowing from the left page to the right. */
	private void renderTasks(GuiGraphics g) {
		com.deadlands.network.JournalPayload journal = ClientState.journal();
		header(g, Component.translatable("journal.deadlands.tasks.title"), LEFT_X, PAGE_TOP);
		int x = LEFT_X;
		int y = PAGE_TOP + 18;
		if (journal.lines().isEmpty()) {
			paragraph(g, Component.translatable("journal.deadlands.tasks.none"), LEFT_X, y, DLTheme.INK_SOFT);
			return;
		}
		for (int i = 0; i < journal.lines().size(); i++) {
			int style = i < journal.styles().size() ? journal.styles().get(i) : 1;
			Component line = journal.lines().get(i);
			List<FormattedCharSequence> wrapped = font.split(line, PAGE_W - (style == 0 ? 0 : 10));
			int needed = wrapped.size() * 10 + (style == 0 ? 6 : 2);
			if (y + needed > PAGE_BOTTOM + 4) {
				if (x == RIGHT_X) {
					break;
				}
				x = RIGHT_X;
				y = PAGE_TOP;
			}
			if (style == 0) {
				y += 2;
			} else {
				String mark = style == 2 ? "\u2714" : style == 1 ? "\u25A1" : "-";
				g.drawString(font, mark, x, y, style == 2 ? DLTheme.INK_SOFT : DLTheme.INK_RED, false);
			}
			int colour = switch (style) {
				case 0 -> DLTheme.INK_RED;
				case 2 -> DLTheme.INK_SOFT;
				case 3 -> DLTheme.INK_SOFT;
				default -> DLTheme.INK;
			};
			for (FormattedCharSequence part : wrapped) {
				g.drawString(font, part, x + (style == 0 ? 0 : 10), y, colour, false);
				y += 10;
			}
			y += style == 0 ? 2 : 2;
		}
	}

	// ------------------------------------------------------------------------------------ archive

	private static final int ARCHIVE_ROW = 14;
	private static final int ENTRY_ROW = 11;

	private List<com.deadlands.story.LoreEntry> archiveEntries(com.deadlands.story.LoreCategory category) {
		java.util.Set<String> read = new java.util.HashSet<>(ClientState.archive().ids());
		List<com.deadlands.story.LoreEntry> out = new ArrayList<>();
		for (com.deadlands.story.LoreEntry entry : com.deadlands.story.Lore.all()) {
			if (entry.category() == category && read.contains(entry.id())) {
				out.add(entry);
			}
		}
		return out;
	}

	private int categoryTotal(com.deadlands.story.LoreCategory category) {
		int total = 0;
		for (com.deadlands.story.LoreEntry entry : com.deadlands.story.Lore.all()) {
			if (entry.category() == category) {
				total++;
			}
		}
		return total;
	}

	/** The ARCHIVE page: shelves on the left with how much of each has been found; the chosen shelf's entries on the right. */
	private void renderArchive(GuiGraphics g, float mx, float my) {
		com.deadlands.story.LoreCategory[] categories = com.deadlands.story.LoreCategory.values();
		archiveCategory = Math.floorMod(archiveCategory, categories.length);
		header(g, Component.translatable("journal.deadlands.archive.title"), LEFT_X, PAGE_TOP);
		int found = ClientState.archive().ids().size();
		g.drawString(font, Component.translatable("journal.deadlands.archive.found", found, com.deadlands.story.Lore.all().size()), LEFT_X, PAGE_TOP + 16,
			DLTheme.INK_SOFT, false);
		int y = PAGE_TOP + 32;
		for (int i = 0; i < categories.length; i++) {
			boolean selected = i == archiveCategory;
			boolean hot = mx >= LEFT_X && mx < LEFT_X + PAGE_W && my >= y - 2 && my < y + ARCHIVE_ROW - 2;
			if (selected || hot) {
				g.fill(LEFT_X - 2, y - 2, LEFT_X + PAGE_W, y + ARCHIVE_ROW - 3, DLTheme.withAlpha(DLTheme.INK_RED, selected ? 0.18F : 0.08F));
			}
			g.drawString(font, Component.translatable(categories[i].translationKey()), LEFT_X, y, selected ? DLTheme.INK_RED : DLTheme.INK, false);
			Component count = Component.literal(archiveEntries(categories[i]).size() + "/" + categoryTotal(categories[i]));
			g.drawString(font, count, LEFT_X + PAGE_W - font.width(count), y, DLTheme.INK_SOFT, false);
			y += ARCHIVE_ROW;
		}
		com.deadlands.story.LoreCategory category = categories[archiveCategory];
		header(g, Component.translatable(category.translationKey()), RIGHT_X, PAGE_TOP);
		List<com.deadlands.story.LoreEntry> entries = archiveEntries(category);
		if (entries.isEmpty()) {
			paragraph(g, Component.translatable("journal.deadlands.archive.empty"), RIGHT_X, PAGE_TOP + 20, DLTheme.INK_SOFT);
			return;
		}
		int rows = (PAGE_BOTTOM - PAGE_TOP - 18) / ENTRY_ROW;
		archiveScroll = net.minecraft.util.Mth.clamp(archiveScroll, 0, Math.max(0, entries.size() - rows));
		int ey = PAGE_TOP + 18;
		for (int i = archiveScroll; i < entries.size() && i < archiveScroll + rows; i++) {
			boolean hot = mx >= RIGHT_X && mx < RIGHT_X + PAGE_W && my >= ey - 1 && my < ey + ENTRY_ROW - 1;
			String title = Component.translatable(com.deadlands.story.Lore.titleKey(entries.get(i).id())).getString();
			g.drawString(font, font.plainSubstrByWidth(title, PAGE_W), RIGHT_X, ey, hot ? DLTheme.INK_RED : DLTheme.INK, false);
			ey += ENTRY_ROW;
		}
		if (entries.size() > rows) {
			Component more = Component.translatable("journal.deadlands.archive.scroll");
			g.drawString(font, more, RIGHT_X + PAGE_W - font.width(more), PAGE_BOTTOM + 2, DLTheme.INK_SOFT, false);
		}
	}

	private boolean archiveClick(float mx, float my) {
		com.deadlands.story.LoreCategory[] categories = com.deadlands.story.LoreCategory.values();
		int y = PAGE_TOP + 32;
		for (int i = 0; i < categories.length; i++) {
			if (mx >= LEFT_X && mx < LEFT_X + PAGE_W && my >= y - 2 && my < y + ARCHIVE_ROW - 2) {
				archiveCategory = i;
				archiveScroll = 0;
				DLTheme.click();
				return true;
			}
			y += ARCHIVE_ROW;
		}
		List<com.deadlands.story.LoreEntry> entries = archiveEntries(categories[Math.floorMod(archiveCategory, categories.length)]);
		int rows = (PAGE_BOTTOM - PAGE_TOP - 18) / ENTRY_ROW;
		int ey = PAGE_TOP + 18;
		for (int i = archiveScroll; i < entries.size() && i < archiveScroll + rows; i++) {
			if (mx >= RIGHT_X && mx < RIGHT_X + PAGE_W && my >= ey - 1 && my < ey + ENTRY_ROW - 1) {
				com.deadlands.network.ArchivePayload archive = ClientState.archive();
				int index = archive.ids().indexOf(entries.get(i).id());
				List<String> args = index >= 0 && index < archive.args().size() ? archive.args().get(index) : List.of();
				DocumentScreen.open(entries.get(i).id(), args);
				return true;
			}
			ey += ENTRY_ROW;
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (tab == Tab.ARCHIVE) {
			archiveScroll = Math.max(0, archiveScroll - (int) Math.signum(scrollY));
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	private void renderThreats(GuiGraphics g, Optional<WorldStatusPayload> status) {
		header(g, Component.translatable("journal.deadlands.threats.title"), LEFT_X, PAGE_TOP);
		header(g, Component.translatable("journal.deadlands.notes.title"), RIGHT_X, PAGE_TOP);

		int ny = PAGE_TOP + 18;
		for (int i = 1; i <= 3; i++) {
			ny = paragraph(g, Component.translatable("journal.deadlands.notes." + i), RIGHT_X, ny, DLTheme.INK) + 6;
		}

		if (status.isEmpty()) {
			paragraph(g, Component.translatable("journal.deadlands.no_status"), LEFT_X, PAGE_TOP + 20, DLTheme.INK_SOFT);
			return;
		}
		WorldStatusPayload s = status.get();
		List<EncounterSchedule.Encounter> upcoming = EncounterSchedule.upcoming(s.day(), 6, s.schedule());
		int y = PAGE_TOP + 20;
		if (upcoming.isEmpty()) {
			y = paragraph(g, Component.translatable("journal.deadlands.threats.none"), LEFT_X, y, DLTheme.INK_SOFT);
		}
		for (EncounterSchedule.Encounter e : upcoming) {
			Component dayLabel = e.day() == s.day()
				? Component.translatable("journal.deadlands.threats.today")
				: Component.translatable("journal.deadlands.day_short", e.day());
			Component what = switch (e.kind()) {
				case HORDE -> Component.translatable("journal.deadlands.threats.horde", e.hordeSize());
				case BOSS -> e.tier() > 0
					? Component.translatable("journal.deadlands.threats.boss_tier", e.boss().displayName(), e.tier() + 1)
					: Component.translatable("journal.deadlands.threats.boss", e.boss().displayName());
				case NONE -> Component.empty();
			};
			List<FormattedCharSequence> lines = font.split(what, PAGE_W - 40);
			if (y + lines.size() * 10 > PAGE_BOTTOM - 28) {
				break;
			}
			g.drawString(font, dayLabel, LEFT_X, y, DLTheme.INK_RED, false);
			for (FormattedCharSequence line : lines) {
				g.drawString(font, line, LEFT_X + 40, y, DLTheme.INK, false);
				y += 10;
			}
			y += 3;
		}

		// Honest development note: the schedule is real, the encounters are not built yet.
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(LEFT_X, PAGE_BOTTOM - 22);
		pose.scale(0.75F, 0.75F);
		int ly = 0;
		for (FormattedCharSequence line : font.split(Component.translatable("journal.deadlands.threats.dev_note"), (int) (PAGE_W / 0.75F))) {
			g.drawString(font, line, 0, ly, DLTheme.INK_SOFT, false);
			ly += 10;
		}
		pose.popMatrix();
	}

	// ------------------------------------------------------------------------------------ story

	/** Left: the chapters and where each stands, and the ending. Right: the Cinematic Archive, seen ones to watch again. */
	private void renderStory(GuiGraphics g, float mx, float my) {
		header(g, Component.translatable("story.deadlands.archive.header"), LEFT_X, PAGE_TOP);
		header(g, Component.translatable("story.deadlands.cinematics.header"), RIGHT_X, PAGE_TOP);
		if (story == null) {
			paragraph(g, Component.translatable("journal.deadlands.no_status"), LEFT_X, PAGE_TOP + 20, DLTheme.INK_SOFT);
			return;
		}
		int y = PAGE_TOP + 18;
		for (String entry : story.chapters()) {
			String[] kv = entry.split("=", 2);
			String status = kv.length > 1 ? kv[1] : "locked";
			int colour = status.equals("done") ? 0xFF2E5A2A : status.equals("active") ? DLTheme.INK_RED : DLTheme.INK_SOFT;
			y = paragraph(g, Component.translatable("story.deadlands.chapter." + kv[0]), LEFT_X, y, status.equals("locked") ? DLTheme.INK_SOFT : DLTheme.INK);
			Component state = Component.translatable("story.deadlands.status." + status);
			g.drawString(font, state, LEFT_X + PAGE_W - font.width(state), y, colour, false);
			y += 13;
		}
		y += 4;
		paragraph(g, story.ending().isEmpty() ? Component.translatable("story.deadlands.archive.no_ending")
			: Component.translatable("story.deadlands.archive.ending", Component.translatable("ending.deadlands.name." + story.ending())), LEFT_X, y, DLTheme.INK_RED);
		int cy = PAGE_TOP + 18;
		int seen = 0;
		for (String entry : story.cinematics()) {
			String[] kv = entry.split("=", 2);
			boolean watched = kv.length > 1 && kv[1].equals("seen");
			if (watched) {
				seen++;
				boolean hot = mx >= RIGHT_X && mx < RIGHT_X + PAGE_W && my >= cy - 2 && my < cy + STORY_ROW - 2;
				if (hot) {
					g.fill(RIGHT_X - 2, cy - 2, RIGHT_X + PAGE_W, cy + STORY_ROW - 3, DLTheme.withAlpha(DLTheme.INK_RED, 0.12F));
				}
				g.drawString(font, Component.translatable("cinematic.deadlands." + kv[0]), RIGHT_X, cy, DLTheme.INK, false);
				Component watch = Component.translatable("story.deadlands.cinematics.watch");
				g.drawString(font, watch, RIGHT_X + PAGE_W - font.width(watch), cy, hot ? DLTheme.INK_RED : DLTheme.INK_SOFT, false);
			} else {
				g.drawString(font, Component.translatable("story.deadlands.cinematics.unseen"), RIGHT_X, cy, DLTheme.withAlpha(DLTheme.INK_SOFT, 0.6F), false);
			}
			cy += STORY_ROW;
		}
		paragraph(g, Component.translatable("story.deadlands.cinematics.count", seen, story.cinematics().size()), RIGHT_X, cy + 6, DLTheme.INK_SOFT);
	}

	/** A click on a seen cinematic: close the journal and watch it ({@code /deadlands watch}). */
	private boolean storyClick(float mx, float my) {
		if (story == null || mx < RIGHT_X || mx >= RIGHT_X + PAGE_W) {
			return false;
		}
		int cy = PAGE_TOP + 18;
		for (String entry : story.cinematics()) {
			String[] kv = entry.split("=", 2);
			if (my >= cy - 2 && my < cy + STORY_ROW - 2) {
				if (kv.length > 1 && kv[1].equals("seen") && minecraft != null && minecraft.player != null) {
					onClose();
					minecraft.player.connection.sendCommand("deadlands watch " + kv[0]);
					return true;
				}
				return false;
			}
			cy += STORY_ROW;
		}
		return false;
	}

	// ------------------------------------------------------------------------------------ helpers

	private void header(GuiGraphics g, Component text, int x, int y) {
		g.drawString(font, text, x, y, DLTheme.INK_RED, false);
		g.fill(x, y + 10, x + PAGE_W, y + 11, DLTheme.withAlpha(DLTheme.INK_RED, 0.55F));
	}

	private int row(GuiGraphics g, Component label, Component value, int y) {
		g.drawString(font, label, LEFT_X, y, DLTheme.INK_SOFT, false);
		g.drawString(font, value, LEFT_X + PAGE_W - font.width(value), y, DLTheme.INK, false);
		return y + 13;
	}

	private int paragraph(GuiGraphics g, Component text, int x, int y, int color) {
		for (FormattedCharSequence line : font.split(text, PAGE_W)) {
			if (y > PAGE_BOTTOM) {
				break;
			}
			g.drawString(font, line, x, y, color, false);
			y += 10;
		}
		return y;
	}
}
