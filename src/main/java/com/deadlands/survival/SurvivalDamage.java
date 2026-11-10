package com.deadlands.survival;

import com.deadlands.Deadlands;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;

/**
 * The ways the survival systems hurt (damage types are data: {@code data/deadlands/damage_type}).
 * They ignore armour. On the STORY preset they never take the last half heart.
 */
public final class SurvivalDamage {
	public static final ResourceKey<DamageType> DEHYDRATION = key("dehydration");
	public static final ResourceKey<DamageType> HYPOTHERMIA = key("hypothermia");
	public static final ResourceKey<DamageType> HEATSTROKE = key("heatstroke");
	public static final ResourceKey<DamageType> BLEEDING = key("bleeding");
	public static final ResourceKey<DamageType> INFECTION = key("infection");

	private SurvivalDamage() {
	}

	private static ResourceKey<DamageType> key(String name) {
		return ResourceKey.create(Registries.DAMAGE_TYPE, Deadlands.id(name));
	}

	/** True for damage dealt by the survival systems themselves (it never causes new injuries). */
	public static boolean isSurvival(DamageSource source) {
		return source.is(DEHYDRATION) || source.is(HYPOTHERMIA) || source.is(HEATSTROKE) || source.is(BLEEDING) || source.is(INFECTION);
	}

	/** Hurt a survivor. Returns false if nothing happened (STORY protection, or the game refused). */
	public static boolean hurt(ServerPlayer player, ResourceKey<DamageType> type, float amount) {
		if (!SurvivalRules.lethal()) {
			amount = Math.min(amount, player.getHealth() - 1.0F);
			if (amount <= 0.0F) {
				return false;
			}
		}
		ServerLevel level = player.level();
		DamageSource source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(type));
		return player.hurtServer(level, source, amount);
	}
}
