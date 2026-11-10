package com.deadlands.registry;

import com.deadlands.item.EmptyBottleItem;
import com.deadlands.item.SurvivalItem;
import com.deadlands.skill.Skill;
import com.deadlands.skill.Skills;
import com.deadlands.survival.Condition;
import com.deadlands.survival.SurvivalRules;
import com.deadlands.survival.SurvivalSystem;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.Consumables;

/**
 * Food, drink and medicine (Milestone 5). Call after {@link DLItems}.
 *
 * <p>Food quality is the kind of food: sealed cans and rations are safe, stale bread sometimes
 * makes you sick, a blown can usually does. Thirst and sickness come from {@link SurvivalItem}
 * uses; hunger and saturation are Minecraft's own food values.
 */
public final class DLSurvivalItems {
	private static final int BLEEDING = Condition.BLEEDING.bit() | Condition.HEAVY_BLEEDING.bit();

	// ------------------------------------------------------------------------------------ food
	public static final Item CANNED_BEANS = food("canned_beans", 7, 0.6F, (player, body) -> body.addThirst(2.0F));
	public static final Item CANNED_SOUP = food("canned_soup", 6, 0.6F, (player, body) -> body.addThirst(12.0F));
	public static final Item CANNED_FRUIT = food("canned_fruit", 4, 0.3F, (player, body) -> body.addThirst(14.0F));
	public static final Item JERKY = food("jerky", 5, 0.8F, (player, body) -> body.addThirst(-4.0F));
	public static final Item CRACKERS = food("crackers", 3, 0.3F, (player, body) -> body.addThirst(-3.0F));
	public static final Item ENERGY_BAR = food("energy_bar", 4, 0.4F, (player, body) -> body.addStamina(30.0F));
	public static final Item MRE = food("mre", 10, 0.8F, (player, body) -> body.addThirst(5.0F));
	public static final Item STALE_BREAD = food("stale_bread", 3, 0.2F, (player, body) -> {
		if (player.getRandom().nextFloat() < 0.15F * SurvivalRules.injuryChance()) {
			SurvivalSystem.sicken(body, SurvivalRules.SICKNESS_TICKS / 2);
		}
	});
	public static final Item SPOILED_CAN = food("spoiled_can", 2, 0.1F, (player, body) -> {
		if (player.getRandom().nextFloat() < 0.7F) {
			SurvivalSystem.sicken(body, SurvivalRules.SICKNESS_TICKS);
		}
	});

	// ------------------------------------------------------------------------------------ drink
	public static final Item EMPTY_BOTTLE = DLItems.register("empty_bottle", EmptyBottleItem::new, new Item.Properties().stacksTo(16), DLTab.FOOD_AND_DRINK);
	public static final Item WATER_BOTTLE = drink("water_bottle", EMPTY_BOTTLE, (player, body) -> body.addThirst(40.0F));
	public static final Item DIRTY_WATER_BOTTLE = drink("dirty_water_bottle", EMPTY_BOTTLE, (player, body) -> {
		body.addThirst(35.0F);
		if (player.getRandom().nextFloat() < 0.4F * SurvivalRules.injuryChance()) {
			SurvivalSystem.sicken(body, SurvivalRules.SICKNESS_TICKS);
		}
	});
	public static final Item SODA_CAN = drink("soda_can", null, (player, body) -> {
		body.addThirst(22.0F);
		body.addStamina(15.0F);
	});
	public static final Item ENERGY_DRINK = drink("energy_drink", null, (player, body) -> {
		body.addThirst(15.0F);
		body.addStamina(50.0F);
		body.addFatigue(-12.0F);
	});
	/** Crafted with a bottle of dirty water into clean water (a shapeless recipe). */
	public static final Item PURIFICATION_TABLET = DLItems.register("purification_tablet", Item::new, new Item.Properties().stacksTo(16), DLTab.FOOD_AND_DRINK);

	// ------------------------------------------------------------------------------------ medicine
	public static final Item BANDAGE = medicine("bandage", 16, BLEEDING, true, (player, body) -> {
		SurvivalSystem.bandage(body);
		player.heal(2.0F * Skills.healingFactor(player));
	});
	public static final Item DIRTY_RAG = medicine("dirty_rag", 16, BLEEDING, true, (player, body) -> {
		SurvivalSystem.bandage(body);
		if (SurvivalRules.infection() && player.getRandom().nextFloat() < 0.12F * SurvivalRules.injuryChance()) {
			body.addInfection(body.infected() ? 2.0F : 3.0F);
		}
	});
	public static final Item SPLINT = medicine("splint", 8, Condition.FRACTURE.bit(), true, (player, body) -> body.setFracture(0));
	/** Cleans a fresh infection away (below 15), and sets back an older one a little. */
	public static final Item ANTISEPTIC = medicine("antiseptic", 8, Condition.INFECTED.bit(), true,
		(player, body) -> body.addInfection(body.infection() < 15.0F ? -body.infection() : -8.0F));
	public static final Item ANTIBIOTICS = medicine("antibiotics", 8, Condition.SICK.bit() | Condition.INFECTED.bit(), false, (player, body) -> {
		body.setSickness(0);
		body.addInfection(-12.0F);
	});
	/** The rare one: takes 60 off an infection, which cures anything short of the last stage. */
	public static final Item ANTIVIRAL = medicine("antiviral", 4, Condition.INFECTED.bit(), true, (player, body) -> body.addInfection(-60.0F));
	public static final Item PAINKILLERS = medicine("painkillers", 8, 0, false, (player, body) -> body.setPainkiller(SurvivalRules.PAINKILLER_TICKS));
	public static final Item MEDKIT = medicine("medkit", 1, 0, true, (player, body) -> {
		SurvivalSystem.bandage(body);
		body.setFracture(0);
		player.heal(8.0F * Skills.healingFactor(player));
	});

	// ------------------------------------------------------------------------------------ gear
	/** A pack raises carrying capacity while carried (the best one counts) and holds 18 or 27 stacks of its own. */
	public static final Item BACKPACK = DLItems.register("backpack", p -> new com.deadlands.item.BackpackItem(2, 15.0F, p),
		new Item.Properties().stacksTo(1), DLTab.SURVIVAL_GEAR);
	public static final Item MILITARY_BACKPACK = DLItems.register("military_backpack", p -> new com.deadlands.item.BackpackItem(3, 25.0F, p),
		new Item.Properties().stacksTo(1), DLTab.SURVIVAL_GEAR);

	private DLSurvivalItems() {
	}

	private static Item food(String name, int nutrition, float saturation, SurvivalItem.Use use) {
		FoodProperties food = new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation).build();
		return DLItems.register(name, properties -> new SurvivalItem(use, properties), new Item.Properties().food(food), DLTab.FOOD_AND_DRINK);
	}

	private static Item drink(String name, Item emptied, SurvivalItem.Use use) {
		Item.Properties properties = new Item.Properties().stacksTo(16).component(net.minecraft.core.component.DataComponents.CONSUMABLE,
			Consumables.defaultDrink().build());
		if (emptied != null) {
			properties = properties.usingConvertsTo(emptied);
		}
		return DLItems.register(name, p -> new SurvivalItem(use, p), properties, DLTab.FOOD_AND_DRINK);
	}

	/** Applied (wrapped, swabbed, injected: two seconds, a cloth sound) or swallowed (pills). */
	private static Item medicine(String name, int stack, int treats, boolean applied, SurvivalItem.Use use) {
		Consumable consumable = applied
			? Consumable.builder().consumeSeconds(2.0F).animation(ItemUseAnimation.BRUSH).sound(SoundEvents.ARMOR_EQUIP_LEATHER).hasConsumeParticles(false).build()
			: Consumables.defaultDrink().consumeSeconds(1.2F).build();
		SurvivalItem.Use treated = (player, body) -> {
			use.apply(player, body);
			Skills.award(player, Skill.MEDIC, Skills.XP_TREATMENT);
		};
		return DLItems.register(name, p -> new SurvivalItem(treated, treats, p),
			new Item.Properties().stacksTo(stack).component(net.minecraft.core.component.DataComponents.CONSUMABLE, consumable), DLTab.MEDICAL);
	}

	public static void init() {
		// Class loading registers everything above.
	}
}
