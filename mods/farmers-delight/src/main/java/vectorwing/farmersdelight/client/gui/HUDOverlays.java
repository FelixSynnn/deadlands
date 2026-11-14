package vectorwing.farmersdelight.client.gui;

import java.util.Random;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.jspecify.annotations.NonNull;
import vectorwing.farmersdelight.client.FarmersDelightClient;
import vectorwing.farmersdelight.common.Configuration;
import vectorwing.farmersdelight.common.registry.ModEffects;

public class HUDOverlays {
    public static int healthIconsOffset = 39;
    public static int foodIconsOffset = 39;
    private static final Identifier MOD_ICONS_TEXTURE = Identifier.fromNamespaceAndPath("farmersdelight", "textures/gui/fd_icons.png");

    @Deprecated
    public static void register() {
    }

    public static void drawNourishmentOverlay(FoodData foodData, Minecraft minecraft, GuiGraphics graphics, int right, int top, boolean naturalHealing) {
        float saturation = foodData.getSaturationLevel();
        int foodLevel = foodData.getFoodLevel();
        int ticks = minecraft.gui.getGuiTicks();
        Random rand = new Random();
        rand.setSeed(ticks * 312871L);

        for (int j = 0; j < 10; j++) {
            int x = right - j * 8 - 9;
            int y = top;
            if (saturation <= 0.0F && ticks % (foodLevel * 3 + 1) == 0) {
                y = top + (rand.nextInt(3) - 1);
            }

            graphics.blit(RenderPipelines.GUI_TEXTURED, MOD_ICONS_TEXTURE, x, y, 0.0F, 0.0F, 9, 9, 256, 256);
            float effectiveHungerOfBar = foodData.getFoodLevel() / 2.0F - j;
            int naturalHealingOffset = naturalHealing ? 18 : 0;
            if (effectiveHungerOfBar >= 1.0F) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, MOD_ICONS_TEXTURE, x, y, 18 + naturalHealingOffset, 0.0F, 9, 9, 256, 256);
            } else if (effectiveHungerOfBar >= 0.5) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, MOD_ICONS_TEXTURE, x, y, 9 + naturalHealingOffset, 0.0F, 9, 9, 256, 256);
            }
        }
    }

    public static void drawComfortOverlay(Player player, Minecraft minecraft, GuiGraphics graphics, int left, int top) {
        int ticks = minecraft.gui.getGuiTicks();
        Random rand = new Random();
        rand.setSeed(ticks * 312871L);
        int health = Mth.ceil(player.getHealth());
        float absorb = Mth.ceil(player.getAbsorptionAmount());
        AttributeInstance attrMaxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        assert attrMaxHealth != null;
        float healthMax = (float)attrMaxHealth.getValue();
        int regen = -1;
        if (player.hasEffect(MobEffects.REGENERATION)) {
            regen = ticks % 25;
        }

        int healthRows = Mth.ceil((healthMax + absorb) / 2.0F / 10.0F);
        int rowHeight = Math.max(10 - (healthRows - 2), 3);
        int comfortSheen = ticks % 50;
        int comfortHeartFrame = comfortSheen % 2;
        int[] textureWidth = new int[]{5, 9};
        int healthMaxSingleRow = Mth.ceil(Math.min(healthMax, 20.0F) / 2.0F);
        int leftHeightOffset = (healthRows - 1) * rowHeight;

        for (int i = 0; i < healthMaxSingleRow; i++) {
            int column = i % 10;
            int x = left + column * 8;
            int y = top + leftHeightOffset;
            if (health <= 4) {
                y += rand.nextInt(2);
            }

            if (i == regen) {
                y -= 2;
            }

            if (column == comfortSheen / 2) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, MOD_ICONS_TEXTURE, x, y, 0.0F, 9.0F, textureWidth[comfortHeartFrame], 9, 256, 256);
            }

            if (column == comfortSheen / 2 - 1 && comfortHeartFrame == 0) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, MOD_ICONS_TEXTURE, x + 5, y, 5.0F, 9.0F, 4, 9, 256, 256);
            }
        }
    }

    public abstract static class BaseOverlay {
        public abstract void render(Minecraft var1, Player var2, GuiGraphics var3, int var4, int var5, int var6, int var7);

        public final void render(@NonNull GuiGraphics guiGraphics, @NonNull DeltaTracker deltaTracker) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null && this.shouldRenderOverlay(minecraft, minecraft.player, guiGraphics, minecraft.gui.getGuiTicks())) {
                int top = guiGraphics.guiHeight();
                int left = guiGraphics.guiWidth() / 2 - 91;
                int right = guiGraphics.guiWidth() / 2 + 91;
                this.render(minecraft, minecraft.player, guiGraphics, left, right, top, minecraft.gui.getGuiTicks());
            }
        }

        public boolean shouldRenderOverlay(Minecraft minecraft, Player player, GuiGraphics guiGraphics, int guiTicks) {
            return !minecraft.options.hideGui && minecraft.gameMode != null && minecraft.gameMode.canHurtPlayer();
        }
    }

    public static class ComfortOverlay extends HUDOverlays.BaseOverlay {
        public static final Identifier ID = Identifier.fromNamespaceAndPath("farmersdelight", "comfort");
        public static final HUDOverlays.ComfortOverlay INSTANCE = new HUDOverlays.ComfortOverlay();

        @Override
        public void render(Minecraft minecraft, Player player, GuiGraphics guiGraphics, int left, int right, int top, int guiTicks) {
            FoodData stats = player.getFoodData();
            boolean isPlayerEligibleForComfort = stats.getSaturationLevel() == 0.0F && player.isHurt() && !player.hasEffect(MobEffects.REGENERATION);
            if (player.getEffect(ModEffects.COMFORT) != null && isPlayerEligibleForComfort) {
                HUDOverlays.drawComfortOverlay(player, minecraft, guiGraphics, left, top - HUDOverlays.healthIconsOffset);
            }
        }

        @Override
        public boolean shouldRenderOverlay(Minecraft mc, Player player, GuiGraphics guiGraphics, int guiTicks) {
            return !super.shouldRenderOverlay(mc, player, guiGraphics, guiTicks) ? false : Configuration.ENABLE_COMFORT_HEALTH_OVERLAY.get();
        }
    }

    public static class NourishmentOverlay extends HUDOverlays.BaseOverlay {
        public static final Identifier ID = Identifier.fromNamespaceAndPath("farmersdelight", "nourishment");
        public static final HUDOverlays.NourishmentOverlay INSTANCE = new HUDOverlays.NourishmentOverlay();

        @Override
        public void render(Minecraft minecraft, Player player, GuiGraphics guiGraphics, int left, int right, int top, int guiTicks) {
            FoodData stats = player.getFoodData();
            boolean isPlayerHealingWithSaturation = FarmersDelightClient.naturalRegenerationEnabled && player.isHurt() && stats.getFoodLevel() >= 18;
            if (player.getEffect(ModEffects.NOURISHMENT) != null) {
                HUDOverlays.drawNourishmentOverlay(stats, minecraft, guiGraphics, right, top - HUDOverlays.foodIconsOffset, isPlayerHealingWithSaturation);
            }
        }

        @Override
        public boolean shouldRenderOverlay(Minecraft mc, Player player, GuiGraphics guiGraphics, int guiTicks) {
            return !super.shouldRenderOverlay(mc, player, guiGraphics, guiTicks) ? false : Configuration.ENABLE_NOURISHMENT_HUNGER_OVERLAY.get();
        }
    }
}
