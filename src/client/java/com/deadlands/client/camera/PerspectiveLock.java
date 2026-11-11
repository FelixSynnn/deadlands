package com.deadlands.client.camera;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;

/**
 * Hard Rule 1: on foot the camera is always first person and the perspective key does nothing. In
 * any seat (a vehicle, the helicopter) it switches to the third-person vehicle camera, and back to
 * first person on getting out. Cutscenes move the camera themselves ({@code CameraMixin}).
 */
public final class PerspectiveLock {
	private PerspectiveLock() {
	}

	public static void init() {
		// Swallow the key before Minecraft's own key handling sees it this tick.
		ClientTickEvents.START_CLIENT_TICK.register(client -> {
			while (client.options.keyTogglePerspective.consumeClick()) {
				// ignored
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(PerspectiveLock::enforce);
	}

	private static void enforce(Minecraft client) {
		if (client.player == null) {
			return;
		}
		CameraType wanted = client.player.isPassenger() ? CameraType.THIRD_PERSON_BACK : CameraType.FIRST_PERSON;
		if (client.options.getCameraType() != wanted) {
			client.options.setCameraType(wanted);
		}
	}
}
