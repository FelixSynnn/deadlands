package vectorwing.farmersdelight.common.mixin.refabricated;

import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(GameRules.class)
public interface GameRulesTypeAccessor<T> {
}
