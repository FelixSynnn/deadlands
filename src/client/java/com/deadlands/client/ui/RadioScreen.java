package com.deadlands.client.ui;

import com.deadlands.client.ui.widget.DLButton;
import com.deadlands.network.RadioPayload;
import com.deadlands.network.RadioTunePayload;
import com.deadlands.registry.DLSounds;
import com.deadlands.story.Radio;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * The radio. A dial across the band with the five stations marked, a needle that slides to the one
 * chosen, a signal meter, and what the station is carrying, typed out as it arrives. A weak signal
 * garbles the words and turns up the static (a loop that plays while the radio is open). It asks the
 * server again every ten seconds, so broadcasts change while you listen.
 */
public class RadioScreen extends Screen {
	private static final int W = 300;
	private static final int H = 200;
	private static final int REFRESH_TICKS = 200;
	private static int lastStation;

	private int station = lastStation;
	private float needle = lastStation;
	private @Nullable RadioPayload received;
	private int typed;
	private int ticks;
	private Static noise;

	public RadioScreen() {
		super(Component.translatable("screen.deadlands.radio"));
	}

	/** A transmission arrived: show it if it is for the station tuned. */
	public static void receive(RadioPayload payload) {
		if (Minecraft.getInstance().screen instanceof RadioScreen radio && payload.station() == radio.station) {
			radio.received = payload;
			radio.typed = 0;
		}
	}

	@Override
	protected void init() {
		int x = (width - W) / 2;
		int y = (height - H) / 2;
		for (int i = 0; i < Radio.STATIONS.length; i++) {
			int index = i;
			addRenderableWidget(new DLButton(x + 14 + i * 56, y + H - 30, 50, 18, Component.literal(Radio.STATIONS[i]), button -> tune(index)));
		}
		if (noise == null) {
			noise = new Static();
			minecraft.getSoundManager().play(noise);
		}
		request();
	}

	private void tune(int next) {
		if (next == station) {
			return;
		}
		station = next;
		lastStation = next;
		received = null;
		typed = 0;
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(DLSounds.RADIO_TUNE.value(), 1.0F, 0.6F));
		request();
	}

	private void request() {
		if (ClientPlayNetworking.canSend(RadioTunePayload.TYPE)) {
			ClientPlayNetworking.send(new RadioTunePayload(station));
		}
	}

	@Override
	public void tick() {
		ticks++;
		needle += (station - needle) * 0.25F;
		typed += 3;
		if (ticks % REFRESH_TICKS == 0) {
			request();
		}
	}

	/** Weak signal: some letters become noise, different ones each moment. */
	private String garble(String text, int signal) {
		if (signal >= 85) {
			return text;
		}
		RandomSource random = RandomSource.create(text.hashCode() * 31L + ticks / 6);
		StringBuilder out = new StringBuilder(text.length());
		float loss = (85 - signal) / 110.0F;
		for (char c : text.toCharArray()) {
			out.append(c != ' ' && random.nextFloat() < loss ? (random.nextBoolean() ? '.' : '-') : c);
		}
		return out.toString();
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		int x = (width - W) / 2;
		int y = (height - H) / 2;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, DLTheme.PANEL, x - 6, y - 8, W + 12, H + 12);
		graphics.drawString(font, title, x + 8, y, DLTheme.ACCENT, true);
		// The dial.
		int dialY = y + 18;
		graphics.fill(x + 10, dialY, x + W - 10, dialY + 22, 0xFF1A1714);
		for (int i = 0; i <= 40; i++) {
			int tx = x + 14 + i * (W - 28) / 40;
			graphics.fill(tx, dialY + (i % 5 == 0 ? 12 : 16), tx + 1, dialY + 20, DLTheme.TEXT_FAINT);
		}
		for (int i = 0; i < Radio.STATIONS.length; i++) {
			int sx = x + 14 + i * (W - 28) / (Radio.STATIONS.length - 1);
			graphics.drawCenteredString(font, Radio.STATIONS[i], sx, dialY + 2, i == station ? DLTheme.ACCENT_LIGHT : DLTheme.TEXT_DIM);
		}
		int nx = x + 14 + Math.round(needle * (W - 28) / (Radio.STATIONS.length - 1));
		graphics.fill(nx, dialY - 2, nx + 1, dialY + 24, DLTheme.DANGER);
		// Signal.
		int signal = received == null ? 0 : received.signal();
		graphics.drawString(font, Component.translatable("screen.deadlands.radio.signal"), x + 10, dialY + 28, DLTheme.TEXT_DIM, true);
		for (int i = 0; i < 10; i++) {
			int bx = x + 60 + i * 8;
			graphics.fill(bx, dialY + 34 - i / 2, bx + 6, dialY + 36, i * 10 < signal ? DLTheme.ACCENT : 0x55000000);
		}
		// What is coming through.
		int ty = dialY + 46;
		graphics.fill(x + 10, ty - 4, x + W - 10, y + H - 36, 0x66000000);
		if (received == null) {
			graphics.drawString(font, garble(Component.translatable("radio.deadlands.static").getString(), 20), x + 14, ty, DLTheme.TEXT_FAINT, false);
		} else {
			int budget = typed;
			for (Component line : received.lines()) {
				String text = garble(line.getString(), signal);
				String shown = text.substring(0, Math.min(text.length(), Math.max(0, budget)));
				budget -= text.length();
				List<FormattedCharSequence> wrapped = font.split(Component.literal(shown), W - 28);
				for (FormattedCharSequence part : wrapped) {
					if (ty > y + H - 46) {
						break;
					}
					graphics.drawString(font, part, x + 14, ty, line == received.lines().getFirst() ? DLTheme.ACCENT_LIGHT : DLTheme.TEXT, false);
					ty += 10;
				}
				ty += 3;
				if (budget <= 0) {
					break;
				}
			}
		}
		if (noise != null) {
			noise.target = received == null ? 0.6F : (100 - signal) / 140.0F + 0.08F;
		}
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public void removed() {
		if (noise != null) {
			noise.done = true;
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** The static: loops while the radio is open, louder the weaker the signal. */
	private static final class Static extends AbstractTickableSoundInstance {
		float target = 0.5F;
		boolean done;

		Static() {
			super(DLSounds.RADIO_STATIC.value(), SoundSource.PLAYERS, RandomSource.create());
			this.looping = true;
			this.relative = true;
			this.volume = 0.3F;
		}

		@Override
		public void tick() {
			if (done) {
				stop();
				return;
			}
			volume += (target - volume) * 0.2F;
		}
	}
}
