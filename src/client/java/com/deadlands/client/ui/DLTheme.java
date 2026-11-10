package com.deadlands.client.ui;

import com.deadlands.Deadlands;
import com.deadlands.config.ConfigManager;
import com.deadlands.registry.DLSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;

/** The DEADLANDS interface palette, sprite ids and UI sound helpers. */
public final class DLTheme {
	// Text on dark metal.
	public static final int TEXT = 0xFFE3DCCB;
	public static final int TEXT_DIM = 0xFF9A9384;
	public static final int TEXT_FAINT = 0xFF6B655A;
	public static final int ACCENT = 0xFFC8902E;
	public static final int ACCENT_LIGHT = 0xFFF0C774;
	public static final int DANGER = 0xFFC0443A;
	// Ink on journal paper.
	public static final int INK = 0xFF2A241F;
	public static final int INK_SOFT = 0xFF5A5046;
	public static final int INK_RED = 0xFF6B1E16;

	// GUI atlas sprites (assets/deadlands/textures/gui/sprites/...).
	public static final Identifier BUTTON = Deadlands.id("widget/button");
	public static final Identifier BUTTON_HIGHLIGHTED = Deadlands.id("widget/button_highlighted");
	public static final Identifier BUTTON_DISABLED = Deadlands.id("widget/button_disabled");
	public static final Identifier MENU_HIGHLIGHT = Deadlands.id("widget/menu_highlight");
	public static final Identifier MENU_SHADE = Deadlands.id("widget/menu_shade");
	public static final Identifier PANEL = Deadlands.id("widget/panel");
	public static final Identifier INSET = Deadlands.id("widget/inset");
	public static final Identifier SLIDER = Deadlands.id("widget/slider");
	public static final Identifier SLIDER_HIGHLIGHTED = Deadlands.id("widget/slider_highlighted");
	public static final Identifier SLIDER_HANDLE = Deadlands.id("widget/slider_handle");
	public static final Identifier SLIDER_HANDLE_HIGHLIGHTED = Deadlands.id("widget/slider_handle_highlighted");
	public static final Identifier LAMP_ON = Deadlands.id("widget/lamp_on");
	public static final Identifier LAMP_OFF = Deadlands.id("widget/lamp_off");
	public static final Identifier TAB = Deadlands.id("journal/tab");
	public static final Identifier TAB_SELECTED = Deadlands.id("journal/tab_selected");
	public static final Identifier ICON_LOG = Deadlands.id("icon/log");
	public static final Identifier ICON_THREAT = Deadlands.id("icon/threat");
	public static final Identifier ICON_SETTINGS = Deadlands.id("icon/settings");
	public static final Identifier ICON_TASKS = Deadlands.id("icon/tasks");
	public static final Identifier ICON_ARCHIVE = Deadlands.id("icon/archive");
	public static final Identifier DAY_STROKE = Deadlands.id("hud/day_stroke");

	private DLTheme() {
	}

	public static int withAlpha(int argb, float alpha) {
		int a = Math.round(((argb >>> 24) & 0xFF) * Mth.clamp(alpha, 0.0F, 1.0F));
		return (a << 24) | (argb & 0x00FFFFFF);
	}

	public static void playUi(Holder<SoundEvent> sound, float pitch, float volume) {
		if (ConfigManager.get().audio.interfaceSounds) {
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound.value(), pitch, volume));
		}
	}

	/** Button press sound: the DEADLANDS latch click, or vanilla's click if interface sounds are off. */
	public static void click() {
		if (ConfigManager.get().audio.interfaceSounds) {
			playUi(DLSounds.UI_CLICK, 0.95F + (float) Math.random() * 0.1F, 0.7F);
		} else {
			AbstractWidget.playButtonClickSound(Minecraft.getInstance().getSoundManager());
		}
	}
}
