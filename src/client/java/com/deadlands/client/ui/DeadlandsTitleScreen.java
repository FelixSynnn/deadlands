package com.deadlands.client.ui;

import com.deadlands.Deadlands;
import com.deadlands.client.ui.widget.DLMenuButton;
import com.deadlands.config.ConfigManager;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.screens.CreditsAndAttributionScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/**
 * The DEADLANDS main menu. Extends {@link TitleScreen} so the game, other mods and Fabric's test
 * harness still recognise it as the title screen, but replaces all of its content and rendering.
 * Menu music comes from the DEADLANDS theme, which replaces {@code music.menu} via sounds.json.
 */
public class DeadlandsTitleScreen extends TitleScreen {
	private static boolean shownThisSession;

	private final boolean fadeIn;
	private final long openedAt = Util.getMillis();
	private final List<AbstractWidget> widgets = new ArrayList<>();
	private int left;
	private int logoX;
	private int logoY;
	private int logoW;
	private int logoH;
	private int taglineY;
	private int versionY;
	private String versionText = "";

	public DeadlandsTitleScreen() {
		super(false);
		this.fadeIn = !shownThisSession;
		shownThisSession = true;
	}

	@Override
	protected void init() {
		widgets.clear();
		left = Math.max(20, (int) (width * 0.07F));
		int w = 180;
		int h = 18;
		int step = 21;
		int menuHeight = 4 * step + h;

		// Bottom: the version line shares a row with the copyright notice when it fits, otherwise
		// it moves up a line; the language/accessibility row sits above it.
		Component copyright = Component.translatable("title.credits");
		int cw = font.width(copyright);
		versionText = "DEADLANDS " + Deadlands.version() + "  |  Minecraft " + SharedConstants.getCurrentVersion().name() + "  |  Fabric";
		boolean stacked = left + font.width(versionText) + 12 > width - cw - 4;
		versionY = stacked ? height - 24 : height - 12;
		int smallY = versionY - 18;
		int menuLimit = smallY - 10;

		// The logo shrinks on short windows so it never collides with the menu.
		logoY = Math.max(10, (int) (height * 0.10F));
		logoW = Math.min((int) (width * 0.42F), 400);
		logoH = logoW * TitleBackdrop.LOGO_HEIGHT / TitleBackdrop.LOGO_WIDTH;
		int maxLogoH = menuLimit - menuHeight - 24 - logoY;
		if (logoH > maxLogoH) {
			logoH = Math.max(14, maxLogoH);
			logoW = logoH * TitleBackdrop.LOGO_WIDTH / TitleBackdrop.LOGO_HEIGHT;
		}
		logoX = left - Math.round(logoW * 0.035F);
		taglineY = logoY + logoH + 4;

		int y = Math.max(taglineY + 16, Math.min(taglineY + 26, menuLimit - menuHeight));
		add(new DLMenuButton(left, y, w, h, Component.translatable("menu.deadlands.singleplayer"),
			b -> minecraft.setScreen(new SelectWorldScreen(this))));
		DLMenuButton multiplayer = add(new DLMenuButton(left, y += step, w, h, Component.translatable("menu.deadlands.multiplayer"), b -> {
			Screen next = minecraft.options.skipMultiplayerWarning ? new JoinMultiplayerScreen(this) : new SafetyScreen(this);
			minecraft.setScreen(next);
		}));
		multiplayer.active = minecraft.allowsMultiplayer();
		add(new DLMenuButton(left, y += step, w, h, Component.translatable("menu.deadlands.options"),
			b -> minecraft.setScreen(new OptionsScreen(this, minecraft.options))));
		add(new DLMenuButton(left, y += step, w, h, Component.translatable("menu.deadlands.settings"),
			b -> minecraft.setScreen(new SettingsScreen(this))));
		add(new DLMenuButton(left, y += step, w, h, Component.translatable("menu.deadlands.quit"),
			b -> minecraft.stop()));

		Component language = Component.translatable("options.language");
		Component accessibility = Component.translatable("options.accessibility");
		int languageW = font.width(language) + 12;
		add(new DLMenuButton(left, smallY, languageW, 12, language,
			b -> minecraft.setScreen(new LanguageSelectScreen(this, minecraft.options, minecraft.getLanguageManager()))));
		add(new DLMenuButton(left + languageW + 10, smallY, font.width(accessibility) + 12, 12, accessibility,
			b -> minecraft.setScreen(new AccessibilityOptionsScreen(this, minecraft.options))));

		add(new PlainTextButton(width - cw - 4, height - 12, cw, 10, copyright,
			b -> minecraft.setScreen(new CreditsAndAttributionScreen(this)), font));
	}

	private <T extends AbstractWidget> T add(T widget) {
		widgets.add(widget);
		return addRenderableWidget(widget);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		float nx = Mth.clamp((mouseX / (float) Math.max(1, width) - 0.5F) * 2.0F, -1.0F, 1.0F);
		float ny = Mth.clamp((mouseY / (float) Math.max(1, height) - 0.5F) * 2.0F, -1.0F, 1.0F);
		TitleBackdrop.render(graphics, width, height, nx, ny, ConfigManager.get().ui.filmGrain);

		float since = (Util.getMillis() - openedAt) / 1000.0F;
		float ui = fadeIn ? Mth.clamp((since - 0.9F) / 1.1F, 0.0F, 1.0F) : 1.0F;
		float logoAlpha = fadeIn ? Mth.clamp((since - 0.3F) / 1.4F, 0.0F, 1.0F) : 1.0F;
		fadeWidgets(ui);

		// Darken the left side so the menu stays readable over bright fog.
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, DLTheme.MENU_SHADE, 0, 0, Math.min(width, left + 300), height, ARGB.white(0.85F * ui));

		// A faint flicker, as if the logo were lit by a failing light.
		float flicker = 0.93F + 0.07F * (float) Math.sin(since * 7.3F) * (float) Math.sin(since * 2.1F + 1.3F);
		graphics.blit(RenderPipelines.GUI_TEXTURED, TitleBackdrop.LOGO, logoX, logoY, 0.0F, 0.0F, logoW, logoH,
			TitleBackdrop.LOGO_WIDTH, TitleBackdrop.LOGO_HEIGHT, TitleBackdrop.LOGO_WIDTH, TitleBackdrop.LOGO_HEIGHT, ARGB.white(logoAlpha * flicker));
		graphics.drawString(font, Component.translatable("menu.deadlands.tagline"), left, taglineY,
			DLTheme.withAlpha(DLTheme.TEXT_DIM, ui), true);

		for (AbstractWidget widget : widgets) {
			widget.render(graphics, mouseX, mouseY, partialTick);
		}

		graphics.drawString(font, versionText, left, versionY, DLTheme.withAlpha(DLTheme.TEXT_FAINT, ui), false);
		Component stamp = Component.translatable("menu.deadlands.dev_build");
		graphics.drawString(font, stamp, width - font.width(stamp) - 6, 6, DLTheme.withAlpha(DLTheme.ACCENT, ui * 0.9F), true);

		if (fadeIn && since < 1.6F) {
			graphics.fill(0, 0, width, height, ARGB.black(1.0F - since / 1.6F));
		}
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		// Everything is drawn in render().
	}
}
