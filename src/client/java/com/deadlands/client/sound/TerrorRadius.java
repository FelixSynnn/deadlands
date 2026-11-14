package com.deadlands.client.sound;

import com.deadlands.config.ConfigManager;
import com.deadlands.config.DeadlandsConfig;
import com.deadlands.entity.AsesinoEntity;
import com.deadlands.entity.DotEntity;
import com.deadlands.entity.GuestEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Each killer's terror radius ({@code audio.aberrantTerrorRadius}, {@code audio.guestTerrorRadius}) and which
 * one's chase music plays. A killer's "terror" is its distance divided by its own radius (0 when it is
 * chasing you): layer 1 within 100% of the radius, layer 2 within 50%, layer 3 within 25%, layer 4 while it
 * chases you. The killer with the lowest terror decides the music; the other's fades out.
 */
public final class TerrorRadius {
	public static final int NONE = 0;
	public static final int ABERRANT = 1;
	public static final int GUEST = 2;
	public static final int DOT = 3;
	/** Terror above this is out of range. */
	private static final double OUT = 1.0;

	private TerrorRadius() {
	}

	/** The music layer (0 to 3) for a terror value, or -1 when out of range. */
	public static int layer(double terror) {
		if (terror < 0.0) {
			return 3;
		}
		return terror <= 0.25 ? 2 : terror <= 0.5 ? 1 : terror <= OUT ? 0 : -1;
	}

	/** The Aberrant's terror for this survivor: -1 while he chases them; stalking them counts as at least layer 1. */
	public static double aberrant(Minecraft minecraft, LocalPlayer player) {
		DeadlandsConfig.Audio config = ConfigManager.get().audio;
		double best = Double.MAX_VALUE;
		for (AsesinoEntity a : minecraft.level.getEntitiesOfClass(AsesinoEntity.class, player.getBoundingBox().inflate(160.0), AsesinoEntity::isAlive)) {
			double terror = a.chasingId() == player.getId() ? -1.0 : a.distanceTo(player) / config.aberrantTerrorRadius;
			if (a.stalkingId() == player.getId()) {
				terror = Math.min(terror, OUT);
			}
			best = Math.min(best, terror);
		}
		return best;
	}

	/** The Guest's terror for this survivor: -1 while it hunts them. */
	public static double guest(Minecraft minecraft, LocalPlayer player) {
		DeadlandsConfig.Audio config = ConfigManager.get().audio;
		double best = Double.MAX_VALUE;
		for (GuestEntity g : minecraft.level.getEntitiesOfClass(GuestEntity.class, player.getBoundingBox().inflate(160.0), GuestEntity::isAlive)) {
			best = Math.min(best, g.huntingId() == player.getId() ? -1.0 : g.distanceTo(player) / config.guestTerrorRadius);
		}
		return best;
	}

	/** D.O.T.'s terror for this survivor: -1 while he pursues them. */
	public static double dot(Minecraft minecraft, LocalPlayer player) {
		DeadlandsConfig.Audio config = ConfigManager.get().audio;
		double best = Double.MAX_VALUE;
		for (DotEntity d : minecraft.level.getEntitiesOfClass(DotEntity.class, player.getBoundingBox().inflate(160.0), DotEntity::isAlive)) {
			best = Math.min(best, d.preyId() == player.getId() ? -1.0 : d.distanceTo(player) / config.dotTerrorRadius);
		}
		return best;
	}

	/** Whose music plays for this survivor: the killer with the lowest terror, if any is in range. */
	public static int winner(Minecraft minecraft, LocalPlayer player) {
		double a = aberrant(minecraft, player);
		double g = guest(minecraft, player);
		double d = dot(minecraft, player);
		if (layer(a) < 0 && layer(g) < 0 && layer(d) < 0) {
			return NONE;
		}
		if (d <= a && d <= g) {
			return DOT;
		}
		return g <= a ? GUEST : ABERRANT;
	}
}
