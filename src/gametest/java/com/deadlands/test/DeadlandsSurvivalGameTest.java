package com.deadlands.test;

import com.deadlands.Deadlands;
import com.deadlands.network.SurvivalPayload;
import com.deadlands.noise.NoiseSystem;
import com.deadlands.registry.DLSurvivalItems;
import com.deadlands.registry.DLWeapons;
import com.deadlands.skill.Skill;
import com.deadlands.skill.SkillData;
import com.deadlands.survival.CarryWeight;
import com.deadlands.survival.Condition;
import com.deadlands.survival.SurvivalDamage;
import com.deadlands.survival.SurvivalData;
import com.deadlands.survival.SurvivalRules;
import com.deadlands.survival.SurvivalSystem;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Milestone 5 (first part) checks: the survivor's body state, its conditions and saving, the
 * treatment rules, the survival damage types and text, and noise through walls (a Milestone 4 fix).
 *
 * <p>Written while the project owner asked for no test runs; run in every build since the
 * atmosphere overhaul.
 */
public class DeadlandsSurvivalGameTest {
	@GameTest
	public void bodyStateSurvivesSaveAndLoad(GameTestHelper helper) {
		SurvivalData body = new SurvivalData();
		body.addThirst(-70.0F);
		body.addFatigue(80.0F);
		body.setTemperature(-45.0F);
		body.addInfection(40.0F);
		body.setBleeding(2);
		body.setFracture(1200);
		body.setSickness(600);
		body.setPainkiller(300);
		body.setWinded(true);
		Tag tag = SurvivalData.CODEC.encodeStart(NbtOps.INSTANCE, body).getOrThrow();
		SurvivalData loaded = SurvivalData.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
		helper.assertValueEqual(loaded.thirst(), 30.0F, "thirst after loading");
		helper.assertValueEqual(loaded.fatigue(), 80.0F, "fatigue after loading");
		helper.assertValueEqual(loaded.temperature(), -45.0F, "temperature after loading");
		helper.assertValueEqual(loaded.infection(), 40.0F, "infection after loading");
		helper.assertValueEqual(loaded.bleeding(), 2, "bleeding after loading");
		helper.assertValueEqual(loaded.fracture(), 1200, "fracture after loading");
		helper.assertValueEqual(loaded.conditions(), body.conditions(), "conditions after loading");
		helper.assertTrue(loaded.winded(), "still winded after loading");
		helper.succeed();
	}

	@GameTest
	public void conditionsFollowTheBody(GameTestHelper helper) {
		SurvivalData body = new SurvivalData();
		helper.assertValueEqual(body.conditions(), 0, "a fresh survivor has nothing wrong");
		body.addThirst(-80.0F);
		helper.assertTrue(body.has(Condition.THIRSTY) && !body.has(Condition.DEHYDRATED), "20 thirst is thirsty, not dehydrated");
		body.addThirst(-100.0F);
		helper.assertTrue(body.has(Condition.DEHYDRATED) && !body.has(Condition.THIRSTY), "0 thirst is dehydrated");
		body.setTemperature(-70.0F);
		helper.assertTrue(body.has(Condition.FREEZING), "-70 is freezing");
		body.setTemperature(35.0F);
		helper.assertTrue(body.has(Condition.HOT) && !body.has(Condition.OVERHEATING), "35 is hot");
		body.addFatigue(95.0F);
		helper.assertTrue(body.has(Condition.EXHAUSTED), "95 fatigue is exhausted");
		helper.assertValueEqual(body.maxStamina(), 45.0F, "fatigue caps stamina");
		helper.assertTrue(body.stamina() <= body.maxStamina(), "stamina never exceeds its cap");
		body.addInfection(10.0F);
		helper.assertValueEqual(body.infectionStage(), 1, "an early infection");
		body.addInfection(30.0F);
		helper.assertValueEqual(body.infectionStage(), 2, "a feverish infection");
		body.addInfection(40.0F);
		helper.assertValueEqual(body.infectionStage(), 3, "a failing infection");
		body.setFracture(40);
		body.elapse(20);
		helper.assertTrue(body.has(Condition.FRACTURE), "a fracture lasts until its time is up");
		body.elapse(20);
		helper.assertFalse(body.has(Condition.FRACTURE), "then it has healed");
		helper.succeed();
	}

	@GameTest
	public void treatmentAndPayload(GameTestHelper helper) {
		SurvivalData body = new SurvivalData();
		helper.assertFalse(SurvivalSystem.bandage(body), "nothing to bandage");
		body.setBleeding(2);
		helper.assertTrue(SurvivalSystem.bandage(body), "a bandage stops heavy bleeding");
		helper.assertValueEqual(body.bleeding(), 0, "bleeding after a bandage");
		SurvivalSystem.sicken(body, 400);
		SurvivalSystem.sicken(body, 100);
		helper.assertValueEqual(body.sickness(), 400, "a second bad meal does not shorten the sickness");
		SurvivalPayload a = SurvivalPayload.of(body);
		helper.assertTrue(a.active() && (a.conditions() & Condition.SICK.bit()) != 0, "the payload carries the conditions");
		body.addThirst(-0.2F);
		helper.assertValueEqual(SurvivalPayload.of(body).fingerprint(), a.fingerprint(), "a change too small to see is not sent");
		body.addThirst(-5.0F);
		helper.assertTrue(SurvivalPayload.of(body).fingerprint() != a.fingerprint(), "a visible change is sent");
		helper.assertTrue(SurvivalRules.THIRST_PER_SECOND * 1800.0F >= 99.0F, "a full thirst bar lasts about half an hour standing still");
		helper.succeed();
	}

	@GameTest
	public void survivalItemsDamageTypesAndText(GameTestHelper helper) throws Exception {
		ModContainer mod = FabricLoader.getInstance().getModContainer(Deadlands.MOD_ID).orElseThrow();
		JsonObject lang = JsonParser.parseString(Files.readString(mod.findPath("assets/deadlands/lang/en_us.json").orElseThrow(), StandardCharsets.UTF_8)).getAsJsonObject();
		var damageTypes = helper.getLevel().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE);
		for (var type : new net.minecraft.resources.ResourceKey[] {SurvivalDamage.DEHYDRATION, SurvivalDamage.HYPOTHERMIA, SurvivalDamage.HEATSTROKE,
			SurvivalDamage.BLEEDING, SurvivalDamage.INFECTION}) {
			helper.assertTrue(damageTypes.get(type).isPresent(), "damage type registered: " + type.identifier());
			helper.assertTrue(lang.has("death.attack.deadlands." + type.identifier().getPath()), "death message for " + type.identifier());
		}
		for (Condition condition : Condition.values()) {
			helper.assertTrue(lang.has(condition.translationKey()), "name for condition " + condition.id());
			helper.assertTrue(lang.has(condition.translationKey() + ".hint"), "hint for condition " + condition.id());
			helper.assertTrue(mod.findPath("assets/deadlands/textures/gui/sprites/survival/" + condition.id() + ".png").isPresent(),
				"icon for condition " + condition.id());
		}
		helper.assertTrue(lang.has("item.deadlands.bandage.desc"), "survival items say what they do");
		helper.assertTrue(DLSurvivalItems.WATER_BOTTLE.getDefaultInstance().has(net.minecraft.core.component.DataComponents.CONSUMABLE), "water can be drunk");
		helper.succeed();
	}

	@GameTest
	public void skillsGrowAndSave(GameTestHelper helper) {
		helper.assertValueEqual(Skill.xpFor(1), 50, "experience for level 1");
		helper.assertValueEqual(Skill.levelFor(49), 0, "49 experience is still level 0");
		helper.assertValueEqual(Skill.levelFor(200), 2, "200 experience is level 2");
		helper.assertValueEqual(Skill.levelFor(1_000_000), Skill.MAX_LEVEL, "levels stop at the maximum");
		SkillData skills = new SkillData();
		helper.assertValueEqual(skills.add(Skill.MEDIC, 30), 0, "no level yet");
		helper.assertValueEqual(skills.add(Skill.MEDIC, 30), 1, "the level gained is reported once");
		helper.assertValueEqual(skills.add(Skill.MEDIC, 5), 0, "and not again");
		skills.add(Skill.SCAVENGER, 460);
		Tag tag = SkillData.CODEC.encodeStart(NbtOps.INSTANCE, skills).getOrThrow();
		SkillData loaded = SkillData.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
		helper.assertValueEqual(loaded.xp(Skill.MEDIC), 65, "medic experience after loading");
		helper.assertValueEqual(loaded.level(Skill.SCAVENGER), 3, "scavenger level after loading");
		helper.assertValueEqual(loaded.level(Skill.ENGINEER), 0, "untouched skills start at 0");
		helper.succeed();
	}

	@GameTest
	public void loadAndWeapons(GameTestHelper helper) {
		helper.assertValueEqual(CarryWeight.of(ItemStack.EMPTY), 0.0F, "nothing weighs nothing");
		helper.assertTrue(Math.abs(CarryWeight.of(new ItemStack(Items.STICK, 10)) - 1.0F) < 1.0E-4F, "ten sticks weigh a kilo");
		helper.assertValueEqual(CarryWeight.of(new ItemStack(Items.IRON_CHESTPLATE)), 4.0F, "armour is heavy");
		helper.assertValueEqual(CarryWeight.of(new ItemStack(DLWeapons.PISTOL)), 2.0F, "a pistol weighs two kilos");
		helper.assertValueEqual(CarryWeight.speedFactor(30.0F, 45.0F), 1.0, "under capacity costs nothing");
		helper.assertTrue(CarryWeight.speedFactor(60.0F, 45.0F) < 1.0, "over capacity slows");
		helper.assertValueEqual(CarryWeight.speedFactor(500.0F, 45.0F), 0.4, "but never below 40% speed");
		SurvivalData body = new SurvivalData();
		body.setLoad(50.0F, 45.0F);
		helper.assertTrue(body.has(Condition.ENCUMBERED), "over capacity is overloaded");
		helper.assertTrue(DLWeapons.isWeapon(new ItemStack(DLWeapons.FIRE_AXE)) && !DLWeapons.isWeapon(new ItemStack(DLWeapons.PISTOL_AMMO)),
			"weapons are weapons, rounds are not");
		helper.assertTrue(DLWeapons.PISTOL.getDefaultInstance().isDamageableItem(), "the pistol wears out");
		ItemStack rifle = new ItemStack(DLWeapons.HUNTING_RIFLE);
		helper.assertValueEqual(com.deadlands.item.ItemCondition.of(rifle), com.deadlands.item.ItemCondition.GOOD, "a new rifle is in good condition");
		rifle.setDamageValue(rifle.getMaxDamage() / 2);
		helper.assertValueEqual(com.deadlands.item.ItemCondition.of(rifle), com.deadlands.item.ItemCondition.WORN, "half worn");
		rifle.setDamageValue(rifle.getMaxDamage() * 9 / 10);
		helper.assertValueEqual(com.deadlands.item.ItemCondition.of(rifle), com.deadlands.item.ItemCondition.FAILING, "nearly finished");
		helper.assertTrue(((com.deadlands.item.FirearmItem) DLWeapons.SHOTGUN).spec().pellets() > 1, "a shotgun fires several pellets");
		helper.assertValueEqual(com.deadlands.item.FirearmItem.loaded(rifle), 0, "a found gun is empty");
		rifle.set(com.deadlands.registry.DLDataComponents.LOADED, 3);
		helper.assertValueEqual(com.deadlands.item.FirearmItem.loaded(rifle.copy()), 3, "loaded rounds stay with the gun");
		ItemStack pack = new ItemStack(DLSurvivalItems.BACKPACK);
		float empty = CarryWeight.of(pack);
		pack.set(net.minecraft.core.component.DataComponents.CONTAINER,
			net.minecraft.world.item.component.ItemContainerContents.fromItems(java.util.List.of(new ItemStack(Items.STICK, 10))));
		helper.assertTrue(Math.abs(CarryWeight.of(pack) - empty - 1.0F) < 1.0E-4F, "what is in a pack still weighs on you");
		helper.assertTrue(((com.deadlands.item.FirearmItem) DLWeapons.HUNTING_RIFLE).spec().range() > ((com.deadlands.item.FirearmItem) DLWeapons.PISTOL).spec().range(),
			"the rifle outranges the pistol");
		helper.succeed();
	}

	@GameTest
	public void wallsMuffleNoise(GameTestHelper helper) {
		Vec3 from = Vec3.atCenterOf(helper.absolutePos(new BlockPos(0, 1, 1)));
		Vec3 to = Vec3.atCenterOf(helper.absolutePos(new BlockPos(6, 1, 1)));
		helper.assertValueEqual(NoiseSystem.muffling(helper.getLevel(), from, to), 1.0, "nothing in the way");
		helper.setBlock(new BlockPos(3, 1, 1), Blocks.STONE);
		double one = NoiseSystem.muffling(helper.getLevel(), from, to);
		helper.assertTrue(Math.abs(one - (1.0 - NoiseSystem.WALL_LOSS)) < 1.0E-6, "one wall takes its share: " + one);
		helper.setBlock(new BlockPos(2, 1, 1), Blocks.STONE);
		helper.setBlock(new BlockPos(4, 1, 1), Blocks.STONE);
		helper.assertTrue(NoiseSystem.muffling(helper.getLevel(), from, to) < one, "thicker walls muffle more");
		for (int x = 1; x <= 5; x++) {
			helper.setBlock(new BlockPos(x, 1, 1), Blocks.STONE);
		}
		helper.assertTrue(NoiseSystem.muffling(helper.getLevel(), from, to) >= NoiseSystem.MIN_MUFFLING, "but never to nothing");
		helper.succeed();
	}
}
