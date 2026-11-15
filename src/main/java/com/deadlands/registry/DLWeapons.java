package com.deadlands.registry;

import com.deadlands.item.FirearmItem;
import com.deadlands.item.MeleeItem;
import java.util.Set;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;

/**
 * Weapons (Milestone 5). Call after {@link DLItems}.
 *
 * <p>Melee weapons are sword-like items: damage and swing speed as attributes, durability from
 * the iron tier. Damage per hit: crowbar 6 (fast), baseball bat 5 (fastest), machete 7, fire axe 9
 * (slow).
 *
 * <p>Firearms ({@link FirearmItem}): the pistol does 9 a shot to 48 blocks, about two shots a
 * second; the shotgun fires 7 pellets of 4 that spread, deadly close and useless far, one shot a
 * second; the hunting rifle does 18 to 96 blocks with a second and a half between shots. All of
 * them bring everything running.
 */
public final class DLWeapons {
	public static final Item CROWBAR = melee("crowbar", 3.0F, -2.2F);
	public static final Item BASEBALL_BAT = melee("baseball_bat", 2.0F, -2.0F);
	public static final Item MACHETE = melee("machete", 4.0F, -2.4F);
	/** A weapon and a woodcutter: it mines logs, planks and other wooden blocks like an iron axe, and drops them. */
	public static final Item FIRE_AXE = DLItems.register("fire_axe", MeleeItem::new, new Item.Properties().axe(ToolMaterial.IRON, 6.0F, -3.0F), DLTab.WEAPONS);
	// Atmosphere overhaul: knives, a worn-out machete and what people made weapons of.
	public static final Item SURVIVAL_KNIFE = melee("survival_knife", 2.0F, -1.6F);
	public static final Item HUNTING_KNIFE = melee("hunting_knife", 2.5F, -1.8F);
	public static final Item RUSTY_MACHETE = DLItems.register("rusty_machete", MeleeItem::new,
		new Item.Properties().sword(ToolMaterial.IRON, 3.5F, -2.5F).durability(140), DLTab.WEAPONS);
	public static final Item NAIL_BAT = melee("nail_bat", 3.0F, -2.2F);
	public static final Item LEAD_PIPE = melee("lead_pipe", 3.0F, -2.4F);
	public static final Item PIPE_WRENCH = melee("pipe_wrench", 4.5F, -2.8F);

	public static final Item PISTOL_AMMO = DLItems.register("pistol_ammo", Item::new, new Item.Properties(), DLTab.AMMUNITION);
	public static final Item SHOTGUN_SHELL = DLItems.register("shotgun_shell", Item::new, new Item.Properties(), DLTab.AMMUNITION);
	public static final Item RIFLE_ROUND = DLItems.register("rifle_round", Item::new, new Item.Properties(), DLTab.AMMUNITION);

	public static final Item PISTOL = firearm("pistol", 400,
		new FirearmItem.Spec(9.0F, 1, 0.0F, 48.0, 9, 12, 30, () -> PISTOL_AMMO, DLSounds.WEAPON_PISTOL_SHOT));
	public static final Item SHOTGUN = firearm("shotgun", 250,
		new FirearmItem.Spec(4.0F, 7, 0.09F, 24.0, 20, 5, 50, () -> SHOTGUN_SHELL, DLSounds.WEAPON_SHOTGUN_SHOT));
	public static final Item HUNTING_RIFLE = firearm("hunting_rifle", 300,
		new FirearmItem.Spec(18.0F, 1, 0.0F, 96.0, 30, 4, 45, () -> RIFLE_ROUND, DLSounds.WEAPON_RIFLE_SHOT));

	private static final Set<Item> ALL = Set.of(CROWBAR, BASEBALL_BAT, MACHETE, FIRE_AXE, SURVIVAL_KNIFE, HUNTING_KNIFE, RUSTY_MACHETE, NAIL_BAT,
		LEAD_PIPE, PIPE_WRENCH, PISTOL, SHOTGUN, HUNTING_RIFLE);

	private DLWeapons() {
	}

	/** Attack damage is {@code bonus} + the tier's 2 + the fist's 1; swing speed is 4 + {@code speed}. */
	private static Item melee(String name, float bonus, float speed) {
		return DLItems.register(name, MeleeItem::new, new Item.Properties().sword(ToolMaterial.IRON, bonus, speed), DLTab.WEAPONS);
	}

	private static Item firearm(String name, int durability, FirearmItem.Spec spec) {
		return DLItems.register(name, properties -> new FirearmItem(spec, properties), new Item.Properties().stacksTo(1).durability(durability), DLTab.WEAPONS);
	}

	public static boolean isWeapon(ItemStack stack) {
		return ALL.contains(stack.getItem());
	}

	public static void init() {
		// Class loading registers everything above.
	}
}
