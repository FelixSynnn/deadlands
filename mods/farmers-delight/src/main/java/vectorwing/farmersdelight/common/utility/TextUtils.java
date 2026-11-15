package vectorwing.farmersdelight.common.utility;

import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ConsumeEffect;

public class TextUtils {
    public static final MutableComponent PLACEABLE = tooltip("placeable").withStyle(ChatFormatting.DARK_GRAY).withStyle(ChatFormatting.ITALIC);
    public static final MutableComponent PLACEABLE_SNEAKING = tooltip("placeable_sneaking")
        .withStyle(ChatFormatting.DARK_GRAY)
        .withStyle(ChatFormatting.ITALIC);
    public static final MutableComponent DEBUG_ITEM = tooltip("debug_item").withStyle(ChatFormatting.RED);

    public static MutableComponent getTranslation(String key, Object... args) {
        return Component.translatable("farmersdelight." + key, args);
    }

    public static MutableComponent getTextWithType(String translationType, String translationKey, Object... args) {
        return Component.translatable(translationType + ".farmersdelight." + translationKey, args);
    }

    public static MutableComponent block(String key, Object... args) {
        return getTextWithType("block", key, args);
    }

    public static MutableComponent item(String key, Object... args) {
        return getTextWithType("item", key, args);
    }

    public static MutableComponent advancement(String key, Object... args) {
        return getTextWithType("advancements", key, args);
    }

    public static MutableComponent container(String key, Object... args) {
        return getTextWithType("container", key, args);
    }

    public static MutableComponent JEI(String key, Object... args) {
        return getTextWithType("jei", key, args);
    }

    public static MutableComponent tooltip(String key, Object... args) {
        return getTextWithType("tooltip", key, args);
    }

    public static String subtitleKey(String key, Object... args) {
        return getTextWithType("subtitles", key, args).getString();
    }

    public static void addFoodEffectTooltip(ItemStack stack, Consumer<Component> tooltipAdder, float durationFactor, float tickRate) {
        Consumable consumable = stack.get(DataComponents.CONSUMABLE);
        if (consumable != null) {
            List<ConsumeEffect> consumeEffectList = consumable.onConsumeEffects();
            List<Pair<Holder<Attribute>, AttributeModifier>> attributeList = new ArrayList<>();

            for (ConsumeEffect possibleConsumeEffect : consumeEffectList) {
                if (possibleConsumeEffect instanceof ApplyStatusEffectsConsumeEffect statusEffectsEffect) {
                    for (MobEffectInstance instance : statusEffectsEffect.effects()) {
                        MutableComponent mutableComponent = Component.translatable(instance.getDescriptionId());
                        MobEffect effect = instance.getEffect().value();
                        effect.createModifiers(
                            instance.getAmplifier(), (attributeHolder, attributeModifier) -> attributeList.add(new Pair<>(attributeHolder, attributeModifier))
                        );
                        if (instance.getAmplifier() > 0) {
                            mutableComponent = Component.translatable(
                                "potion.withAmplifier", mutableComponent, Component.translatable("potion.potency." + instance.getAmplifier())
                            );
                        }

                        if (instance.getDuration() > 20) {
                            mutableComponent = Component.translatable(
                                "potion.withDuration", mutableComponent, MobEffectUtil.formatDuration(instance, durationFactor, tickRate)
                            );
                        }

                        tooltipAdder.accept(mutableComponent.withStyle(effect.getCategory().getTooltipFormatting()));
                    }
                }
            }

            if (!attributeList.isEmpty()) {
                tooltipAdder.accept(CommonComponents.EMPTY);
                tooltipAdder.accept(Component.translatable("potion.whenDrank").withStyle(ChatFormatting.DARK_PURPLE));

                for (Pair<Holder<Attribute>, AttributeModifier> pair : attributeList) {
                    AttributeModifier attributemodifier = pair.getSecond();
                    double amount = attributemodifier.amount();
                    double formattedAmount;
                    if (attributemodifier.operation() != Operation.ADD_MULTIPLIED_BASE && attributemodifier.operation() != Operation.ADD_MULTIPLIED_TOTAL) {
                        formattedAmount = attributemodifier.amount();
                    } else {
                        formattedAmount = attributemodifier.amount() * 100.0;
                    }

                    if (amount > 0.0) {
                        tooltipAdder.accept(
                            Component.translatable(
                                    "attribute.modifier.plus." + attributemodifier.operation().id(),
                                    ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(formattedAmount),
                                    Component.translatable(pair.getFirst().value().getDescriptionId())
                                )
                                .withStyle(ChatFormatting.BLUE)
                        );
                    } else if (amount < 0.0) {
                        formattedAmount *= -1.0;
                        tooltipAdder.accept(
                            Component.translatable(
                                    "attribute.modifier.take." + attributemodifier.operation().id(),
                                    ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(formattedAmount),
                                    Component.translatable(pair.getFirst().value().getDescriptionId())
                                )
                                .withStyle(ChatFormatting.RED)
                        );
                    }
                }
            }
        }
    }
}
