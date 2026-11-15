package vectorwing.farmersdelight.common.registry;

import java.util.function.Supplier;
import net.minecraft.world.item.consume_effects.ConsumeEffect.Type;
import vectorwing.farmersdelight.common.item.component.consumable.ExtinguishConsumeEffect;
import vectorwing.farmersdelight.common.item.component.consumable.HealConsumeEffect;
import vectorwing.farmersdelight.common.item.component.consumable.RemoveRandomStatusEffectsConsumeEffect;
import vectorwing.farmersdelight.refabricated.RegUtils;

public class ModConsumeEffectTypes {
    public static final Supplier<Type<RemoveRandomStatusEffectsConsumeEffect>> REMOVE_RANDOM_EFFECTS = RegUtils.regConsumeEffectType(
        "remove_random_effects", () -> new Type<>(RemoveRandomStatusEffectsConsumeEffect.CODEC, RemoveRandomStatusEffectsConsumeEffect.STREAM_CODEC)
    );
    public static final Supplier<Type<HealConsumeEffect>> HEAL = RegUtils.regConsumeEffectType(
        "heal", () -> new Type<>(HealConsumeEffect.CODEC, HealConsumeEffect.STREAM_CODEC)
    );
    public static final Supplier<Type<ExtinguishConsumeEffect>> EXTINGUISH = RegUtils.regConsumeEffectType(
        "extinguish", () -> new Type<>(ExtinguishConsumeEffect.CODEC, ExtinguishConsumeEffect.STREAM_CODEC)
    );

    public static void touch() {
    }
}
