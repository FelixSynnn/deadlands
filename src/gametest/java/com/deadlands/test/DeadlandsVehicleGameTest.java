package com.deadlands.test;

import com.deadlands.Deadlands;
import com.deadlands.block.DecorKind;
import com.deadlands.item.FuelCanItem;
import com.deadlands.registry.DLDecor;
import com.deadlands.registry.DLVehicles;
import com.deadlands.vehicle.FuelPumpBlockEntity;
import com.deadlands.vehicle.VehicleEntity;
import com.deadlands.vehicle.VehicleKind;
import com.deadlands.vehicle.VehicleParts;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.phys.AABB;

/**
 * Milestone 6 checks: the vehicle kinds and their assets, condition and restoration, fuel cans and
 * pumps, and saving a vehicle with its cargo.
 *
 * <p>Written without being run (the project owner asked for no test runs).
 */
public class DeadlandsVehicleGameTest {
	@GameTest
	public void vehicleKindsAreRegisteredWithTextures(GameTestHelper helper) {
		ModContainer mod = FabricLoader.getInstance().getModContainer(Deadlands.MOD_ID).orElseThrow();
		helper.assertValueEqual(VehicleKind.values().length, 12, "twelve vehicles");
		for (VehicleKind kind : VehicleKind.values()) {
			helper.assertTrue(BuiltInRegistries.ENTITY_TYPE.getValue(Deadlands.id(kind.id())) == DLVehicles.type(kind), "entity type registered: " + kind.id());
			helper.assertValueEqual(VehicleKind.of(DLVehicles.type(kind)), kind, "the type maps back to its kind");
			for (String finish : new String[] {"faded", "rusted", "clean", "burned"}) {
				String path = "assets/deadlands/textures/entity/vehicle/" + kind.id() + "_" + finish + ".png";
				helper.assertTrue(mod.findPath(path).isPresent(), "Missing texture " + path);
			}
			helper.assertTrue(kind.seats() >= 1 && kind.tank() > 0.0F && kind.maxSpeed() > 0.0F, "sane numbers for " + kind.id());
		}
		helper.assertTrue(VehicleKind.MOTORCYCLE.maxSpeed() > VehicleKind.BUS.maxSpeed(), "a motorcycle outruns a bus");
		helper.succeed();
	}

	@GameTest
	public void restorationMakesAVehicleDrivable(GameTestHelper helper) {
		VehicleEntity pickup = helper.spawn(DLVehicles.type(VehicleKind.PICKUP), new BlockPos(2, 1, 2));
		helper.assertTrue(pickup.drivable(), "a new vehicle runs");
		pickup.setEngine(10.0F);
		pickup.setTires(0.0F);
		pickup.setFlag(VehicleEntity.FLAG_BATTERY, false);
		pickup.setFuel(0.0F);
		helper.assertFalse(pickup.drivable(), "a stripped vehicle does not");
		helper.assertValueEqual(pickup.needs().size(), 4, "it needs an engine, tires, a battery and fuel");

		Player mechanic = helper.makeMockPlayer(GameType.SURVIVAL);
		ItemStack parts = new ItemStack(DLVehicles.ENGINE_PARTS, 4);
		VehicleParts.fit(pickup, mechanic, parts);
		helper.assertValueEqual(parts.getCount(), 4, "no wrench, nothing fitted");
		mechanic.getInventory().add(new ItemStack(DLVehicles.WRENCH));
		VehicleParts.fit(pickup, mechanic, parts);
		VehicleParts.fit(pickup, mechanic, parts);
		helper.assertValueEqual(pickup.engine(), 60.0F, "two sets of engine parts");
		ItemStack tires = new ItemStack(DLVehicles.TIRE, 4);
		VehicleParts.fit(pickup, mechanic, tires);
		VehicleParts.fit(pickup, mechanic, tires);
		VehicleParts.fit(pickup, mechanic, new ItemStack(DLVehicles.CAR_BATTERY));
		helper.assertTrue(pickup.drivable(), "engine, two tires and a battery are enough to drive");

		ItemStack can = new ItemStack(DLVehicles.FUEL_CAN);
		FuelCanItem.setLitres(can, 15.0F);
		VehicleParts.fit(pickup, mechanic, can);
		helper.assertValueEqual(pickup.fuel(), 15.0F, "the can went into the tank");
		helper.assertValueEqual(FuelCanItem.litres(can), 0.0F, "and is empty");

		pickup.setFlag(VehicleEntity.FLAG_BURNED, true);
		helper.assertFalse(pickup.drivable(), "a burned-out wreck never runs again");
		helper.succeed();
	}

	@GameTest
	public void vehiclesKeepConditionAndCargo(GameTestHelper helper) {
		VehicleEntity van = helper.spawn(DLVehicles.type(VehicleKind.DELIVERY_VAN), new BlockPos(2, 1, 2));
		van.setFuel(33.0F);
		van.setEngine(72.0F);
		van.setTires(50.0F);
		van.setBody(41.0F);
		van.setPaint(1);
		van.storage().setItem(5, new ItemStack(Items.IRON_INGOT, 7));
		var registries = helper.getLevel().registryAccess();
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
		van.saveWithoutId(output);
		CompoundTag saved = output.buildResult();
		VehicleEntity copy = DLVehicles.type(VehicleKind.DELIVERY_VAN).create(helper.getLevel(), EntitySpawnReason.LOAD);
		copy.load(TagValueInput.create(ProblemReporter.DISCARDING, registries, saved));
		helper.assertValueEqual(copy.fuel(), 33.0F, "fuel after loading");
		helper.assertValueEqual(copy.engine(), 72.0F, "engine after loading");
		helper.assertValueEqual(copy.body(), 41.0F, "body after loading");
		helper.assertValueEqual(copy.paint(), 1, "paint after loading");
		helper.assertValueEqual(copy.storage().getItem(5).getCount(), 7, "cargo after loading");
		helper.succeed();
	}

	@GameTest
	public void pumpsHoldWhatTheyHoldAndRunDry(GameTestHelper helper) {
		BlockPos rel = new BlockPos(1, 1, 1);
		helper.setBlock(rel, DLDecor.get(DecorKind.FUEL_PUMP).defaultBlockState());
		FuelPumpBlockEntity pump = helper.getBlockEntity(rel, FuelPumpBlockEntity.class);
		float first = pump.litres();
		helper.assertTrue(first >= 0.0F && first <= 150.0F, "a pump holds nothing, or 25 to 150 litres: " + first);
		helper.assertValueEqual(pump.litres(), first, "asking again gives the same answer");
		float taken = pump.take(1000.0F);
		helper.assertValueEqual(taken, first, "everything can be taken");
		helper.assertValueEqual(pump.take(10.0F), 0.0F, "and then it is dry");
		helper.succeed();
	}

	@GameTest
	public void vehiclesShowWearAndKeepTheirSwitches(GameTestHelper helper) {
		VehicleEntity sedan = helper.spawn(DLVehicles.type(VehicleKind.SEDAN), new BlockPos(2, 1, 2));
		sedan.setBody(100.0F);
		helper.assertValueEqual(sedan.damageLevel(), 0, "a whole body shows nothing");
		sedan.setBody(50.0F);
		helper.assertValueEqual(sedan.damageLevel(), 1, "dented");
		sedan.setBody(20.0F);
		helper.assertValueEqual(sedan.damageLevel(), 2, "battered");
		sedan.setFlag(VehicleEntity.FLAG_OVERGROWN, true);
		helper.assertTrue(sedan.needs().stream().anyMatch(need -> need.getContents() instanceof TranslatableContents t
			&& t.getKey().equals("vehicle.deadlands.need.overgrown")), "the growth has to come off first");
		sedan.setFlag(VehicleEntity.FLAG_LIGHTS, true);
		var registries = helper.getLevel().registryAccess();
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
		sedan.saveWithoutId(output);
		VehicleEntity copy = DLVehicles.type(VehicleKind.SEDAN).create(helper.getLevel(), EntitySpawnReason.LOAD);
		copy.load(TagValueInput.create(ProblemReporter.DISCARDING, registries, output.buildResult()));
		helper.assertTrue(copy.overgrown(), "overgrowth after loading");
		helper.assertTrue(copy.lights(), "lights after loading");
		helper.assertValueEqual(BuiltInRegistries.ITEM.getKey(DLVehicles.TOW_ROPE).getPath(), "tow_rope", "the tow rope is registered");
		ModContainer mod = FabricLoader.getInstance().getModContainer(Deadlands.MOD_ID).orElseThrow();
		for (VehicleKind kind : VehicleKind.values()) {
			for (String layer : new String[] {"damage_1", "damage_2", "overgrown", "lights"}) {
				String path = "assets/deadlands/textures/entity/vehicle/" + kind.id() + "_" + layer + ".png";
				helper.assertTrue(mod.findPath(path).isPresent(), "Missing " + path);
			}
		}
		helper.succeed();
	}

	@GameTest
	public void ruggedMarkersLeaveAVehicle(GameTestHelper helper) {
		BlockPos rel = new BlockPos(3, 1, 3);
		helper.setBlock(rel, com.deadlands.registry.DLMarkers.SPAWN_MARKER.defaultBlockState()
			.setValue(com.deadlands.block.SpawnMarkerBlock.SPAWN, com.deadlands.block.SpawnMarkerBlock.Spawn.VEHICLE_RUGGED));
		com.deadlands.block.SpawnMarkerBlock.resolve(helper.getLevel(), helper.absolutePos(rel), helper.getBlockState(rel));
		helper.assertTrue(helper.getBlockState(rel).isAir(), "the marker is gone");
		helper.assertFalse(helper.getLevel().getEntitiesOfClass(VehicleEntity.class, new AABB(helper.absolutePos(rel)).inflate(3.0)).isEmpty(),
			"a vehicle stands there");
		helper.succeed();
	}
}
