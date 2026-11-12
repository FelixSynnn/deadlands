package vectorwing.farmersdelight.common.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public class ComfortEffect extends MobEffect {
    @Deprecated
    public ComfortEffect() {
        super(MobEffectCategory.BENEFICIAL, 14545909);
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        if (entity.hasEffect(MobEffects.REGENERATION)) {
            return true;
        } else if (entity instanceof Player player && player.getFoodData().getSaturationLevel() > 0.0) {
            return true;
        } else {
            if (entity.getHealth() < entity.getMaxHealth()) {
                entity.heal(1.0F);
            }

            return true;
        }
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 80 == 0;
    }
}
