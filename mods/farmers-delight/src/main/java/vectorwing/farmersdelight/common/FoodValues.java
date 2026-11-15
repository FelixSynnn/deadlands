package vectorwing.farmersdelight.common;

import java.util.Map;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.food.FoodProperties.Builder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import vectorwing.farmersdelight.common.item.component.consumable.ExtinguishConsumeEffect;
import vectorwing.farmersdelight.common.item.component.consumable.HealConsumeEffect;
import vectorwing.farmersdelight.common.item.component.consumable.RemoveRandomStatusEffectsConsumeEffect;
import vectorwing.farmersdelight.common.registry.ModEffects;
import vectorwing.farmersdelight.refabricated.FDRefabricatedTags;

public class FoodValues {
    public static final int BRIEF_DURATION = 600;
    public static final int SHORT_DURATION = 1200;
    public static final int MEDIUM_DURATION = 3600;
    public static final int LONG_DURATION = 6000;
    public static final FoodProperties CABBAGE = new Builder().nutrition(2).saturationModifier(0.4F).build();
    public static final FoodProperties TOMATO = new Builder().nutrition(1).saturationModifier(0.3F).build();
    public static final FoodProperties ONION = new Builder().nutrition(2).saturationModifier(0.4F).build();
    public static final FoodProperties APPLE_CIDER = new Builder().alwaysEdible().build();
    public static final FoodProperties FRIED_EGG = new Builder().nutrition(4).saturationModifier(0.4F).build();
    public static final FoodProperties TOMATO_SAUCE = new Builder().nutrition(4).saturationModifier(0.4F).build();
    public static final FoodProperties WHEAT_DOUGH = new Builder().nutrition(2).saturationModifier(0.3F).build();
    public static final FoodProperties RAW_PASTA = new Builder().nutrition(2).saturationModifier(0.3F).build();
    public static final FoodProperties PIE_CRUST = new Builder().nutrition(2).saturationModifier(0.2F).build();
    public static final FoodProperties PUMPKIN_SLICE = new Builder().nutrition(3).saturationModifier(0.3F).build();
    public static final FoodProperties CABBAGE_LEAF = new Builder().nutrition(1).saturationModifier(0.4F).build();
    public static final FoodProperties MINCED_BEEF = new Builder().nutrition(2).saturationModifier(0.3F).build();
    public static final FoodProperties BEEF_PATTY = new Builder().nutrition(4).saturationModifier(0.8F).build();
    public static final FoodProperties CHICKEN_CUTS = new Builder().nutrition(1).saturationModifier(0.3F).build();
    public static final FoodProperties COOKED_CHICKEN_CUTS = new Builder().nutrition(3).saturationModifier(0.6F).build();
    public static final FoodProperties BACON = new Builder().nutrition(2).saturationModifier(0.3F).build();
    public static final FoodProperties COOKED_BACON = new Builder().nutrition(4).saturationModifier(0.8F).build();
    public static final FoodProperties COD_SLICE = new Builder().nutrition(1).saturationModifier(0.1F).build();
    public static final FoodProperties COOKED_COD_SLICE = new Builder().nutrition(3).saturationModifier(0.5F).build();
    public static final FoodProperties SALMON_SLICE = new Builder().nutrition(1).saturationModifier(0.1F).build();
    public static final FoodProperties COOKED_SALMON_SLICE = new Builder().nutrition(3).saturationModifier(0.8F).build();
    public static final FoodProperties MUTTON_CHOPS = new Builder().nutrition(1).saturationModifier(0.3F).build();
    public static final FoodProperties COOKED_MUTTON_CHOPS = new Builder().nutrition(3).saturationModifier(0.8F).build();
    public static final FoodProperties HAM = new Builder().nutrition(5).saturationModifier(0.3F).build();
    public static final FoodProperties SMOKED_HAM = new Builder().nutrition(10).saturationModifier(0.8F).build();
    public static final FoodProperties POPSICLE = new Builder().nutrition(3).saturationModifier(0.2F).alwaysEdible().build();
    public static final FoodProperties COOKIES = new Builder().nutrition(2).saturationModifier(0.1F).build();
    public static final FoodProperties CAKE_SLICE = new Builder().nutrition(2).saturationModifier(0.1F).build();
    public static final FoodProperties PIE_SLICE = new Builder().nutrition(3).saturationModifier(0.3F).build();
    public static final FoodProperties FRUIT_SALAD = new Builder().nutrition(6).saturationModifier(0.6F).build();
    public static final FoodProperties GLOW_BERRY_CUSTARD = new Builder().nutrition(7).saturationModifier(0.6F).alwaysEdible().build();
    public static final FoodProperties MIXED_SALAD = new Builder().nutrition(6).saturationModifier(0.6F).build();
    public static final FoodProperties NETHER_SALAD = new Builder().nutrition(5).saturationModifier(0.4F).build();
    public static final FoodProperties BARBECUE_STICK = new Builder().nutrition(8).saturationModifier(0.9F).build();
    public static final FoodProperties EGG_SANDWICH = new Builder().nutrition(8).saturationModifier(0.8F).build();
    public static final FoodProperties CHICKEN_SANDWICH = new Builder().nutrition(10).saturationModifier(0.8F).build();
    public static final FoodProperties HAMBURGER = new Builder().nutrition(11).saturationModifier(0.8F).build();
    public static final FoodProperties BACON_SANDWICH = new Builder().nutrition(10).saturationModifier(0.8F).build();
    public static final FoodProperties MUTTON_WRAP = new Builder().nutrition(10).saturationModifier(0.8F).build();
    public static final FoodProperties DUMPLINGS = new Builder().nutrition(8).saturationModifier(0.8F).build();
    public static final FoodProperties STUFFED_POTATO = new Builder().nutrition(10).saturationModifier(0.7F).build();
    public static final FoodProperties CABBAGE_ROLLS = new Builder().nutrition(5).saturationModifier(0.5F).build();
    public static final FoodProperties SALMON_ROLL = new Builder().nutrition(7).saturationModifier(0.6F).build();
    public static final FoodProperties COD_ROLL = new Builder().nutrition(7).saturationModifier(0.6F).build();
    public static final FoodProperties KELP_ROLL = new FoodProperties(12, 12.0F, false);
    public static final FoodProperties KELP_ROLL_SLICE = new Builder().nutrition(6).saturationModifier(0.5F).build();
    public static final FoodProperties COOKED_RICE = new Builder().nutrition(6).saturationModifier(0.4F).build();
    public static final FoodProperties BONE_BROTH = new Builder().nutrition(8).saturationModifier(0.7F).build();
    public static final FoodProperties BEEF_STEW = new Builder().nutrition(12).saturationModifier(0.8F).build();
    public static final FoodProperties VEGETABLE_SOUP = new Builder().nutrition(12).saturationModifier(0.8F).build();
    public static final FoodProperties FISH_STEW = new Builder().nutrition(12).saturationModifier(0.8F).build();
    public static final FoodProperties CHICKEN_SOUP = new Builder().nutrition(14).saturationModifier(0.75F).build();
    public static final FoodProperties FRIED_RICE = new Builder().nutrition(14).saturationModifier(0.75F).build();
    public static final FoodProperties PUMPKIN_SOUP = new Builder().nutrition(14).saturationModifier(0.75F).build();
    public static final FoodProperties BAKED_COD_STEW = new Builder().nutrition(14).saturationModifier(0.75F).build();
    public static final FoodProperties NOODLE_SOUP = new Builder().nutrition(14).saturationModifier(0.75F).build();
    public static final FoodProperties BACON_AND_EGGS = new Builder().nutrition(10).saturationModifier(0.6F).build();
    public static final FoodProperties RATATOUILLE = new Builder().nutrition(10).saturationModifier(0.6F).build();
    public static final FoodProperties STEAK_AND_POTATOES = new Builder().nutrition(12).saturationModifier(0.8F).build();
    public static final FoodProperties PASTA_WITH_MEATBALLS = new Builder().nutrition(12).saturationModifier(0.8F).build();
    public static final FoodProperties PASTA_WITH_MUTTON_CHOP = new Builder().nutrition(12).saturationModifier(0.8F).build();
    public static final FoodProperties MUSHROOM_RICE = new Builder().nutrition(12).saturationModifier(0.8F).build();
    public static final FoodProperties ROASTED_MUTTON_CHOPS = new Builder().nutrition(14).saturationModifier(0.75F).build();
    public static final FoodProperties VEGETABLE_NOODLES = new Builder().nutrition(14).saturationModifier(0.75F).build();
    public static final FoodProperties SQUID_INK_PASTA = new Builder().nutrition(14).saturationModifier(0.75F).build();
    public static final FoodProperties GRILLED_SALMON = new Builder().nutrition(14).saturationModifier(0.75F).build();
    public static final FoodProperties ROAST_CHICKEN = new Builder().nutrition(14).saturationModifier(0.75F).build();
    public static final FoodProperties STUFFED_PUMPKIN = new Builder().nutrition(14).saturationModifier(0.75F).build();
    public static final FoodProperties HONEY_GLAZED_HAM = new Builder().nutrition(14).saturationModifier(0.75F).build();
    public static final FoodProperties SHEPHERDS_PIE = new Builder().nutrition(14).saturationModifier(0.75F).build();
    public static final FoodProperties GLEAMING_SALAD = new Builder().nutrition(14).saturationModifier(0.75F).build();
    public static final FoodProperties DOG_FOOD = new Builder().nutrition(4).saturationModifier(0.2F).build();
    public static final FoodProperties RABBIT_STEW_BUFF = new Builder().nutrition(14).saturationModifier(0.75F).build();

    public static MobEffectInstance nourishment(int duration) {
        return new MobEffectInstance(ModEffects.NOURISHMENT, duration, 0, false, false);
    }

    public static class ConsumableValues {
        public static final Consumable FAST_FOOD = Consumables.defaultFood().consumeSeconds(0.8F).build();
        public static final Consumable HUNGER_INDUCING_FOOD = Consumables.defaultFood()
            .onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.HUNGER, 600, 0), 0.3F))
            .build();
        public static final Consumable NOURISHMENT_BRIEF_DURATION = Consumables.defaultFood()
            .onConsume(new ApplyStatusEffectsConsumeEffect(FoodValues.nourishment(600)))
            .build();
        public static final Consumable NOURISHMENT_SHORT_DURATION = Consumables.defaultFood()
            .onConsume(new ApplyStatusEffectsConsumeEffect(FoodValues.nourishment(1200)))
            .build();
        public static final Consumable NOURISHMENT_MEDIUM_DURATION = Consumables.defaultFood()
            .onConsume(new ApplyStatusEffectsConsumeEffect(FoodValues.nourishment(3600)))
            .build();
        public static final Consumable NOURISHMENT_LONG_DURATION = Consumables.defaultFood()
            .onConsume(new ApplyStatusEffectsConsumeEffect(FoodValues.nourishment(6000)))
            .build();
        public static final Consumable BONE_BROTH = Consumables.defaultDrink()
            .onConsume(new ApplyStatusEffectsConsumeEffect(FoodValues.nourishment(1200)))
            .build();
        public static final Consumable HOT_COCOA = Consumables.defaultDrink()
            .onConsume(new RemoveRandomStatusEffectsConsumeEffect(FDRefabricatedTags.MobEffects.HOT_COCOA_IGNORED, true))
            .build();
        public static final Consumable MILK_BOTTLE = Consumables.defaultDrink()
            .onConsume(new RemoveRandomStatusEffectsConsumeEffect(FDRefabricatedTags.MobEffects.MILK_BOTTLE_IGNORED))
            .build();
        public static final Consumable MELON_JUICE = Consumables.defaultDrink().onConsume(new HealConsumeEffect(2.0F)).build();
        public static final Consumable MELON_POPSICLE = Consumables.defaultFood().consumeSeconds(0.8F).onConsume(new ExtinguishConsumeEffect()).build();
        public static final Consumable APPLE_CIDER = Consumables.defaultDrink()
            .onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.ABSORPTION, 1200, 0)))
            .build();
        public static final Consumable CAKE_SLICE = Consumables.defaultFood()
            .onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.SPEED, 400, 0, false, false), 1.0F))
            .build();
        public static final Consumable PIE_SLICE = Consumables.defaultFood()
            .onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.SPEED, 600, 0, false, false), 1.0F))
            .build();
        public static final Consumable CHICKEN_CUTS = Consumables.defaultFood()
            .consumeSeconds(0.8F)
            .onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.HUNGER, 600, 0), 0.3F))
            .build();
        public static final Consumable GLOW_BERRY_CUSTARD = Consumables.defaultDrink()
            .onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0)))
            .build();
        public static final Consumable FRUIT_SALAD = Consumables.defaultFood()
            .onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0), 1.0F))
            .build();
        public static final Consumable NETHER_SALAD = Consumables.defaultFood()
            .onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.NAUSEA, 240, 0), 0.3F))
            .build();
        public static final Consumable MIXED_SALAD = Consumables.defaultFood()
            .onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0), 1.0F))
            .build();
        public static final Consumable KELP_ROLL = Consumables.defaultFood().consumeSeconds(2.4F).build();
        public static final Map<Item, ApplyStatusEffectsConsumeEffect> VANILLA_SOUP_EFFECTS = new com.google.common.collect.ImmutableMap.Builder()
            .put(Items.MUSHROOM_STEW, new ApplyStatusEffectsConsumeEffect(FoodValues.nourishment(3600)))
            .put(Items.BEETROOT_SOUP, new ApplyStatusEffectsConsumeEffect(FoodValues.nourishment(3600)))
            .put(Items.RABBIT_STEW, new ApplyStatusEffectsConsumeEffect(FoodValues.nourishment(6000)))
            .build();
    }
}
