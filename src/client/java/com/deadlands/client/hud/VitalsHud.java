package com.deadlands.client.hud;

import com.deadlands.Deadlands;
import com.deadlands.client.ui.DLTheme;
import com.deadlands.config.ConfigManager;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * DEADLANDS health, armour and hunger, in place of Minecraft's hearts, armour icons and drumsticks:
 * three plain bars with a pictogram each, in the same spots above the hotbar. Health turns to the
 * danger colour below a third, shows absorption as a lighter cap, and blinks when it is nearly
 * gone; hunger shows saturation as a lighter inner bar. While a gun is held, the rounds loaded and
 * the magazine size are shown to the right of the hotbar. With {@code ui.vitalsHud} off, Minecraft's
 * own elements are drawn instead.
 */
public final class VitalsHud {
	private static final int BAR_W = 81;
	private static final Identifier ICON_HEALTH = Deadlands.id("survival/health");
	private static final Identifier ICON_ARMOUR = Deadlands.id("survival/armour");
	private static final Identifier ICON_HUNGER = Deadlands.id("survival/hunger");
	private static final int HEALTH = 0xFFB8483C;
	private static final int ABSORPTION = 0xFFE8C860;
	private static final int ARMOUR = 0xFF9AA4AC;
	private static final int HUNGER = 0xFFB88A4A;
	private static final int SATURATION = 0xFFE0C080;

	private VitalsHud() {
	}

	/** Swap the three vanilla elements for ours (each falls back to vanilla when the setting is off). */
	public static void register() {
		HudElementRegistry.replaceElement(VanillaHudElements.HEALTH_BAR, vanilla -> swap(vanilla, VitalsHud::health));
		HudElementRegistry.replaceElement(VanillaHudElements.ARMOR_BAR, vanilla -> swap(vanilla, VitalsHud::armour));
		HudElementRegistry.replaceElement(VanillaHudElements.FOOD_BAR, vanilla -> swap(vanilla, VitalsHud::hunger));
	}

	private static HudElement swap(HudElement vanilla, HudElement ours) {
		return (graphics, deltaTracker) -> {
			if (ConfigManager.get().ui.vitalsHud) {
				ours.render(graphics, deltaTracker);
			} else {
				vanilla.render(graphics, deltaTracker);
			}
		};
	}

	private static LocalPlayer survivor() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.gameMode == null || !minecraft.gameMode.canHurtPlayer() || minecraft.options.hideGui) {
			return null;
		}
		return minecraft.player;
	}

	private static void health(GuiGraphics graphics, DeltaTracker deltaTracker) {
		LocalPlayer player = survivor();
		if (player == null) {
			return;
		}
		int x = graphics.guiWidth() / 2 - 91;
		int y = graphics.guiHeight() - 39;
		float max = Math.max(1.0F, player.getMaxHealth());
		float share = Mth.clamp(player.getHealth() / max, 0.0F, 1.0F);
		int colour = HEALTH;
		if (share <= 0.2F && (player.tickCount / 5) % 2 == 0) {
			colour = DLTheme.ACCENT_LIGHT; // nearly gone: blink
		} else if (share < 0.34F) {
			colour = DLTheme.DANGER;
		}
		bar(graphics, x, y, ICON_HEALTH, share, colour);
		ammo(graphics, player);
		float absorption = player.getAbsorptionAmount();
		if (absorption > 0.0F) {
			int w = Math.round((BAR_W - 12) * Mth.clamp(absorption / max, 0.0F, 1.0F));
			graphics.fill(x + 11, y + 2, x + 11 + w, y + 4, ABSORPTION);
		}
	}

	private static void armour(GuiGraphics graphics, DeltaTracker deltaTracker) {
		LocalPlayer player = survivor();
		if (player == null || player.getArmorValue() <= 0) {
			return;
		}
		bar(graphics, graphics.guiWidth() / 2 - 91, graphics.guiHeight() - 49, ICON_ARMOUR, Mth.clamp(player.getArmorValue() / 20.0F, 0.0F, 1.0F), ARMOUR);
	}

	private static void hunger(GuiGraphics graphics, DeltaTracker deltaTracker) {
		LocalPlayer player = survivor();
		if (player == null) {
			return;
		}
		int x = graphics.guiWidth() / 2 + 10;
		int y = graphics.guiHeight() - 39;
		int food = player.getFoodData().getFoodLevel();
		bar(graphics, x, y, ICON_HUNGER, Mth.clamp(food / 20.0F, 0.0F, 1.0F), food <= 6 ? DLTheme.DANGER : HUNGER);
		int w = Math.round((BAR_W - 12) * Mth.clamp(player.getFoodData().getSaturationLevel() / 20.0F, 0.0F, 1.0F));
		graphics.fill(x + 11, y + 5, x + 11 + w, y + 6, SATURATION);
	}

	/** Rounds in the held gun and its magazine size, right of the hotbar. Drawn with the health bar. */
	private static void ammo(GuiGraphics graphics, LocalPlayer player) {
		net.minecraft.world.item.ItemStack held = player.getMainHandItem();
		if (!(held.getItem() instanceof com.deadlands.item.FirearmItem gun)) {
			return;
		}
		int loaded = com.deadlands.item.FirearmItem.loaded(held);
		String text = loaded + " / " + gun.spec().magazine();
		net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
		int x = graphics.guiWidth() / 2 + 98;
		int y = graphics.guiHeight() - 15;
		graphics.fill(x - 3, y - 3, x + font.width(text) + 3, y + 10, DLTheme.withAlpha(0xFF000000, 0.5F));
		graphics.drawString(font, text, x, y, loaded == 0 ? DLTheme.DANGER : DLTheme.TEXT, true);
	}

	private static void bar(GuiGraphics graphics, int x, int y, Identifier icon, float share, int colour) {
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, icon, x, y, 9, 9);
		int bx = x + 11;
		int width = BAR_W - 12;
		graphics.fill(bx - 1, y + 1, bx + width + 1, y + 8, DLTheme.withAlpha(0xFF000000, 0.6F));
		graphics.fill(bx, y + 2, bx + Math.round(width * share), y + 7, colour);
	}
}
