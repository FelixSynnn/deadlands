package vectorwing.farmersdelight.common;

import java.util.HashMap;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.DispenserBlock;
import vectorwing.farmersdelight.common.registry.ModItems;

public class CommonSetup {
    public static void init() {
        registerDispenserBehaviors();
        registerItemSetAdditions();
    }

    public static void registerDispenserBehaviors() {
        DispenserBlock.registerProjectileBehavior(ModItems.ROTTEN_TOMATO.get());
    }

    public static void registerItemSetAdditions() {
        HashMap<Item, Integer> foodPoints = new HashMap<>(Villager.FOOD_POINTS);
        foodPoints.put(ModItems.CABBAGE.get(), 1);
        foodPoints.put(ModItems.TOMATO.get(), 1);
        foodPoints.put(ModItems.ONION.get(), 1);
        foodPoints.put(ModItems.RICE.get(), 2);
        Villager.FOOD_POINTS = foodPoints;
    }
}
