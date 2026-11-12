package vectorwing.farmersdelight.common.mixin.refabricated;

import com.mojang.serialization.MapCodec;
import net.minecraft.stats.RecipeBookSettings.TypeSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(TypeSettings.class)
public interface RecipeBookSettingsTypeSettingsInvoker {
    @Invoker("codec")
    static MapCodec<TypeSettings> fdrf$invokeCodec(String openField, String filteringField) {
        throw new RuntimeException("");
    }
}
