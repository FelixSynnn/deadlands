package com.deadlands.container;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/**
 * How weathered a container is. Each condition has its own textures; a damaged container also
 * holds about half as much loot. Structures pick the condition; player-placed containers are clean.
 */
public enum ContainerCondition implements StringRepresentable {
	CLEAN, DUSTY, RUSTY, DAMAGED;

	private final String id = name().toLowerCase(Locale.ROOT);

	@Override
	public String getSerializedName() {
		return id;
	}
}
