package vectorwing.farmersdelight.common.event;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class CommonEvents {
    @Deprecated
    public static void onItemUseFinished(Level level, LivingEntity livingEntity, ItemStack stack) {
        handleVanillaSoupEffects(level, livingEntity, stack);
    }

    @Deprecated
    public static void handleVanillaSoupEffects(Level level, LivingEntity livingEntity, ItemStack stack) {
    }
}
