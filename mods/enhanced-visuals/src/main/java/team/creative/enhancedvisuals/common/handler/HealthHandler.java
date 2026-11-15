package team.creative.enhancedvisuals.common.handler;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import team.creative.creativecore.common.config.api.CreativeConfig;
import team.creative.enhancedvisuals.api.Particle;
import team.creative.enhancedvisuals.api.VisualHandler;
import team.creative.enhancedvisuals.api.type.VisualType;
import team.creative.enhancedvisuals.api.type.VisualTypeParticle;

public class HealthHandler extends VisualHandler {
    @CreativeConfig
    public VisualType impact = new VisualTypeParticle("impact");
    @CreativeConfig
    public double particlesPerDamage = 1.0;
    private List<Particle> particles = new ArrayList<>();

    public HealthHandler() {
        this.enabled = false;
    }

    @Override
    public void tick(@Nullable Player player) {
        float damage = player == null ? 0.0F : player.getMaxHealth() - player.getHealth();
        int amount = Math.max(0, (int)Math.ceil(this.particlesPerDamage * damage));
        if (amount != this.particles.size()) {
            while (this.particles.size() < amount) {
                this.particles.add(this.addParticle(this.impact, this, true, DamageHandler.BLOOD_COLOR));
            }

            while (this.particles.size() > amount) {
                this.remove(this.particles.remove(this.particles.size() > 1 ? this.random().nextInt(this.particles.size() - 1) : 0));
            }
        }
    }
}
