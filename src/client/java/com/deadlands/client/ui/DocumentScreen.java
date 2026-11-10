package com.deadlands.client.ui;

import com.deadlands.story.Lore;
import com.deadlands.story.LoreEntry;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * Reading a piece of lore: a sheet of paper (yellowed, a little dirty) with the title, what kind of
 * document it is, and the text, scrolled with the wheel when it is long. Opened by using a document
 * or from the journal's archive; closing returns to wherever it was opened from.
 */
public class DocumentScreen extends Screen {
	private static final int W = 260;
	private static final int PAPER = 0xFFE6DCC2;
	private static final int PAPER_EDGE = 0xFFC9BC9A;

	private final LoreEntry entry;
	private final Component body;
	private final @Nullable Screen parent;
	private List<FormattedCharSequence> lines = List.of();
	private int scroll;

	public DocumentScreen(LoreEntry entry, List<String> args, @Nullable Screen parent) {
		super(Component.translatable(Lore.titleKey(entry.id())));
		this.entry = entry;
		this.body = Component.translatable(Lore.bodyKey(entry.id()), args.toArray());
		this.parent = parent;
	}

	public static void open(String id, List<String> args) {
		LoreEntry entry = Lore.get(id);
		if (entry != null) {
			Minecraft minecraft = Minecraft.getInstance();
			minecraft.setScreen(new DocumentScreen(entry, args, minecraft.screen));
			minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
		}
	}

	@Override
	protected void init() {
		lines = font.split(body, W - 32);
	}

	private int visibleLines() {
		return Math.max(4, (height - 110) / 10);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		scroll = Mth.clamp(scroll - (int) Math.signum(scrollY) * 2, 0, Math.max(0, lines.size() - visibleLines()));
		return true;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		int shown = Math.min(lines.size(), visibleLines());
		int h = 46 + shown * 10 + 16;
		int x = (width - W) / 2;
		int y = Math.max(10, (height - h) / 2);
		graphics.fill(x - 2, y - 2, x + W + 2, y + h + 2, PAPER_EDGE);
		graphics.fill(x, y, x + W, y + h, PAPER);
		// A coffee ring and a fold, so it is not too clean.
		graphics.fill(x, y + h / 2, x + W, y + h / 2 + 1, DLTheme.withAlpha(PAPER_EDGE, 0.7F));
		graphics.drawString(font, title, x + 16, y + 12, DLTheme.INK_RED, false);
		Component kind = Component.translatable(entry.category().translationKey());
		graphics.drawString(font, kind, x + 16, y + 24, DLTheme.INK_SOFT, false);
		if (entry.part() > 0) {
			Component part = Component.translatable("lore.deadlands.part", entry.part(), Lore.series(entry.series()).size());
			graphics.drawString(font, part, x + W - 16 - font.width(part), y + 24, DLTheme.INK_SOFT, false);
		}
		graphics.fill(x + 16, y + 35, x + W - 16, y + 36, DLTheme.withAlpha(DLTheme.INK_RED, 0.5F));
		int ty = y + 44;
		for (int i = scroll; i < scroll + shown && i < lines.size(); i++) {
			graphics.drawString(font, lines.get(i), x + 16, ty, DLTheme.INK, false);
			ty += 10;
		}
		if (lines.size() > shown) {
			Component more = Component.translatable("lore.deadlands.scroll", scroll + 1, lines.size() - shown + 1);
			graphics.drawString(font, more, x + W - 16 - font.width(more), y + h - 12, DLTheme.INK_SOFT, false);
		}
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
