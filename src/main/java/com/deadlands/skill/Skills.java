package com.deadlands.skill;

import com.deadlands.Deadlands;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * Skill experience and what each level is worth. Other systems call {@link #award} when a survivor
 * does something a skill is about, and the bonus methods where the skill matters.
 */
public final class Skills {
	public static final AttachmentType<SkillData> DATA = AttachmentRegistry.create(Deadlands.id("skills"),
		builder -> builder.persistent(SkillData.CODEC).initializer(SkillData::new).copyOnDeath());

	// Experience for the things survivors do.
	public static final int XP_KILL = 10;
	public static final int XP_WEAPON_KILL = 12;
	public static final int XP_MINUTE_ALIVE = 2;
	public static final int XP_TREATMENT = 15;
	public static final int XP_CONTAINER = 8;
	public static final int XP_POWER_RESTORED = 120;

	private Skills() {
	}

	public static void init() {
		// Class loading registers the attachment.
	}

	public static SkillData get(Player player) {
		return player.getAttachedOrCreate(DATA);
	}

	public static int level(Player player, Skill skill) {
		return get(player).level(skill);
	}

	/** Give experience; tells the survivor when a level is gained. */
	public static void award(ServerPlayer player, Skill skill, int xp) {
		if (player.isCreative() || player.isSpectator()) {
			return;
		}
		int level = get(player).add(skill, xp);
		com.deadlands.network.DLNetworking.sendSkills(player);
		if (level > 0) {
			player.displayClientMessage(Component.translatable("message.deadlands.skill.level_up", Component.translatable(skill.translationKey()), level), false);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 0.8F);
		}
	}

	// ------------------------------------------------------------------------------------ bonuses

	/** SURVIVALIST: thirst and fatigue drain 3% slower per level. */
	public static float drainFactor(Player player) {
		return 1.0F - 0.03F * level(player, Skill.SURVIVALIST);
	}

	/** SURVIVALIST: 2 kg more carrying capacity per level. */
	public static float extraCapacity(Player player) {
		return 2.0F * level(player, Skill.SURVIVALIST);
	}

	/** MEDIC: healing from bandages and kits is 10% stronger per level. */
	public static float healingFactor(Player player) {
		return 1.0F + 0.10F * level(player, Skill.MEDIC);
	}

	/** GUNSLINGER: firearms do 5% more damage per level. */
	public static float firearmDamageFactor(Player player) {
		return 1.0F + 0.05F * level(player, Skill.GUNSLINGER);
	}

	/** GUNSLINGER: 3% less time between shots per level. */
	public static float firearmCooldownFactor(Player player) {
		return 1.0F - 0.03F * level(player, Skill.GUNSLINGER);
	}

	/** ENGINEER: 8% chance per level that starting a generator does not use up the fuse. */
	public static float keepFuseChance(Player player) {
		return 0.08F * level(player, Skill.ENGINEER);
	}

	/** SCAVENGER: 4% chance per level of an extra roll when a container's loot is first rolled. */
	public static float extraLootChance(Player player) {
		return 0.04F * level(player, Skill.SCAVENGER);
	}
}
