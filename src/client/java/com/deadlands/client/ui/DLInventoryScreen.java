package com.deadlands.client.ui;

import com.deadlands.client.ClientState;
import com.deadlands.network.SkillsPayload;
import com.deadlands.network.SurvivalPayload;
import com.deadlands.skill.Skill;
import com.deadlands.survival.Condition;
import com.deadlands.survival.LocalSurvival;
import com.deadlands.survival.SurvivalData;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * The survivor's inventory. On the left, Minecraft's own inventory menu in DEADLANDS dress: the
 * equipment column (helmet to boots) and the off hand, the survivor, the 2x2 field crafting grid,
 * the pack and the hotbar. On the right, a status page: body meters with numbers, the load carried
 * against capacity, up to three of the things that are wrong, and the five skill levels.
 *
 * <p>It does not have Minecraft's recipe book. Creative mode keeps all of Minecraft's normal menus.
 */
public class DLInventoryScreen extends DLMenuScreen<InventoryMenu> {
	private static final int INVENTORY_W = 176;
	private static final int STATUS_W = 112;
	private static final int WATER = 0xFF5A9CC8;

	private final Player player;

	public DLInventoryScreen(Player player) {
		super(player.inventoryMenu, player.getInventory(), Component.translatable("inventory.deadlands.title"));
		this.player = player;
		this.imageWidth = INVENTORY_W + STATUS_W;
		this.titleLabelX = 97;
		this.titleLabelY = 6;
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		super.renderBg(graphics, partialTick, mouseX, mouseY);
		// The survivor, in the frame between the equipment and the crafting grid.
		graphics.fill(leftPos + 25, topPos + 7, leftPos + 76, topPos + 79, 0x88000000);
		InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, leftPos + 26, topPos + 8, leftPos + 75, topPos + 78, 30, 0.0625F, mouseX, mouseY, player);
		divider(graphics, leftPos + 7, leftPos + INVENTORY_W - 7, topPos + 80);
		graphics.fill(leftPos + INVENTORY_W, topPos + 6, leftPos + INVENTORY_W + 1, topPos + imageHeight - 6, 0x66000000);
		status(graphics, leftPos + INVENTORY_W + 8, topPos + 8);
	}

	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		graphics.drawString(font, Component.translatable("inventory.deadlands.crafting"), titleLabelX, titleLabelY, DLTheme.TEXT_DIM, true);
	}

	// ======================================================================================== status page

	private void status(GuiGraphics graphics, int x, int y) {
		int w = STATUS_W - 16;
		SurvivalPayload body = LocalSurvival.get();
		graphics.drawString(font, Component.translatable("inventory.deadlands.body"), x, y, DLTheme.ACCENT, true);
		y += 11;
		if (!body.active()) {
			graphics.drawString(font, Component.translatable("inventory.deadlands.unaffected"), x, y, DLTheme.TEXT_DIM, true);
			y += 12;
		} else {
			y = meter(graphics, x, y, w, "inventory.deadlands.thirst", body.thirst() / SurvivalData.MAX, percent(body.thirst()), body.thirst() < 25.0F ? DLTheme.DANGER : WATER);
			y = meter(graphics, x, y, w, "inventory.deadlands.stamina", body.stamina() / SurvivalData.MAX, percent(body.stamina()), DLTheme.ACCENT);
			y = meter(graphics, x, y, w, "inventory.deadlands.rest", 1.0F - body.fatigue() / SurvivalData.MAX, percent(SurvivalData.MAX - body.fatigue()),
				body.fatigue() >= 65.0F ? DLTheme.DANGER : DLTheme.TEXT_DIM);
			float share = body.load() / Math.max(1.0F, body.capacity());
			y = meter(graphics, x, y, w, "inventory.deadlands.load", Math.min(1.0F, share),
				String.format(Locale.ROOT, "%.0f / %.0f kg", body.load(), body.capacity()), share > 1.0F ? DLTheme.DANGER : DLTheme.TEXT_DIM);
			int shown = 0;
			for (Condition condition : Condition.values()) {
				if ((body.conditions() & condition.bit()) != 0 && shown < 3) {
					graphics.drawString(font, Component.translatable(condition.translationKey()), x, y, condition.serious() ? DLTheme.DANGER : DLTheme.TEXT, true);
					y += 9;
					shown++;
				}
			}
		}
		// Skills fill the foot of the page whatever is above.
		int sy = topPos + imageHeight - 8 - 9 * Skill.values().length;
		divider(graphics, x, x + w, sy - 4);
		SkillsPayload skills = ClientState.skills();
		for (Skill skill : Skill.values()) {
			graphics.drawString(font, Component.translatable(skill.translationKey()), x, sy, DLTheme.TEXT_DIM, true);
			String level = Integer.toString(skills.level(skill));
			graphics.drawString(font, level, x + w - font.width(level), sy, DLTheme.TEXT, true);
			sy += 9;
		}
	}

	private int meter(GuiGraphics graphics, int x, int y, int w, String key, float value, String number, int colour) {
		graphics.drawString(font, Component.translatable(key), x, y, DLTheme.TEXT_DIM, true);
		graphics.drawString(font, number, x + w - font.width(number), y, DLTheme.TEXT, true);
		graphics.fill(x, y + 9, x + w, y + 11, 0x99000000);
		graphics.fill(x, y + 9, x + Math.round(w * Mth.clamp(value, 0.0F, 1.0F)), y + 11, colour);
		return y + 14;
	}

	private static String percent(float value) {
		return Math.round(Mth.clamp(value, 0.0F, SurvivalData.MAX)) + "%";
	}
}
