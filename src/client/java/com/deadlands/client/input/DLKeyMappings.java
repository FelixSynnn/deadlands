package com.deadlands.client.input;

import com.deadlands.Deadlands;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/** DEADLANDS key bindings, grouped under their own category in Controls. */
public final class DLKeyMappings {
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Deadlands.id("deadlands"));

	public static final KeyMapping JOURNAL = KeyBindingHelper.registerKeyBinding(
		new KeyMapping("key.deadlands.journal", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY));

	/** Sound the horn of the vehicle you are driving. */
	public static final KeyMapping HORN = KeyBindingHelper.registerKeyBinding(
		new KeyMapping("key.deadlands.horn", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY));

	/** Switch the headlights of the vehicle you are driving. */
	public static final KeyMapping HEADLIGHTS = KeyBindingHelper.registerKeyBinding(
		new KeyMapping("key.deadlands.headlights", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY));

	private DLKeyMappings() {
	}

	public static void init() {
		// Class loading registers everything above.
	}
}
