package com.deadlands.progression;

import com.deadlands.config.DeadlandsConfig;
import net.minecraft.network.FriendlyByteBuf;

/**
 * The subset of the server config that decides when hordes and bosses happen. The server sends it
 * to clients so the journal can show the real schedule without trusting the client's own config.
 */
public record ScheduleSettings(
	boolean hordesEnabled,
	int hordeInterval,
	int hordeBaseSize,
	double hordeGrowth,
	int hordeMaxSize,
	boolean bossesEnabled,
	int bossInterval
) {
	public static final ScheduleSettings DEFAULT = fromConfig(new DeadlandsConfig());

	public ScheduleSettings {
		hordeInterval = Math.max(1, hordeInterval);
		bossInterval = Math.max(1, bossInterval);
		hordeBaseSize = Math.max(0, hordeBaseSize);
		hordeMaxSize = Math.max(hordeBaseSize, hordeMaxSize);
		hordeGrowth = Math.max(0.0, hordeGrowth);
	}

	public static ScheduleSettings fromConfig(DeadlandsConfig config) {
		return new ScheduleSettings(
			config.hordes.enabled,
			config.hordes.intervalDays,
			config.hordes.baseSize,
			config.hordes.growthPerHorde,
			config.hordes.maxSize,
			config.bosses.enabled,
			config.bosses.intervalDays
		);
	}

	public void write(FriendlyByteBuf buf) {
		buf.writeBoolean(hordesEnabled);
		buf.writeVarInt(hordeInterval);
		buf.writeVarInt(hordeBaseSize);
		buf.writeDouble(hordeGrowth);
		buf.writeVarInt(hordeMaxSize);
		buf.writeBoolean(bossesEnabled);
		buf.writeVarInt(bossInterval);
	}

	public static ScheduleSettings read(FriendlyByteBuf buf) {
		return new ScheduleSettings(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readDouble(), buf.readVarInt(), buf.readBoolean(), buf.readVarInt());
	}
}
