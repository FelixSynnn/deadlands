package com.deadlands.client.achievement;

import com.deadlands.client.ui.DLTheme;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * The DEADLANDS achievement toast: a dark worn strip with a thin accent rule, a small "ACHIEVEMENT"
 * line and the achievement's name beside its icon. Timing comes from vanilla's toast (it is a
 * subclass); only the look is ours. Toasts are only ever shown to the one who earned it.
 */
public class DLAchievementToast extends AdvancementToast {
	private final AdvancementHolder holder;

	public DLAchievementToast(AdvancementHolder holder) {
		super(holder);
		this.holder = holder;
	}

	@Override
	public void render(GuiGraphics graphics, Font font, long timeSinceLastVisible) {
		int width = width();
		int height = height();
		graphics.fill(0, 0, width, height, 0xE0141210);
		graphics.fill(0, 0, 2, height, DLTheme.ACCENT);
		graphics.fill(0, height - 1, width, height, 0x66000000);
		DisplayInfo display = holder.value().display().orElse(null);
		if (display == null) {
			return;
		}
		graphics.renderFakeItem(display.getIcon(), 8, 8);
		graphics.drawString(font, Component.translatable("achievement.deadlands.toast"), 30, 6, DLTheme.TEXT_FAINT, false);
		FormattedCharSequence title = font.split(display.getTitle(), width - 36).stream().findFirst().orElse(FormattedCharSequence.EMPTY);
		graphics.drawString(font, title, 30, 18, DLTheme.TEXT, false);
	}
}
