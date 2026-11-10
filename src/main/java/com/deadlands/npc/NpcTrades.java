package com.deadlands.npc;

import com.deadlands.registry.DLNests;
import com.deadlands.registry.DLSurvivalItems;
import com.deadlands.registry.DLVehicles;
import com.deadlands.registry.DLWeapons;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * What survivors trade. Money is gone; 9 mm rounds are what everyone accepts. Each role has a list
 * of things it sells for rounds and things it pays rounds for, and each survivor offers a random
 * six or so of them, with limited stock that does not refill.
 */
public final class NpcTrades {
	private NpcTrades() {
	}

	private record Line(Item item, int count, int rounds, boolean sells, int stock) {
	}

	private static Line sell(Item item, int count, int rounds, int stock) {
		return new Line(item, count, rounds, true, stock);
	}

	private static Line buy(Item item, int count, int rounds, int stock) {
		return new Line(item, count, rounds, false, stock);
	}

	private static List<Line> lines(NpcRole role) {
		return switch (role) {
			case DOCTOR -> List.of(sell(DLSurvivalItems.BANDAGE, 2, 4, 6), sell(DLSurvivalItems.ANTISEPTIC, 1, 6, 4), sell(DLSurvivalItems.ANTIBIOTICS, 1, 10, 3),
				sell(DLSurvivalItems.PAINKILLERS, 1, 5, 4), sell(DLSurvivalItems.SPLINT, 1, 6, 3), sell(DLSurvivalItems.MEDKIT, 1, 24, 1),
				sell(DLSurvivalItems.ANTIVIRAL, 1, 48, 1), buy(DLNests.INFECTED_TISSUE, 1, 8, 6), buy(DLSurvivalItems.DIRTY_RAG, 4, 2, 6));
			case MECHANIC -> List.of(sell(DLVehicles.ENGINE_PARTS, 1, 12, 4), sell(DLVehicles.TIRE, 1, 9, 4), sell(DLVehicles.CAR_BATTERY, 1, 18, 1),
				sell(DLVehicles.BODY_PANEL, 1, 6, 6), sell(DLVehicles.WRENCH, 1, 8, 1), sell(DLVehicles.FUEL_CAN, 1, 10, 2),
				sell(DLWeapons.CROWBAR, 1, 10, 1), buy(DLVehicles.ENGINE_PARTS, 1, 5, 6), buy(DLVehicles.TIRE, 1, 4, 6));
			case FARMER -> List.of(sell(DLSurvivalItems.CANNED_BEANS, 2, 5, 6), sell(DLSurvivalItems.CANNED_SOUP, 2, 5, 6), sell(DLSurvivalItems.JERKY, 3, 4, 6),
				sell(DLSurvivalItems.WATER_BOTTLE, 2, 3, 8), sell(DLSurvivalItems.CANNED_FRUIT, 2, 4, 4), buy(DLSurvivalItems.EMPTY_BOTTLE, 4, 1, 8),
				buy(DLSurvivalItems.PURIFICATION_TABLET, 2, 3, 6));
			case RESEARCHER -> List.of(sell(DLSurvivalItems.ANTIVIRAL, 1, 40, 1), sell(DLSurvivalItems.ANTIBIOTICS, 1, 9, 3), sell(DLSurvivalItems.MEDKIT, 1, 22, 1),
				sell(DLSurvivalItems.ANTISEPTIC, 1, 5, 4), sell(DLSurvivalItems.PURIFICATION_TABLET, 3, 4, 6), buy(DLNests.INFECTED_TISSUE, 1, 12, 8),
				buy(DLSurvivalItems.EMPTY_BOTTLE, 4, 1, 6));
			case WANDERER -> List.of(sell(DLWeapons.PISTOL, 1, 40, 1), sell(DLWeapons.SHOTGUN_SHELL, 6, 10, 3), sell(DLWeapons.RIFLE_ROUND, 4, 10, 3),
				sell(DLSurvivalItems.BACKPACK, 1, 30, 1), sell(DLSurvivalItems.ENERGY_DRINK, 1, 4, 4), sell(DLSurvivalItems.MRE, 1, 6, 4),
				sell(DLWeapons.MACHETE, 1, 18, 1), buy(DLNests.INFECTED_TISSUE, 1, 6, 4), buy(DLSurvivalItems.MEDKIT, 1, 12, 2),
				buy(DLVehicles.CAR_BATTERY, 1, 9, 2));
			default -> List.of(sell(DLSurvivalItems.WATER_BOTTLE, 2, 3, 8), sell(DLSurvivalItems.CANNED_BEANS, 2, 6, 6), sell(DLSurvivalItems.CRACKERS, 3, 3, 6),
				sell(DLSurvivalItems.BANDAGE, 1, 3, 4), sell(DLSurvivalItems.PURIFICATION_TABLET, 2, 4, 6), sell(DLWeapons.BASEBALL_BAT, 1, 9, 1),
				sell(DLSurvivalItems.EMPTY_BOTTLE, 2, 1, 6), sell(DLWeapons.SHOTGUN_SHELL, 4, 8, 3), buy(DLSurvivalItems.CANNED_SOUP, 2, 3, 6),
				buy(DLSurvivalItems.JERKY, 3, 2, 6), buy(DLNests.INFECTED_TISSUE, 1, 5, 4));
		};
	}

	public static MerchantOffers offers(NpcRole role, RandomSource random) {
		List<Line> pool = new ArrayList<>(lines(role));
		MerchantOffers offers = new MerchantOffers();
		int wanted = Math.min(pool.size(), 5 + random.nextInt(3));
		for (int i = 0; i < wanted; i++) {
			Line line = pool.remove(random.nextInt(pool.size()));
			offers.add(line.sells()
				? new MerchantOffer(new ItemCost(DLWeapons.PISTOL_AMMO, line.rounds()), new ItemStack(line.item(), line.count()), line.stock(), 0, 0.0F)
				: new MerchantOffer(new ItemCost(line.item(), line.count()), new ItemStack(DLWeapons.PISTOL_AMMO, line.rounds()), line.stock(), 0, 0.0F));
		}
		return offers;
	}
}
