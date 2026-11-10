package com.deadlands.survival;

import com.deadlands.Deadlands;
import com.deadlands.entity.InfectedEntity;
import com.deadlands.entity.InfectedKind;
import com.deadlands.network.SurvivalPayload;
import com.deadlands.skill.Skill;
import com.deadlands.skill.Skills;
import com.deadlands.spawning.SpawnFinder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Runs the survival rules for every survivor in survival or adventure mode (creative and
 * spectator players are left alone). Each system can be switched off in the settings.
 *
 * <p><b>Every tick:</b> stamina. Sprinting, jumping and melee swings spend it; it comes back when
 * you stop (slower when thirsty, hungry or freezing). At zero you are winded: no sprinting, and
 * weaker hits, until it is back above a quarter.
 *
 * <p><b>Every second:</b>
 * <ul>
 *   <li>thirst drains (faster sprinting, in heat, when sick); at zero you take damage;</li>
 *   <li>body temperature drifts toward the surroundings ({@link Temperature}); freezing and
 *       overheating hurt, cold burns food, heat burns water;</li>
 *   <li>fatigue builds while awake and lowers your stamina cap; sleeping clears it;</li>
 *   <li>bleeding hurts until bandaged (light bleeding can stop on its own);</li>
 *   <li>an infection advances through three stages and kills at the end unless treated;</li>
 *   <li>sickness (bad food, dirty water) brings nausea and wastes food and water;</li>
 *   <li>a fracture slows you until splinted or healed; painkillers hide it for a while;</li>
 *   <li>the load carried is weighed ({@link CarryWeight}): over capacity you slow down and tire faster.</li>
 * </ul>
 * Injuries come from what happens to you: infected hits can cause bleeding and infection, hard
 * hits bleeding, long falls a fracture. A survivor who dies of the infection gets back up as a
 * walker where they fell.
 */
public final class SurvivalSystem {
	private static final Identifier SPEED_ID = Deadlands.id("survival_speed");
	private static final Identifier DAMAGE_ID = Deadlands.id("survival_weakness");
	private static final int SYNC_INTERVAL = 10;

	/** Per-player bookkeeping that is not worth saving. */
	private static final class Track {
		double lastY;
		boolean wasOnGround = true;
		int lastFingerprint = Integer.MIN_VALUE;
	}

	private static final Map<UUID, Track> TRACKS = new HashMap<>();

	private SurvivalSystem() {
	}

	public static void init() {
		SurvivalAttachments.init();
		ServerTickEvents.END_SERVER_TICK.register(SurvivalSystem::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(SurvivalSystem::afterDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register(SurvivalSystem::afterDeath);
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (player instanceof ServerPlayer survivor && applies(survivor) && SurvivalRules.stamina()) {
				spendStamina(SurvivalAttachments.get(survivor), SurvivalRules.STAMINA_ATTACK);
			}
			return InteractionResult.PASS;
		});
		EntitySleepEvents.STOP_SLEEPING.register((entity, pos) -> {
			if (entity instanceof ServerPlayer survivor) {
				SurvivalData data = SurvivalAttachments.get(survivor);
				// A full night clears fatigue; being woken early gives back what was slept.
				data.addFatigue(survivor.isSleepingLongEnough() ? -SurvivalData.MAX : -survivor.getSleepTimer() * 0.3F);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> TRACKS.remove(handler.getPlayer().getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> TRACKS.clear());
	}

	/** Survival applies to living survivors in survival or adventure mode. */
	public static boolean applies(ServerPlayer player) {
		return player.isAlive() && !player.isCreative() && !player.isSpectator();
	}

	private static void tick(MinecraftServer server) {
		boolean second = server.getTickCount() % 20 == 0;
		boolean sync = server.getTickCount() % SYNC_INTERVAL == 0;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Track track = TRACKS.computeIfAbsent(player.getUUID(), id -> new Track());
			if (!applies(player)) {
				clearModifiers(player);
				if (sync) {
					send(player, track, SurvivalPayload.INACTIVE);
				}
				continue;
			}
			SurvivalData data = SurvivalAttachments.get(player);
			tickStamina(player, data, track);
			if (second) {
				tickSecond(player, data, server.getTickCount() / 20);
				applyModifiers(player, data);
			}
			if (sync) {
				send(player, track, SurvivalPayload.of(data));
			}
		}
	}

	private static void send(ServerPlayer player, Track track, SurvivalPayload payload) {
		int fingerprint = payload.fingerprint();
		if (fingerprint != track.lastFingerprint && ServerPlayNetworking.canSend(player, SurvivalPayload.TYPE)) {
			track.lastFingerprint = fingerprint;
			ServerPlayNetworking.send(player, payload);
		}
	}

	// ======================================================================================== stamina

	private static void tickStamina(ServerPlayer player, SurvivalData data, Track track) {
		if (!SurvivalRules.stamina()) {
			data.addStamina(SurvivalData.MAX);
			data.setWinded(false);
			return;
		}
		boolean onGround = player.onGround();
		if (track.wasOnGround && !onGround && player.getY() - track.lastY > 0.2 && !player.isInWater() && !player.onClimbable()) {
			spendStamina(data, SurvivalRules.STAMINA_JUMP);
		}
		track.wasOnGround = onGround;
		track.lastY = player.getY();

		if (player.isSprinting() && !player.isPassenger()) {
			float cost = SurvivalRules.STAMINA_SPRINT_PER_SECOND / 20.0F;
			if (data.fracture() > 0 && data.painkiller() <= 0) {
				cost *= 1.5F;
			}
			if (data.load() > data.capacity()) {
				cost *= 1.5F;
			}
			spendStamina(data, cost);
		} else {
			float regen = SurvivalRules.STAMINA_REGEN_PER_SECOND / 20.0F;
			if (data.thirst() <= 0.0F) {
				regen *= 0.25F;
			} else if (data.thirst() < 25.0F) {
				regen *= 0.5F;
			}
			if (player.getFoodData().getFoodLevel() <= 6) {
				regen *= 0.5F;
			}
			if (data.temperature() <= -60.0F) {
				regen *= 0.5F;
			}
			data.addStamina(regen);
		}
		if (data.winded() && data.stamina() >= Math.min(SurvivalRules.WINDED_RECOVERY, data.maxStamina())) {
			data.setWinded(false);
		}
	}

	private static void spendStamina(SurvivalData data, float amount) {
		data.addStamina(-amount);
		if (data.stamina() <= 0.0F) {
			data.setWinded(true);
		}
	}

	// ======================================================================================== once a second

	private static void tickSecond(ServerPlayer player, SurvivalData data, int second) {
		ServerLevel level = player.level();
		RandomSource random = player.getRandom();
		float drain = SurvivalRules.drain() * Skills.drainFactor(player);
		data.elapse(20);
		if (SurvivalRules.weight()) {
			data.setLoad(CarryWeight.carried(player), CarryWeight.capacity(player));
		} else {
			data.setLoad(0.0F, CarryWeight.BASE_CAPACITY);
		}
		if (second % 60 == 0) {
			Skills.award(player, Skill.SURVIVALIST, Skills.XP_MINUTE_ALIVE);
		}

		// Temperature first: the rest reads it.
		if (SurvivalRules.temperature()) {
			float ambient = Temperature.ambient(level, player);
			data.setTemperature(data.temperature() + (ambient - data.temperature()) * 0.04F);
			if (data.temperature() <= -60.0F && second % 4 == 0) {
				SurvivalDamage.hurt(player, SurvivalDamage.HYPOTHERMIA, 1.0F);
			} else if (data.temperature() >= 60.0F && second % 5 == 0) {
				SurvivalDamage.hurt(player, SurvivalDamage.HEATSTROKE, 1.0F);
			}
			if (data.temperature() <= -30.0F) {
				player.causeFoodExhaustion(0.02F * drain); // shivering burns food
			}
		} else {
			data.setTemperature(0.0F);
		}

		if (SurvivalRules.thirst()) {
			float loss = SurvivalRules.THIRST_PER_SECOND * drain;
			if (player.isSprinting()) {
				loss *= 2.2F;
			}
			if (data.temperature() >= 60.0F) {
				loss *= 2.2F;
			} else if (data.temperature() >= 30.0F) {
				loss *= 1.5F;
			}
			if (data.sickness() > 0) {
				loss *= 1.5F;
			}
			data.addThirst(-loss);
			if (data.thirst() <= 0.0F && second % 4 == 0) {
				SurvivalDamage.hurt(player, SurvivalDamage.DEHYDRATION, 1.0F);
			}
		} else {
			data.addThirst(SurvivalData.MAX);
		}

		if (SurvivalRules.sleep()) {
			if (!player.isSleeping()) {
				data.addFatigue(SurvivalRules.FATIGUE_PER_SECOND * drain);
			}
		} else {
			data.addFatigue(-SurvivalData.MAX);
		}

		if (data.bleeding() > 0) {
			boolean heavy = data.bleeding() > 1;
			if (second % (heavy ? 3 : 8) == 0) {
				SurvivalDamage.hurt(player, SurvivalDamage.BLEEDING, 1.0F);
			}
			if (heavy && random.nextFloat() < 0.01F) {
				data.setBleeding(1); // it slows
			} else if (!heavy && random.nextFloat() < 0.02F) {
				data.setBleeding(0); // it clots
			}
		}

		if (data.sickness() > 0) {
			player.causeFoodExhaustion(0.03F);
			if (second % 12 == 0) {
				player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 140, 0, true, false, false));
			}
		}

		if (data.infected()) {
			if (!SurvivalRules.infection()) {
				data.addInfection(-SurvivalData.MAX);
			} else {
				data.addInfection(SurvivalRules.INFECTION_PER_SECOND * SurvivalRules.infectionSpeed());
				if (!SurvivalRules.lethal() && data.infection() > 69.0F) {
					data.addInfection(69.0F - data.infection()); // STORY: it never reaches the last stage
				}
				int stage = data.infectionStage();
				if (stage >= 2) {
					player.causeFoodExhaustion(0.015F);
				}
				if (stage >= 3 && second % 10 == 0) {
					SurvivalDamage.hurt(player, SurvivalDamage.INFECTION, 1.0F);
				}
				if (data.infection() >= SurvivalData.MAX) {
					SurvivalDamage.hurt(player, SurvivalDamage.INFECTION, Float.MAX_VALUE);
				}
			}
		}
	}

	// ======================================================================================== penalties

	/** Movement and melee penalties, as attribute modifiers (so they need no potion effects). */
	private static void applyModifiers(ServerPlayer player, SurvivalData data) {
		double speed = 1.0;
		if (data.fracture() > 0 && data.painkiller() <= 0) {
			speed *= 0.7;
		}
		if (data.temperature() <= -60.0F) {
			speed *= 0.8;
		}
		if (data.fatigue() >= 90.0F) {
			speed *= 0.85;
		}
		if (data.infectionStage() >= 3) {
			speed *= 0.85;
		}
		if (data.winded()) {
			speed *= 0.8;
		}
		speed *= CarryWeight.speedFactor(data.load(), data.capacity());
		boolean weak = data.winded() || data.fatigue() >= 90.0F || data.infectionStage() >= 3;
		set(player.getAttribute(Attributes.MOVEMENT_SPEED), SPEED_ID, speed - 1.0);
		set(player.getAttribute(Attributes.ATTACK_DAMAGE), DAMAGE_ID, weak ? -0.25 : 0.0);
	}

	private static void set(AttributeInstance attribute, Identifier id, double amount) {
		if (attribute == null) {
			return;
		}
		AttributeModifier current = attribute.getModifier(id);
		if (Math.abs(amount) < 1.0E-4) {
			if (current != null) {
				attribute.removeModifier(id);
			}
		} else if (current == null || Math.abs(current.amount() - amount) > 1.0E-4) {
			attribute.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	private static void clearModifiers(ServerPlayer player) {
		set(player.getAttribute(Attributes.MOVEMENT_SPEED), SPEED_ID, 0.0);
		set(player.getAttribute(Attributes.ATTACK_DAMAGE), DAMAGE_ID, 0.0);
	}

	// ======================================================================================== injuries

	private static void afterDamage(LivingEntity entity, DamageSource source, float baseDamage, float damageTaken, boolean blocked) {
		if (!(entity instanceof ServerPlayer player) || !applies(player) || blocked || damageTaken <= 0.0F || SurvivalDamage.isSurvival(source)) {
			return;
		}
		SurvivalData data = SurvivalAttachments.get(player);
		RandomSource random = player.getRandom();
		float chance = SurvivalRules.injuryChance();
		if (source.getEntity() instanceof InfectedEntity) {
			if (random.nextFloat() < 0.22F * chance) {
				data.setBleeding(Math.max(data.bleeding(), damageTaken >= 6.0F ? 2 : 1));
				tell(player, "message.deadlands.survival.bleeding");
			}
			if (SurvivalRules.infection()) {
				if (data.infected()) {
					data.addInfection(1.5F); // every further bite makes it worse
				} else if (random.nextFloat() < 0.10F * chance) {
					data.addInfection(4.0F);
					tell(player, "message.deadlands.survival.infected");
				}
			}
		} else if (source.is(DamageTypeTags.IS_FALL)) {
			if (damageTaken >= 5.0F && random.nextFloat() < 0.55F * chance) {
				data.setFracture(SurvivalRules.FRACTURE_TICKS);
				tell(player, "message.deadlands.survival.fracture");
			}
		} else if (damageTaken >= 6.0F && !source.is(DamageTypeTags.IS_FIRE) && random.nextFloat() < 0.25F * chance) {
			data.setBleeding(Math.max(data.bleeding(), source.is(DamageTypeTags.IS_EXPLOSION) ? 2 : 1));
			tell(player, "message.deadlands.survival.bleeding");
		}
	}

	/** Those who die of the infection get up again. */
	private static void afterDeath(LivingEntity entity, DamageSource source) {
		if (entity instanceof ServerPlayer player && source.is(SurvivalDamage.INFECTION)) {
			InfectedEntity walker = SpawnFinder.spawn(player.level(), InfectedKind.WALKER, player.blockPosition(), EntitySpawnReason.CONVERSION, null);
			if (walker != null) {
				walker.setCustomName(player.getName());
				walker.setPersistenceRequired();
			}
		}
	}

	private static void tell(ServerPlayer player, String key) {
		player.displayClientMessage(Component.translatable(key), true);
	}

	// ======================================================================================== treatment (used by items)

	/** Stop bleeding. Returns false if there was nothing to stop. */
	public static boolean bandage(SurvivalData data) {
		if (data.bleeding() <= 0) {
			return false;
		}
		data.setBleeding(0);
		return true;
	}

	/** Make a survivor sick (bad food, dirty water) unless they already are for longer. */
	public static void sicken(SurvivalData data, int ticks) {
		data.setSickness(Math.max(data.sickness(), ticks));
	}
}
