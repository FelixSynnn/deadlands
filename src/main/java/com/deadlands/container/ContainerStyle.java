package com.deadlands.container;

/** Visual family of a container's inventory screen. */
public enum ContainerStyle {
	WOOD, METAL, MEDICAL;

	public static ContainerStyle byId(int id) {
		ContainerStyle[] values = values();
		return values[Math.floorMod(id, values.length)];
	}
}
