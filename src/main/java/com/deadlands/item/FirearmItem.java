package com.deadlands.item;

import com.deadlands.noise.Loudness;
import com.deadlands.noise.NoiseSystem;
import com.deadlands.registry.DLDataComponents;
import com.deadlands.registry.DLSounds;
import com.deadlands.skill.Skills;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A gun. Using it fires one loaded round along the line of sight: each
 * pellet stops at the first block or living thing within range. Every shot wears the gun, has a
 * delay before the next, and is the loudest thing a survivor can do: the noise system carries it
 * as a gunshot to every infected and nest in range. A gun in poor condition (see
 * {@link ItemCondition}) sometimes jams: a click, a pause, and the round stays in the pack.
 *
 * <p>Rounds are loaded into the gun ({@code deadlands:loaded} on the item). Using an empty gun, or
 * using it while sneaking, reloads: it takes rounds from the inventory up to the magazine size and
 * cannot fire until the reload time has passed. Creative players do not use up rounds.
 */
public class FirearmItem extends Item {
	/**
	 * @param damage per pellet
	 * @param pellets projectiles per shot (a shotgun fires several)
	 * @param spread how far pellets stray, as a fraction of distance (0 is dead straight)
	 * @param range blocks
	 * @param cooldownTicks delay before the next shot
	 * @param magazine rounds it holds
	 * @param reloadTicks how long reloading takes
	 * @param ammo the round it takes
	 * @param sound the shot
	 */
	public record Spec(float damage, int pellets, float spread, double range, int cooldownTicks, int magazine, int reloadTicks,
		Supplier<Item> ammo, Holder<SoundEvent> sound) {
	}

	private static final float JAM_CHANCE = 0.15F;

	private final Spec spec;

	public FirearmItem(Spec spec, Properties properties) {
		super(properties);
		this.spec = spec;
	}

	public Spec spec() {
		return spec;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack gun = player.getItemInHand(hand);
		if (player.getCooldowns().isOnCooldown(gun)) {
			return InteractionResult.PASS;
		}
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer shooter)) {
			// The server decides whether there was a round to fire; the shooter feels the kick at once.
			if (level.isClientSide()) {
				int loaded = loaded(gun);
				boolean reloading = loaded <= 0 || (player.isSecondaryUseActive() && loaded < spec.magazine());
				if (reloading && (player.isCreative() || player.getInventory().countItem(spec.ammo().get()) > 0)) {
					com.deadlands.bridge.ClientBridge.reload(spec.reloadTicks()); // the same rule the server uses below
				} else if (loaded > 0 && !player.isSecondaryUseActive()) {
					com.deadlands.bridge.ClientBridge.recoil(Math.min(7.0F, 1.5F + spec.damage() * spec.pellets() * 0.18F));
				}
			}
			return InteractionResult.CONSUME;
		}
		int loaded = loaded(gun);
		boolean free = player.isCreative();
		if (!free && (loaded <= 0 || (player.isSecondaryUseActive() && loaded < spec.magazine()))) {
			int added = takeRounds(player.getInventory(), spec.magazine() - loaded);
			if (added <= 0) {
				level.playSound(null, player.getX(), player.getY(), player.getZ(), DLSounds.WEAPON_DRY_FIRE.value(), SoundSource.PLAYERS, 0.8F, 1.0F);
				player.getCooldowns().addCooldown(gun, 6);
				return InteractionResult.FAIL;
			}
			gun.set(DLDataComponents.LOADED, loaded + added);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), DLSounds.WEAPON_RELOAD.value(), SoundSource.PLAYERS, 0.9F, 1.0F);
			player.getCooldowns().addCooldown(gun, spec.reloadTicks());
			return InteractionResult.SUCCESS_SERVER;
		}
		if (ItemCondition.of(gun) == ItemCondition.FAILING && player.getRandom().nextFloat() < JAM_CHANCE) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), DLSounds.WEAPON_DRY_FIRE.value(), SoundSource.PLAYERS, 0.8F, 0.8F);
			player.displayClientMessage(Component.translatable("message.deadlands.weapon.jammed"), true);
			player.getCooldowns().addCooldown(gun, 30);
			return InteractionResult.FAIL;
		}
		if (!free) {
			gun.set(DLDataComponents.LOADED, loaded - 1);
		}
		fire(server, shooter);
		gun.hurtAndBreak(1, player, hand);
		player.getCooldowns().addCooldown(gun, Math.max(3, Math.round(spec.cooldownTicks() * Skills.firearmCooldownFactor(player))));
		return InteractionResult.SUCCESS_SERVER;
	}

	/** Rounds in the gun. */
	public static int loaded(ItemStack gun) {
		return Math.max(0, gun.getOrDefault(DLDataComponents.LOADED, 0));
	}

	/** Take up to {@code wanted} rounds from the inventory. Returns how many were found. */
	private int takeRounds(Inventory inventory, int wanted) {
		Item round = spec.ammo().get();
		int taken = 0;
		for (int slot = 0; slot < inventory.getContainerSize() && taken < wanted; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(round)) {
				int take = Math.min(wanted - taken, stack.getCount());
				stack.shrink(take);
				taken += take;
			}
		}
		return taken;
	}

	private void fire(ServerLevel level, ServerPlayer shooter) {
		RandomSource random = shooter.getRandom();
		Vec3 from = shooter.getEyePosition();
		Vec3 aim = shooter.getLookAngle();
		float damage = spec.damage() * Skills.firearmDamageFactor(shooter);
		for (int pellet = 0; pellet < spec.pellets(); pellet++) {
			Vec3 direction = spec.spread() <= 0.0F ? aim : aim.add((random.nextDouble() - 0.5) * 2.0 * spec.spread(),
				(random.nextDouble() - 0.5) * 2.0 * spec.spread(), (random.nextDouble() - 0.5) * 2.0 * spec.spread()).normalize();
			Vec3 to = from.add(direction.scale(spec.range()));
			HitResult wall = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
			if (wall.getType() != HitResult.Type.MISS) {
				to = wall.getLocation();
			}
			EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, shooter, from, to, new AABB(from, to).inflate(1.0),
				entity -> entity instanceof LivingEntity living && living.isAlive() && !entity.isSpectator() && entity.isPickable(), 0.25F);
			if (hit != null && hit.getEntity() instanceof LivingEntity target) {
				target.invulnerableTime = 0; // every pellet of one shot counts
				target.hurtServer(level, level.damageSources().playerAttack(shooter), damage);
				to = hit.getLocation();
			}
			level.sendParticles(ParticleTypes.CRIT, to.x, to.y, to.z, 3, 0.05, 0.05, 0.05, 0.02);
		}
		Vec3 muzzle = from.add(aim.scale(0.8)).add(0.0, -0.15, 0.0);
		level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 3, 0.02, 0.02, 0.02, 0.01);
		level.sendParticles(com.deadlands.registry.DLParticles.EMBER, muzzle.x, muzzle.y, muzzle.z, 4, 0.03, 0.03, 0.03, 0.04); // muzzle flash
		level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), spec.sound().value(), SoundSource.PLAYERS, 4.0F,
			0.95F + random.nextFloat() * 0.1F);
		NoiseSystem.emit(level, from, Loudness.GUNSHOT, shooter, true);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag) {
		String id = BuiltInRegistries.ITEM.getKey(this).getPath();
		lines.accept(Component.translatable("item.deadlands." + id + ".desc").withStyle(ChatFormatting.GRAY));
		lines.accept(Component.translatable("item.deadlands.loaded", loaded(stack), spec.magazine()).withStyle(ChatFormatting.DARK_GRAY));
		lines.accept(ItemCondition.of(stack).line());
	}
}
