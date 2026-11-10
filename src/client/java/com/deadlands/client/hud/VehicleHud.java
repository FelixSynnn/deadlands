package com.deadlands.client.hud;

import com.deadlands.client.ui.DLTheme;
import com.deadlands.vehicle.VehicleEntity;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The dashboard, bottom right, while a survivor sits in a vehicle: its name, the fuel in litres
 * with a gauge, and the state of the engine, tires and body. Read straight from the vehicle the
 * player is riding (the server keeps those values in sync).
 */
public final class VehicleHud {
	private static final int W = 96;

	private VehicleHud() {
	}

	public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.options.hideGui || !(minecraft.player.getVehicle() instanceof VehicleEntity vehicle)) {
			return;
		}
		Font font = minecraft.font;
		int x = graphics.guiWidth() - W - 8;
		int y = graphics.guiHeight() - 62;
		graphics.fill(x - 4, y - 4, x + W + 4, y + 54, DLTheme.withAlpha(0xFF000000, 0.5F));
		graphics.drawString(font, Component.translatable(vehicle.kind().translationKey()), x, y, DLTheme.ACCENT, true);
		Component flags = vehicle.towing() ? Component.translatable("hud.deadlands.vehicle.towing")
			: vehicle.lights() ? Component.translatable("hud.deadlands.vehicle.lights") : null;
		if (flags != null) {
			graphics.drawString(font, flags, x + W - font.width(flags), y, DLTheme.TEXT, true);
		}
		y += 12;
		float fuel = vehicle.fuel() / vehicle.kind().tank();
		gauge(graphics, font, x, y, "hud.deadlands.vehicle.fuel", fuel, Math.round(vehicle.fuel()) + " L", fuel < 0.15F ? DLTheme.DANGER : DLTheme.ACCENT);
		y += 11;
		gauge(graphics, font, x, y, "hud.deadlands.vehicle.engine", vehicle.engine() / 100.0F, null, colour(vehicle.engine()));
		y += 10;
		gauge(graphics, font, x, y, "hud.deadlands.vehicle.tires", vehicle.tires() / 100.0F, null, colour(vehicle.tires()));
		y += 10;
		gauge(graphics, font, x, y, "hud.deadlands.vehicle.body", vehicle.body() / 100.0F, null, colour(vehicle.body()));
	}

	private static int colour(float condition) {
		return condition < VehicleEntity.USABLE ? DLTheme.DANGER : DLTheme.TEXT_DIM;
	}

	private static void gauge(GuiGraphics graphics, Font font, int x, int y, String key, float value, String number, int colour) {
		graphics.drawString(font, Component.translatable(key), x, y, DLTheme.TEXT_DIM, true);
		int bx = x + 40;
		int bw = number == null ? W - 40 : W - 40 - 26;
		graphics.fill(bx, y + 2, bx + bw, y + 6, 0x99000000);
		graphics.fill(bx, y + 2, bx + Math.round(bw * Mth.clamp(value, 0.0F, 1.0F)), y + 6, colour);
		if (number != null) {
			graphics.drawString(font, number, x + W - font.width(number), y, DLTheme.TEXT, true);
		}
	}
}
