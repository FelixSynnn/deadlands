package com.deadlands.client.ui;

import com.deadlands.client.ClientCharacters;
import com.deadlands.client.render.NpcRenderer;
import com.deadlands.client.ui.widget.DLButton;
import com.deadlands.network.CharacterPickPayload;
import com.deadlands.npc.NpcCharacter;
import com.deadlands.registry.DLSounds;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;

/**
 * Phase 6: choosing who you are, shown after the opening (and on joining, for a survivor who never chose).
 *
 * <p>Black, quiet, cinematic: the title types in, then three tall cards rise one after another. Each holds the
 * character standing in their own skin (a full-body front view, breathing, lit from below like a campfire), their name,
 * what they were before, and a few lines about them. Hovering lifts a card and lights its edge; clicking picks it and
 * the confirm bar fills in at the bottom. A character another survivor already is shows dimmed, stamped TAKEN with that
 * player's name, and can't be picked; the cards update live as others choose. "Decide later" closes it
 * ({@code /deadlands character} picks then). Arrow keys and Enter work too.
 */
public class CharacterSelectScreen extends Screen {
	private static final List<NpcCharacter> CHOICES = List.of(NpcCharacter.ITZBREADLOAF, NpcCharacter.IMLILYLOL, NpcCharacter.FELIXSYN, NpcCharacter.HARLEY);
	private static final int GAP = 10;
	private static final int EDGE = 0xFF2B2722;
	private static final int CARD = 0xF0121110;
	private static final int CARD_HOT = 0xF01A1714;

	private final boolean afterIntro;
	private final float[] lift = new float[CHOICES.size()];
	private int ticks;
	private int selected = -1;
	private int focused = -1;
	private boolean sent;
	private DLButton confirm;

	public CharacterSelectScreen(boolean afterIntro) {
		super(Component.translatable("select.deadlands.title"));
		this.afterIntro = afterIntro;
	}

	@Override
	protected void init() {
		int bottom = height - 28;
		confirm = addRenderableWidget(new DLButton(width / 2 - 75, bottom, 150, 20, Component.translatable("select.deadlands.confirm"), b -> confirm()));
		addRenderableWidget(new DLButton(width - 96, bottom, 86, 20, Component.translatable("select.deadlands.later"), b -> later()));
		updateConfirm();
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false; // a choice (or "decide later"), not an accident
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void tick() {
		ticks++;
		UUID me = Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getUUID();
		if (sent && me != null && ClientCharacters.of(me) != null) {
			DLTheme.playUi(DLSounds.UI_CLICK, 0.7F, 0.9F);
			onClose(); // the server took it
			return;
		}
		if (selected >= 0 && holder(CHOICES.get(selected)) != null) {
			selected = -1; // someone else got there first
			sent = false;
		}
		updateConfirm();
	}

	private void updateConfirm() {
		if (confirm != null) {
			confirm.active = selected >= 0 && !sent;
			confirm.setMessage(selected < 0 ? Component.translatable("select.deadlands.confirm")
				: Component.translatable("select.deadlands.confirm_as", CHOICES.get(selected).displayName()));
		}
	}

	private void confirm() {
		if (selected < 0 || sent) {
			return;
		}
		sent = true;
		DLTheme.click();
		if (ClientPlayNetworking.canSend(CharacterPickPayload.TYPE)) {
			ClientPlayNetworking.send(new CharacterPickPayload(CHOICES.get(selected).id(), afterIntro));
		}
		updateConfirm();
	}

	private void later() {
		DLTheme.click();
		if (ClientPlayNetworking.canSend(CharacterPickPayload.TYPE)) {
			ClientPlayNetworking.send(new CharacterPickPayload("", afterIntro));
		}
		onClose();
	}

	/** The name of the other survivor who is this character, or null if it's free. */
	private static @Nullable String holder(NpcCharacter character) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.getConnection() == null || minecraft.player == null) {
			return null;
		}
		for (PlayerInfo info : minecraft.getConnection().getOnlinePlayers()) {
			UUID id = info.getProfile().id();
			if (!id.equals(minecraft.player.getUUID()) && ClientCharacters.of(id) == character) {
				return info.getProfile().name();
			}
		}
		return null;
	}

	/** Four cards across: as wide as fits, up to 118. */
	private int cardW() {
		return Mth.clamp((width - 16 - GAP * (CHOICES.size() - 1)) / CHOICES.size(), 80, 118);
	}

	private int cardX(int i) {
		int n = CHOICES.size();
		int total = cardW() * n + GAP * (n - 1);
		return (width - total) / 2 + i * (cardW() + GAP);
	}

	/** Below the title and subtitle. */
	private int cardY() {
		return 52;
	}

	/** As tall as fits between the title and the buttons. */
	private int cardH() {
		return Mth.clamp(height - cardY() - 40, 140, 230);
	}

	private int cardAt(double mx, double my) {
		for (int i = 0; i < CHOICES.size(); i++) {
			int x = cardX(i);
			int y = cardY() - Math.round(lift[i]);
			if (mx >= x && mx < x + cardW() && my >= y && my < y + cardH()) {
				return i;
			}
		}
		return -1;
	}

	private void pick(int i) {
		if (i < 0 || sent || holder(CHOICES.get(i)) != null) {
			return;
		}
		if (selected != i) {
			selected = i;
			DLTheme.playUi(DLSounds.UI_CLICK, 1.1F, 0.8F);
		}
		updateConfirm();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int i = cardAt(event.x(), event.y());
		if (i >= 0) {
			pick(i);
			if (doubleClick && selected == i) {
				confirm();
			}
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (key == 263 || key == 262) { // left, right
			int start = selected >= 0 ? selected : focused >= 0 ? focused : 1;
			for (int step = 1; step <= CHOICES.size(); step++) {
				int next = Math.floorMod(start + (key == 262 ? step : -step), CHOICES.size());
				if (holder(CHOICES.get(next)) == null) {
					focused = next;
					pick(next);
					break;
				}
			}
			return true;
		}
		if ((key == 257 || key == 335) && selected >= 0) { // enter
			confirm();
			return true;
		}
		return super.keyPressed(event);
	}

	// ======================================================================================== drawing

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, 0xFF050505);
		// A dim warm glow low in the middle, as if from a fire off screen, and a vignette.
		for (int band = 0; band < 12; band++) {
			int inset = band * Math.max(4, height / 40);
			graphics.fill(inset, height / 3 + inset / 2, width - inset, height - inset / 3, DLTheme.withAlpha(0xFF3A1E0C, 0.035F));
		}
		graphics.fill(0, height - 36, width, height, 0x88000000);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick); // the backdrop and the buttons
		float t = ticks + partialTick;
		Font font = this.font;
		header(graphics, font, t);
		int hover = sent ? -1 : cardAt(mouseX, mouseY);
		for (int i = 0; i < CHOICES.size(); i++) {
			boolean taken = holder(CHOICES.get(i)) != null;
			float target = taken ? 0.0F : i == selected ? 7.0F : i == hover ? 4.0F : 0.0F;
			lift[i] += (target - lift[i]) * Math.min(1.0F, 0.35F * Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaTicks() + 0.05F);
			card(graphics, font, i, t, i == hover && !taken, taken);
		}
		footer(graphics, font, t);
		// Fade in from black.
		float fade = 1.0F - Mth.clamp(t / 20.0F, 0.0F, 1.0F);
		if (fade > 0.0F) {
			graphics.fill(0, 0, width, height, DLTheme.withAlpha(0xFF000000, fade));
		}
	}

	private void header(GuiGraphics graphics, Font font, float t) {
		String title = Component.translatable("select.deadlands.title").getString();
		int shown = Mth.clamp((int) ((t - 6.0F) / 1.6F), 0, title.length());
		String typed = title.substring(0, shown);
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(width / 2.0F, 10.0F);
		pose.scale(2.0F, 2.0F);
		int w = font.width(title);
		graphics.drawString(font, typed, -w / 2, 0, DLTheme.TEXT, false);
		if (shown < title.length() && (ticks / 6) % 2 == 0) {
			graphics.fill(-w / 2 + font.width(typed) + 1, 0, -w / 2 + font.width(typed) + 3, 8, DLTheme.ACCENT);
		}
		pose.popMatrix();
		float sub = Mth.clamp((t - 6.0F - title.length() * 1.6F) / 14.0F, 0.0F, 1.0F);
		if (sub > 0.02F) {
			Component line = Component.translatable("select.deadlands.subtitle");
			graphics.drawCenteredString(font, line, width / 2, 31, DLTheme.withAlpha(DLTheme.TEXT_DIM, sub));
		}
	}

	private void footer(GuiGraphics graphics, Font font, float t) {
		Component hint = Component.translatable(sent ? "select.deadlands.waiting" : "select.deadlands.hint");
		if (width - 2 * 90 > font.width(hint) + 170) { // only where it has room beside the buttons
			graphics.drawString(font, hint, 10, height - 22, DLTheme.TEXT_FAINT, false);
		}
	}

	private void card(GuiGraphics graphics, Font font, int i, float t, boolean hot, boolean taken) {
		NpcCharacter character = CHOICES.get(i);
		// Each card rises into place a beat after the one before.
		float rise = Mth.clamp((t - 18.0F - i * 6.0F) / 12.0F, 0.0F, 1.0F);
		rise = 1.0F - (1.0F - rise) * (1.0F - rise) * (1.0F - rise);
		if (rise <= 0.0F) {
			return;
		}
		boolean chosen = i == selected;
		int x = cardX(i);
		int cw = cardW();
		int y = cardY() - Math.round(lift[i]) + Math.round((1.0F - rise) * 24.0F);
		int h = cardH();
		float a = rise * (taken ? 0.55F : 1.0F);

		// The card: a dark plate, a thin frame, an accent edge when hovered or chosen.
		int edge = chosen ? DLTheme.ACCENT : hot ? DLTheme.withAlpha(DLTheme.ACCENT_LIGHT, 0.6F) : EDGE;
		graphics.fill(x - 1, y - 1, x + cw + 1, y + h + 1, DLTheme.withAlpha(edge, a));
		graphics.fill(x, y, x + cw, y + h, DLTheme.withAlpha(hot || chosen ? CARD_HOT : CARD, a));
		if (chosen) {
			graphics.fill(x, y, x + cw, y + 2, DLTheme.withAlpha(DLTheme.ACCENT, a));
		}

		// The figure, standing in a pool of warm light.
		int stage = y + 8;
		int stageH = Math.round(h * 0.46F);
		for (int band = 0; band < 6; band++) {
			int inset = band * 7;
			graphics.fill(x + 6 + inset, stage + stageH - 26 + band * 3, x + cw - 6 - inset, stage + stageH + 2, DLTheme.withAlpha(0xFF4A2810, 0.07F * a));
		}
		graphics.fill(x + cw / 2 - 22, stage + stageH - 2, x + cw / 2 + 22, stage + stageH + 1, DLTheme.withAlpha(0xFF000000, 0.5F * a));
		float breath = Mth.sin((t + i * 13.0F) * 0.08F) * 0.6F;
		figure(graphics, character, x + cw / 2, stage + stageH - 2, (stageH - 8) / 32.0F, breath, a * (taken ? 0.6F : 1.0F));

		// Name, what they were, a few lines about them.
		int ty = stage + stageH + 7;
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x + cw / 2.0F, ty);
		pose.scale(1.25F, 1.25F);
		String name = character.displayName();
		graphics.drawString(font, name, -font.width(name) / 2, 0, DLTheme.withAlpha(chosen ? DLTheme.ACCENT_LIGHT : DLTheme.TEXT, a), false);
		pose.popMatrix();
		Component role = Component.translatable("select.deadlands." + character.id() + ".role");
		graphics.drawCenteredString(font, role, x + cw / 2, ty + 13, DLTheme.withAlpha(DLTheme.ACCENT, a));
		graphics.fill(x + 16, ty + 24, x + cw - 16, ty + 25, DLTheme.withAlpha(EDGE, a));
		List<FormattedCharSequence> lines = font.split(Component.translatable("select.deadlands." + character.id() + ".about"), cw - 14);
		int ly = ty + 29;
		int room = Math.max(0, (y + h - 5 - ly) / 9); // only the lines that fit
		for (FormattedCharSequence line : lines.subList(0, Math.min(room, lines.size()))) {
			graphics.drawString(font, line, x + 7, ly, DLTheme.withAlpha(DLTheme.TEXT_DIM, a), false);
			ly += 9;
		}

		// Someone else already is them.
		if (taken) {
			String who = holder(character);
			graphics.fill(x, y + h / 3 - 4, x + cw, y + h / 3 + 24, DLTheme.withAlpha(0xFF000000, 0.75F * rise));
			Component stamp = Component.translatable("select.deadlands.taken");
			pose.pushMatrix();
			pose.translate(x + cw / 2.0F, y + h / 3);
			pose.scale(1.5F, 1.5F);
			graphics.drawString(font, stamp, -font.width(stamp) / 2, 0, DLTheme.withAlpha(DLTheme.DANGER, rise), false);
			pose.popMatrix();
			graphics.drawCenteredString(font, Component.translatable("select.deadlands.taken_by", who == null ? "" : who), x + cw / 2, y + h / 3 + 13,
				DLTheme.withAlpha(DLTheme.TEXT_DIM, rise));
		}
	}

	/**
	 * The character's skin drawn as a figure, front view: legs, body, arms, head, each with its outer layer. {@code cx} is
	 * the centre and {@code feet} the bottom, in screen pixels; one skin pixel is {@code s} screen pixels.
	 */
	private static void figure(GuiGraphics graphics, NpcCharacter character, int cx, int feet, float s, float breath, float alpha) {
		Identifier skin = NpcRenderer.characterTexture(character);
		boolean slim = NpcRenderer.characterSlim(character);
		int arm = slim ? 3 : 4;
		int colour = DLTheme.withAlpha(0xFFFFFFFF, alpha);
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(cx, feet);
		pose.scale(s, s);
		// In skin pixels now, origin at the feet, the figure 32 tall and 8 wide at the body.
		part(graphics, skin, -4, -12, 4, 20, 4, 12, colour);            // right leg (viewer's left)
		part(graphics, skin, -4, -12, 4, 36, 4, 12, colour);
		part(graphics, skin, 0, -12, 20, 52, 4, 12, colour);            // left leg
		part(graphics, skin, 0, -12, 4, 52, 4, 12, colour);
		pose.translate(0.0F, -breath * 0.35F);
		part(graphics, skin, -4, -24, 20, 20, 8, 12, colour);           // body
		part(graphics, skin, -4, -24, 20, 36, 8, 12, colour);
		part(graphics, skin, -4 - arm, -24, 44, 20, arm, 12, colour);   // right arm
		part(graphics, skin, -4 - arm, -24, 44, 36, arm, 12, colour);
		part(graphics, skin, 4, -24, 36, 52, arm, 12, colour);          // left arm
		part(graphics, skin, 4, -24, 52, 52, arm, 12, colour);
		pose.translate(0.0F, -breath * 0.25F);
		part(graphics, skin, -4, -32, 8, 8, 8, 8, colour);              // head
		part(graphics, skin, -4, -32, 40, 8, 8, 8, colour);
		pose.popMatrix();
	}

	private static void part(GuiGraphics graphics, Identifier skin, int x, int y, int u, int v, int w, int h, int colour) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, skin, x, y, (float) u, (float) v, w, h, w, h, 64, 64, colour);
	}
}
