package com.deadlands.client.ui.widget;

import com.deadlands.client.ui.DLTheme;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

/** On/off switch drawn as a DEADLANDS button with an indicator lamp. */
public class DLToggle extends DLButton {
	private boolean value;

	public DLToggle(int x, int y, int width, int height, boolean initial, Consumer<Boolean> onChange) {
		super(x, y, width, height, label(initial), button -> {
		});
		this.value = initial;
		this.changeListener = onChange;
	}

	private final Consumer<Boolean> changeListener;

	private static Component label(boolean on) {
		return Component.translatable(on ? "options.on" : "options.off");
	}

	public boolean value() {
		return value;
	}

	public void setValue(boolean newValue) {
		this.value = newValue;
		setMessage(label(newValue));
	}

	@Override
	public void onPress(net.minecraft.client.input.InputWithModifiers input) {
		setValue(!value);
		changeListener.accept(value);
	}

	@Override
	protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.renderContents(graphics, mouseX, mouseY, partialTick);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, value ? DLTheme.LAMP_ON : DLTheme.LAMP_OFF,
			getX() + 5, getY() + (getHeight() - 8) / 2, 8, 8, ARGB.white(alpha * (active ? 1.0F : 0.5F)));
	}
}
