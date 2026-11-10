package com.deadlands.client.ui;

import com.deadlands.client.ui.widget.DLButton;
import com.deadlands.network.DialogueChoicePayload;
import com.deadlands.network.DialoguePayload;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * A conversation with a survivor: their name, who they are and how their people regard you, what
 * they just said, and the replies on offer as buttons. Choosing a reply sends it to the server,
 * which answers with new text (and the screen is rebuilt), opens the trading screen, or, for
 * leaving, nothing. The game is not paused: the world carries on while you talk.
 *
 * <p>The panel always fits the window: the text takes only the lines it needs (up to six), the
 * replies get shorter buttons on small windows, and only if even those would not fit do they take
 * two columns in a wider panel.
 */
public class DialogueScreen extends Screen {
	private static final int W = 300;
	private static final int WIDE = 420;
	private static final int MAX_LINES = 6;

	private record Layout(int top, int width, int textHeight, int columns, int buttonHeight, int height) {
	}

	private DialoguePayload dialogue;

	public DialogueScreen(DialoguePayload dialogue) {
		super(dialogue.title());
		this.dialogue = dialogue;
	}

	/** Show a conversation: open the screen, or update it if it is already open for the same survivor. */
	public static void show(DialoguePayload payload) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.screen instanceof DialogueScreen open && open.dialogue.entityId() == payload.entityId()) {
			open.dialogue = payload;
			open.rebuildWidgets();
		} else {
			minecraft.setScreen(new DialogueScreen(payload));
		}
	}

	private Layout layout() {
		int options = dialogue.options().size();
		int available = height - 24;
		int[][] tries = {{1, 20}, {1, 18}, {1, 16}, {1, 14}, {2, 16}};
		for (int[] attempt : tries) {
			int columns = attempt[0];
			int buttonHeight = attempt[1];
			int panel = columns == 1 ? W : Math.max(W, Math.min(WIDE, width - 24));
			int lines = Math.max(2, Math.min(MAX_LINES, font.split(dialogue.text(), panel - 16).size()));
			int rows = (options + columns - 1) / columns;
			int total = 30 + lines * 10 + 8 + rows * (buttonHeight + 2) + 4;
			if (total <= available || attempt == tries[tries.length - 1]) {
				// Low on the screen, so the survivor stays in view above it.
				return new Layout(Math.max(12, height - 14 - total), panel, lines * 10, columns, buttonHeight, total);
			}
		}
		throw new IllegalStateException("unreachable");
	}

	@Override
	protected void init() {
		Layout layout = layout();
		int x = (width - layout.width()) / 2;
		int top = layout.top() + 30 + layout.textHeight() + 8;
		int gap = 4;
		int columnWidth = (layout.width() - 40 - gap * (layout.columns() - 1)) / layout.columns();
		List<String> options = dialogue.options();
		for (int i = 0; i < options.size(); i++) {
			String option = options.get(i);
			int column = i % layout.columns();
			int row = i / layout.columns();
			addRenderableWidget(new DLButton(x + 20 + column * (columnWidth + gap), top + row * (layout.buttonHeight() + 2), columnWidth, layout.buttonHeight(),
				Component.translatable("npc.deadlands.option." + option), button -> choose(option)));
		}
	}

	private void choose(String option) {
		if (option.equals("leave")) {
			onClose();
			return;
		}
		if (ClientPlayNetworking.canSend(DialogueChoicePayload.TYPE)) {
			ClientPlayNetworking.send(new DialogueChoicePayload(dialogue.entityId(), option));
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		Layout layout = layout();
		int panel = layout.width();
		int x = (width - panel) / 2;
		int y = layout.top();
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, DLTheme.PANEL, x - 6, y - 8, panel + 12, layout.height() + 12);
		graphics.drawString(font, dialogue.title(), x + 8, y, DLTheme.ACCENT, true);
		graphics.drawString(font, dialogue.subtitle(), x + 8, y + 12, DLTheme.TEXT_DIM, true);
		graphics.fill(x + 8, y + 24, x + panel - 8, y + 25, 0x66000000);
		int ty = y + 30;
		List<FormattedCharSequence> lines = font.split(dialogue.text(), panel - 16);
		for (int i = 0; i < lines.size() && i < layout.textHeight() / 10; i++) {
			graphics.drawString(font, lines.get(i), x + 8, ty, DLTheme.TEXT, true);
			ty += 10;
		}
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		// No blur or dimming: the survivor stays in view while they talk.
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
