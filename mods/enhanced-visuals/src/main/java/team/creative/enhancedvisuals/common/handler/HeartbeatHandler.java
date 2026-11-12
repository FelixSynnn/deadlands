package team.creative.enhancedvisuals.common.handler;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import team.creative.creativecore.common.config.api.CreativeConfig;
import team.creative.creativecore.common.config.premade.curve.DecimalCurve;
import team.creative.enhancedvisuals.api.VisualHandler;
import team.creative.enhancedvisuals.api.type.VisualType;
import team.creative.enhancedvisuals.api.type.VisualTypeBlur;
import team.creative.enhancedvisuals.api.type.VisualTypeOverlay;

public class HeartbeatHandler extends VisualHandler {
    @CreativeConfig
    public boolean useHealthPercentage = false;
    @CreativeConfig
    public int maxHealth = 6;
    @CreativeConfig
    public float maxHealthPercentage = 0.3F;
    @CreativeConfig
    public float heartbeatOverlayIntensity = 0.5F;
    @CreativeConfig
    public int heartbeatOverlayDuration = 20;
    @CreativeConfig
    public float heartbeatBlurIntensity = 50.0F;
    @CreativeConfig
    public int heartbeatBlurDuration = 5;
    @CreativeConfig
    public int minHeartbeatLength = 15;
    @CreativeConfig
    public float heartbeatTimeFactor = 100.0F;
    @CreativeConfig
    public float heartbeatVolume = 1.0F;
    @CreativeConfig
    public VisualType lowhealth = new VisualTypeOverlay("lowhealth");
    @CreativeConfig
    public VisualType blur = new VisualTypeBlur("blur");
    public int effectBufferTicks;

    @Override
    public void tick(@Nullable Player player) {
        if (this.shouldHeartbeatTrigger(player)) {
            if (this.effectBufferTicks <= 0) {
                float intensity = this.getIntensity(player);
                this.resetBufferTicks(player);
                this.addVisualFadeOut(
                    this.lowhealth, this, new DecimalCurve(0.0, Math.min(0.7F, intensity) * this.heartbeatOverlayIntensity, this.heartbeatOverlayDuration, 0.0)
                );
                this.addVisualFadeOut(
                    this.blur, this, new DecimalCurve(0.0, Math.min(0.7F, intensity) * this.heartbeatBlurIntensity, this.heartbeatBlurDuration, 0.0)
                );
                this.playSound(Identifier.tryBuild("enhancedvisuals", "heartbeatout"), this.heartbeatVolume);
            } else if (this.effectBufferTicks == 5) {
                float intensity = this.getIntensity(player);
                this.playSound(Identifier.tryBuild("enhancedvisuals", "heartbeatin"), this.heartbeatVolume);
                this.addVisualFadeOut(
                    this.blur, this, new DecimalCurve(0.0, Math.min(0.7F, intensity) * this.heartbeatBlurIntensity, this.heartbeatBlurDuration, 0.0)
                );
            }
        }

        this.effectBufferTicks--;
    }

    private void resetBufferTicks(@NotNull Player player) {
        float percentHealthLeft = player.getHealth() / player.getMaxHealth();
        this.effectBufferTicks = (int)(percentHealthLeft * this.heartbeatTimeFactor + this.minHeartbeatLength);
    }

    private float getIntensity(@NotNull Player player) {
        return this.useHealthPercentage ? this.maxHealthPercentage - player.getHealth() / player.getMaxHealth() : player.getHealth() / this.maxHealth;
    }

    private boolean shouldHeartbeatTrigger(@Nullable Player player) {
        if (player == null || Minecraft.getInstance().isPaused() || !player.isAlive()) {
            return false;
        } else if (this.useHealthPercentage) {
            float percentageHealth = player.getHealth() / player.getMaxHealth();
            return percentageHealth < this.maxHealthPercentage;
        } else {
            return player.getHealth() < this.maxHealth;
        }
    }
}
