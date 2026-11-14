package com.deadlands.entity.ai;

import com.deadlands.config.ConfigManager;
import com.deadlands.config.DeadlandsConfig;
import com.deadlands.item.FlashlightItem;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

/**
 * Hiding in the dark, shared by every hostile DEADLANDS creature (config section {@code stealth}).
 *
 * <p>The light at the survivor (block light, or sky light once the sky is lit) scales how far they are
 * seen: {@link DeadlandsConfig.Stealth#darkRangeFactor} at light 0 to 3, rising to the full range at light 12.
 * Crouching multiplies it by {@link DeadlandsConfig.Stealth#crouchFactor}. Sprinting, having just attacked, or
 * holding a light (a shining flashlight, a torch, a lantern, anything that glows) gives the full range back.
 * A chaser that loses sight of a survivor standing in the dark (light at most {@link DeadlandsConfig.Stealth#darkLight})
 * for {@link DeadlandsConfig.Stealth#loseSeconds} gives up the chase and searches where it last saw them.
 */
public final class Stealth {
	private Stealth() {
	}

	private static DeadlandsConfig.Stealth config() {
		return ConfigManager.get().stealth;
	}

	/** The light where the survivor stands (0 to 15), the sky counted only as lit as it really is. */
	public static int light(LivingEntity entity) {
		BlockPos at = BlockPos.containing(entity.getEyePosition());
		return entity.level().getMaxLocalRawBrightness(at);
	}

	/** Whether the survivor gives themself away whatever the light: sprinting, just attacked, holding a light. */
	public static boolean exposed(Player player) {
		if (player.isSprinting() || holdsLight(player.getMainHandItem()) || holdsLight(player.getOffhandItem())) {
			return true;
		}
		int reveal = config().attackRevealSeconds * 20;
		return reveal > 0 && player.getLastHurtMobTimestamp() > 0 && player.tickCount - player.getLastHurtMobTimestamp() < reveal;
	}

	private static boolean holdsLight(ItemStack stack) {
		if (FlashlightItem.shining(stack)) {
			return true;
		}
		return stack.getItem() instanceof BlockItem block && block.getBlock().defaultBlockState().getLightEmission() > 0;
	}

	/** The share (0 to 1) of a creature's normal sight range at which it sees {@code target}. */
	public static double rangeFactor(LivingEntity target) {
		if (!config().enabled || !(target instanceof Player player) || exposed(player)) {
			return 1.0;
		}
		double dark = config().darkRangeFactor;
		double factor = Mth.clampedMap(light(player), 3.0, 12.0, dark, 1.0);
		if (player.isCrouching()) {
			factor *= config().crouchFactor;
		}
		return factor;
	}

	/** Whether {@code target} is hidden enough for a chaser to lose them once out of its sight. */
	public static boolean inDarkness(LivingEntity target) {
		return config().enabled && target instanceof Player player && !exposed(player) && light(player) <= config().darkLight;
	}

	public static int loseTicks() {
		return config().loseSeconds * 20;
	}

	public static int searchTicks() {
		return config().searchSeconds * 20;
	}
}
