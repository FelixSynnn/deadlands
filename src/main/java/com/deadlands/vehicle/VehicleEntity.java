package com.deadlands.vehicle;

import com.deadlands.noise.Loudness;
import com.deadlands.noise.NoiseSystem;
import com.deadlands.registry.DLSounds;
import com.deadlands.registry.DLTags;
import com.deadlands.registry.DLVehicles;
import com.deadlands.skill.Skill;
import com.deadlands.skill.Skills;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A vehicle: something a survivor restores, fuels, drives, fills with supplies and eventually
 * wrecks.
 *
 * <p><b>Condition.</b> Engine, tires and body each run 0 to 100; there is a battery or there is
 * not; the tank holds litres of fuel; the paint is one of three finishes. A burned-out wreck can
 * never be restored. Everything is saved with the entity and synced to clients.
 *
 * <p><b>Restoration.</b> A vehicle found in the world is usually missing something. Using it with
 * an empty hand lists what it needs; using it with the part (engine parts, a tire, a battery, a
 * body panel, a fuel can) while carrying a wrench fits it. It can be driven once the engine and
 * tires are at 50 or more, it has a battery, and the body is not destroyed. Fuel is only needed to
 * move.
 *
 * <p><b>Driving.</b> The driver's own movement keys steer it. Like Minecraft's boats, the client
 * of the driver moves the vehicle and the server follows; the server charges fuel for the distance
 * covered, makes the engine noise the infected hear, and runs over what is in the way (which costs
 * bodywork). Worn tires and a rough engine lower the top speed; so does leaving the road.
 *
 * <p><b>Lights and horn.</b> With a battery, the driver can switch the headlights on (they light
 * the road a few blocks ahead by moving an invisible light with the vehicle) and sound the horn,
 * which everything nearby hears.
 *
 * <p><b>Towing.</b> Use a tow rope on the vehicle that will pull, then on the one to be pulled. The
 * towed vehicle follows at the rope's length; the puller is slower while it tows. A towed vehicle
 * can pull another in turn, up to three behind one. Using a towed vehicle again lets it go and
 * gives the rope back. The rope is drawn between them.
 *
 * <p><b>Overgrowth.</b> Some found vehicles are grown over; pulling the growth off (an empty hand)
 * is the first step of restoring one.
 *
 * <p><b>Storage.</b> Sneak and use to open the load space. Destroying the vehicle (the body reaching
 * zero) throws everyone out and leaves a wreck that keeps its cargo.
 */
public class VehicleEntity extends Entity {
	private static final EntityDataAccessor<Float> DATA_FUEL = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_ENGINE = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_TIRES = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_BODY = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Byte> DATA_FLAGS = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Integer> DATA_PAINT = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
	/** The entity id of the vehicle pulling this one, -1 for none: the client draws the rope from it. */
	private static final EntityDataAccessor<Integer> DATA_TOWED_BY = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
	/** How many vehicles may follow one on ropes. */
	public static final int MAX_CHAIN = 3;

	public static final int FLAG_BATTERY = 1;
	public static final int FLAG_BURNED = 2;
	public static final int FLAG_LIGHTS = 4;
	public static final int FLAG_OVERGROWN = 8;
	public static final int FLAG_TOWING = 16;
	public static final int ACTION_HORN = 0;
	public static final int ACTION_LIGHTS = 1;
	public static final int PAINTS = 3;
	/** Who has tied a rope to which vehicle and not yet to a second one. */
	private static final Map<UUID, UUID> ROPE_FROM = new HashMap<>();
	public static final float USABLE = 50.0F;
	private static final float GRAVITY = 0.08F;

	private final VehicleKind kind;
	private final SimpleContainer storage;
	private final InterpolationHandler interpolation = new InterpolationHandler(this, 3);
	/** Signed speed along the heading, blocks per tick. Simulated where the vehicle is controlled. */
	private float speed;
	private double lastX;
	private double lastZ;
	private float wheelSpin;
	private float steer;
	/** Cargo waiting to be rolled the first time the load space is opened (found vehicles only). */
	private boolean cargoPending;
	/** The vehicle pulling this one on a rope. */
	private @Nullable UUID towedBy;
	private int towLost;
	/** Where the headlights' light block is. */
	private @Nullable BlockPos lightPos;
	private int hornCooldown;
	/** Client only: the doors swing when someone gets in or out. */
	private float doorOpen;
	private int doorTicks;
	private int lastPassengers;

	public VehicleEntity(EntityType<? extends VehicleEntity> type, Level level) {
		super(type, level);
		VehicleKind bound = VehicleKind.of(type);
		this.kind = bound == null ? VehicleKind.SEDAN : bound;
		this.storage = new SimpleContainer(Math.max(1, kind.storageRows()) * 9);
		this.blocksBuilding = true;
	}

	public VehicleKind kind() {
		return kind;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_FUEL, 0.0F);
		builder.define(DATA_ENGINE, 100.0F);
		builder.define(DATA_TIRES, 100.0F);
		builder.define(DATA_BODY, 100.0F);
		builder.define(DATA_FLAGS, (byte) FLAG_BATTERY);
		builder.define(DATA_PAINT, 0);
		builder.define(DATA_TOWED_BY, -1);
	}

	// ======================================================================================== condition

	public float fuel() {
		return entityData.get(DATA_FUEL);
	}

	public void setFuel(float litres) {
		entityData.set(DATA_FUEL, Mth.clamp(litres, 0.0F, kind.tank()));
	}

	public float engine() {
		return entityData.get(DATA_ENGINE);
	}

	public void setEngine(float value) {
		entityData.set(DATA_ENGINE, Mth.clamp(value, 0.0F, 100.0F));
	}

	public float tires() {
		return entityData.get(DATA_TIRES);
	}

	public void setTires(float value) {
		entityData.set(DATA_TIRES, Mth.clamp(value, 0.0F, 100.0F));
	}

	public float body() {
		return entityData.get(DATA_BODY);
	}

	public void setBody(float value) {
		entityData.set(DATA_BODY, Mth.clamp(value, 0.0F, 100.0F));
	}

	public boolean hasBattery() {
		return (entityData.get(DATA_FLAGS) & FLAG_BATTERY) != 0;
	}

	public boolean burned() {
		return (entityData.get(DATA_FLAGS) & FLAG_BURNED) != 0;
	}

	public void setFlag(int flag, boolean on) {
		byte flags = entityData.get(DATA_FLAGS);
		entityData.set(DATA_FLAGS, (byte) (on ? flags | flag : flags & ~flag));
	}

	public boolean lights() {
		return (entityData.get(DATA_FLAGS) & FLAG_LIGHTS) != 0;
	}

	public boolean overgrown() {
		return (entityData.get(DATA_FLAGS) & FLAG_OVERGROWN) != 0;
	}

	/** Pulling another vehicle on a rope. */
	public boolean towing() {
		return (entityData.get(DATA_FLAGS) & FLAG_TOWING) != 0;
	}

	public boolean towed() {
		return towedBy != null;
	}

	/** The entity id of the vehicle pulling this one, or -1 (synced, for the rope). */
	public int towedById() {
		return entityData.get(DATA_TOWED_BY);
	}

	/** How many vehicles this one is behind (0 when nothing pulls it). */
	private int chainDepth(ServerLevel level) {
		int depth = 0;
		VehicleEntity current = this;
		while (current != null && current.towedBy != null && depth <= MAX_CHAIN) {
			depth++;
			current = level.getEntity(current.towedBy) instanceof VehicleEntity lead ? lead : null;
		}
		return depth;
	}

	/** 0 to 1: how far the doors stand open (client only). */
	public float doorOpen() {
		return doorOpen;
	}

	/** 0 none, 1 dented, 2 battered: what the bodywork shows. */
	public int damageLevel() {
		return burned() ? 0 : body() < 35.0F ? 2 : body() < 70.0F ? 1 : 0;
	}

	/** 0 faded, 1 rusted, 2 clean. Burned wrecks ignore it. */
	public int paint() {
		return Math.floorMod(entityData.get(DATA_PAINT), PAINTS);
	}

	public void setPaint(int paint) {
		entityData.set(DATA_PAINT, Math.floorMod(paint, PAINTS));
	}

	/** Restored enough to drive (fuel aside). */
	public boolean drivable() {
		return !burned() && engine() >= USABLE && tires() >= USABLE && hasBattery() && body() > 0.0F;
	}

	/** What it still needs before it can be driven, as translation keys with a count. */
	public List<Component> needs() {
		List<Component> needs = new ArrayList<>();
		if (burned()) {
			needs.add(Component.translatable("vehicle.deadlands.need.burned"));
			return needs;
		}
		if (overgrown()) {
			needs.add(Component.translatable("vehicle.deadlands.need.overgrown"));
		}
		if (engine() < USABLE) {
			needs.add(Component.translatable("vehicle.deadlands.need.engine", Mth.ceil((USABLE - engine()) / VehicleParts.ENGINE_PER_PART)));
		}
		if (tires() < USABLE) {
			needs.add(Component.translatable("vehicle.deadlands.need.tires", Mth.ceil((USABLE - tires()) / VehicleParts.TIRES_PER_TIRE)));
		}
		if (!hasBattery()) {
			needs.add(Component.translatable("vehicle.deadlands.need.battery"));
		}
		if (body() <= 0.0F) {
			needs.add(Component.translatable("vehicle.deadlands.need.body"));
		}
		if (fuel() <= 0.0F) {
			needs.add(Component.translatable("vehicle.deadlands.need.fuel"));
		}
		return needs;
	}

	/** A random state for a vehicle found in the world: usually broken in two or three ways, sometimes burned out. */
	public void abandon(net.minecraft.util.RandomSource random) {
		if (random.nextFloat() < 0.18F) {
			setFlag(FLAG_BURNED, true);
			setFlag(FLAG_BATTERY, false);
			setEngine(0.0F);
			setTires(0.0F);
			setBody(0.0F);
			setFuel(0.0F);
			setFlag(FLAG_OVERGROWN, random.nextFloat() < 0.4F);
			return;
		}
		setFlag(FLAG_OVERGROWN, random.nextFloat() < 0.25F);
		cargoPending = kind.storageRows() > 0 && random.nextFloat() < 0.6F;
		setPaint(random.nextInt(2)); // faded or rusted, never clean
		setEngine(random.nextFloat() < 0.4F ? 55.0F + random.nextInt(30) : random.nextInt(45));
		setTires(random.nextFloat() < 0.35F ? 50.0F + random.nextInt(40) : 25.0F * random.nextInt(2));
		setBody(30.0F + random.nextInt(60));
		setFlag(FLAG_BATTERY, random.nextFloat() < 0.3F);
		setFuel(random.nextFloat() < 0.3F ? kind.tank() * random.nextFloat() * 0.25F : 0.0F);
	}

	// ======================================================================================== ticking

	@Override
	public void tick() {
		super.tick();
		interpolation.interpolate();
		if (isLocalInstanceAuthoritative()) {
			drive();
			move(MoverType.SELF, getDeltaMovement());
			if (horizontalCollision) {
				speed *= 0.2F;
			}
		} else {
			setDeltaMovement(Vec3.ZERO);
		}
		applyEffectsFromBlocks();
		if (level() instanceof ServerLevel server) {
			serverTick(server);
		} else {
			double dx = getX() - lastX;
			double dz = getZ() - lastZ;
			float moved = (float) Math.sqrt(dx * dx + dz * dz);
			Vec3 heading = Vec3.directionFromRotation(0.0F, getYRot());
			wheelSpin += moved * (dx * heading.x + dz * heading.z >= 0.0 ? 1.0F : -1.0F);
			LivingEntity driver = getControllingPassenger();
			steer += ((driver == null ? 0.0F : -driver.xxa) - steer) * 0.3F;
			int riders = getPassengers().size();
			if (riders != lastPassengers) {
				lastPassengers = riders;
				doorTicks = 16;
			}
			if (doorTicks > 0) {
				doorTicks--;
			}
			doorOpen += ((doorTicks > 4 ? 1.0F : 0.0F) - doorOpen) * 0.35F;
		}
		lastX = getX();
		lastZ = getZ();
	}

	/** Steering, throttle, friction and gravity, run by whoever controls the vehicle. */
	private void drive() {
		LivingEntity driver = getControllingPassenger();
		float throttle = 0.0F;
		float turn = 0.0F;
		if (driver instanceof Player && drivable() && fuel() > 0.0F) {
			throttle = Mth.clamp(driver.zza, -1.0F, 1.0F);
			turn = Mth.clamp(driver.xxa, -1.0F, 1.0F);
		}
		float top = topSpeed();
		if (onGround()) {
			if (throttle != 0.0F) {
				speed += throttle * kind.acceleration() * (throttle * speed < 0.0F ? 3.0F : 1.0F); // braking is stronger than accelerating
			} else {
				speed *= 0.95F;
				if (Math.abs(speed) < 0.003F) {
					speed = 0.0F;
				}
			}
		}
		speed = Mth.clamp(speed, -top * 0.4F, top);
		if (turn != 0.0F && Math.abs(speed) > 0.01F) {
			// Slow, it turns in its own length; fast, it turns wide. In reverse the wheel works backwards.
			float grip = Mth.clamp(Math.abs(speed) / (kind.maxSpeed() * 0.35F), 0.2F, 1.0F);
			setYRot(getYRot() - turn * kind.turnRate() * grip * Math.signum(speed));
		}
		Vec3 heading = Vec3.directionFromRotation(0.0F, getYRot());
		double fall = onGround() ? -GRAVITY : getDeltaMovement().y - GRAVITY;
		if (isInWater()) {
			speed *= 0.8F; // it is not a boat
			fall = Math.max(fall, -0.04);
		}
		setDeltaMovement(heading.x * speed, fall, heading.z * speed);
	}

	/** Top speed now: the kind's, less for worn tires and a rough engine, less again off the road. */
	public float topSpeed() {
		float condition = 0.5F + 0.25F * (tires() / 100.0F) + 0.25F * (engine() / 100.0F);
		BlockPos below = getBlockPosBelowThatAffectsMyMovement();
		boolean road = level().getBlockState(below).is(DLTags.ROAD_SURFACE);
		return kind.maxSpeed() * condition * (road ? 1.0F : kind.offRoad()) * (towing() ? 0.65F : 1.0F);
	}

	private void serverTick(ServerLevel level) {
		if (hornCooldown > 0) {
			hornCooldown--;
		}
		tickLights(level);
		tickTow(level);
		drivenTick(level);
	}

	/** The driver's switches: {@link #ACTION_HORN} or {@link #ACTION_LIGHTS}. Both need a battery. */
	public void control(ServerPlayer player, int action) {
		if (getControllingPassenger() != player || burned()) {
			return;
		}
		if (!hasBattery()) {
			player.displayClientMessage(Component.translatable("vehicle.deadlands.no_battery"), true);
			return;
		}
		if (action == ACTION_LIGHTS) {
			setFlag(FLAG_LIGHTS, !lights());
			level().playSound(null, getX(), getY(), getZ(), SoundEvents.LEVER_CLICK, SoundSource.NEUTRAL, 0.5F, lights() ? 1.2F : 0.9F);
		} else if (action == ACTION_HORN && hornCooldown <= 0 && level() instanceof ServerLevel server) {
			hornCooldown = 15;
			server.playSound(null, getX(), getY(), getZ(), DLSounds.VEHICLE_HORN.value(), SoundSource.NEUTRAL, 3.0F, 1.0F);
			NoiseSystem.emit(server, position(), Loudness.LOUD, player, true);
		}
	}

	/** Headlights: an invisible light block kept a few blocks ahead of the bonnet while they are on. */
	private void tickLights(ServerLevel level) {
		if (!lights() || !hasBattery() || burned()) {
			if (lights()) {
				setFlag(FLAG_LIGHTS, false);
			}
			clearLight(level);
			return;
		}
		if (tickCount % 2 != 0) {
			return;
		}
		Vec3 heading = Vec3.directionFromRotation(0.0F, getYRot());
		BlockPos target = BlockPos.containing(getX() + heading.x * 4.5, getY() + 1.2, getZ() + heading.z * 4.5);
		if (target.equals(lightPos)) {
			return;
		}
		clearLight(level);
		if (level.getBlockState(target).isAir()) {
			level.setBlock(target, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 14), Block.UPDATE_CLIENTS);
			lightPos = target;
		}
	}

	private void clearLight(ServerLevel level) {
		if (lightPos != null) {
			if (level.isLoaded(lightPos) && level.getBlockState(lightPos).is(Blocks.LIGHT)) {
				level.setBlock(lightPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
			lightPos = null;
		}
	}

	@Override
	public void remove(RemovalReason reason) {
		if (level() instanceof ServerLevel server) {
			clearLight(server);
		}
		super.remove(reason);
	}

	/** On a rope: keep within its length of the vehicle pulling, facing it. */
	private void tickTow(ServerLevel level) {
		if (towedBy == null) {
			return;
		}
		Entity lead = level.getEntity(towedBy);
		if (lead == null) {
			// It may not be loaded yet; give it ten seconds.
			if (++towLost > 200) {
				unhitch(level, null);
			}
			return;
		}
		towLost = 0;
		if (towedById() != lead.getId()) {
			entityData.set(DATA_TOWED_BY, lead.getId());
		}
		if (!(lead instanceof VehicleEntity puller) || !puller.isAlive() || distanceToSqr(puller) > 24.0 * 24.0 || !getPassengers().isEmpty()) {
			unhitch(level, null);
			return;
		}
		double dx = puller.getX() - getX();
		double dz = puller.getZ() - getZ();
		double distance = Math.sqrt(dx * dx + dz * dz);
		double slack = 3.5 + (kind.width() + puller.kind.width()) * 0.75;
		if (distance > slack) {
			double step = Math.min(distance - slack, 1.5);
			setYRot(Mth.approachDegrees(getYRot(), (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F, 6.0F));
			move(MoverType.SELF, new Vec3(dx / distance * step, 0.0, dz / distance * step));
		}
	}

	private void unhitch(ServerLevel level, @Nullable Player to) {
		Entity lead = towedBy == null ? null : level.getEntity(towedBy);
		if (lead instanceof VehicleEntity puller) {
			puller.setFlag(FLAG_TOWING, false);
		}
		towedBy = null;
		towLost = 0;
		entityData.set(DATA_TOWED_BY, -1);
		ItemStack rope = new ItemStack(DLVehicles.TOW_ROPE);
		if (to == null || !to.getInventory().add(rope)) {
			spawnAtLocation(level, rope);
		}
	}

	/** A tow rope used on this vehicle: tie it here first, or hitch this one to the one already tied. */
	private void rope(ServerLevel level, Player player) {
		if (towedBy != null) {
			unhitch(level, player);
			player.displayClientMessage(Component.translatable("vehicle.deadlands.tow.released"), true);
			return;
		}
		UUID from = ROPE_FROM.remove(player.getUUID());
		Entity first = from == null ? null : level.getEntity(from);
		if (!(first instanceof VehicleEntity puller) || puller == this) {
			if (first == this) {
				player.displayClientMessage(Component.translatable("vehicle.deadlands.tow.cancelled"), true);
			} else if (towing() || burned()) {
				player.displayClientMessage(Component.translatable("vehicle.deadlands.tow.busy"), true);
			} else {
				ROPE_FROM.put(player.getUUID(), getUUID());
				player.displayClientMessage(Component.translatable("vehicle.deadlands.tow.tied", Component.translatable(kind.translationKey())), true);
			}
			return;
		}
		if (distanceToSqr(puller) > 144.0) {
			player.displayClientMessage(Component.translatable("vehicle.deadlands.tow.too_far"), true);
			return;
		}
		if (!getPassengers().isEmpty() || puller.towing() || puller.chainDepth(level) >= MAX_CHAIN || towing()) {
			player.displayClientMessage(Component.translatable("vehicle.deadlands.tow.busy"), true);
			return;
		}
		towedBy = puller.getUUID();
		entityData.set(DATA_TOWED_BY, puller.getId());
		puller.setFlag(FLAG_TOWING, true);
		player.getMainHandItem().consume(1, player);
		player.displayClientMessage(Component.translatable("vehicle.deadlands.tow.hitched", Component.translatable(kind.translationKey()),
			Component.translatable(puller.kind.translationKey())), true);
	}

	/** Fuel, noise and collisions while someone drives. */
	private void drivenTick(ServerLevel level) {
		double dx = getX() - lastX;
		double dz = getZ() - lastZ;
		double moved = Math.sqrt(dx * dx + dz * dz);
		LivingEntity driver = getControllingPassenger();
		if (driver == null || moved < 0.01 || moved > 3.0) {
			return;
		}
		setFuel(fuel() - (float) (moved * kind.fuelUse() / 100.0));
		if (tickCount % 30 == 0) {
			// An engine is loud. Slower is quieter.
			NoiseSystem.emit(level, position(), moved > kind.maxSpeed() * 0.5 ? Loudness.LOUD : Loudness.MODERATE, driver, true);
		}
		if (moved > 0.18) {
			runOver(level, driver, moved);
		}
	}

	/** Hit whatever living is in the way. It hurts them a lot and the bodywork a little. */
	private void runOver(ServerLevel level, LivingEntity driver, double moved) {
		Vec3 heading = new Vec3(getX() - lastX, 0.0, getZ() - lastZ).normalize();
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.35, 0.0, 0.35).move(heading.scale(0.6)),
			entity -> entity.isAlive() && !hasPassenger(entity) && !entity.isSpectator())) {
			DamageSource source = driver instanceof Player player ? level.damageSources().playerAttack(player) : level.damageSources().generic();
			if (victim.hurtServer(level, source, (float) Math.min(24.0, moved * 40.0))) {
				victim.push(heading.x * 0.9, 0.35, heading.z * 0.9);
				setBody(body() - 1.5F);
				if (random.nextFloat() < 0.15F) {
					setEngine(engine() - 1.0F);
				}
				level.playSound(null, getX(), getY(), getZ(), DLSounds.VEHICLE_IMPACT.value(), SoundSource.NEUTRAL, 1.0F, 0.9F + random.nextFloat() * 0.2F);
				if (body() <= 0.0F) {
					ejectPassengers();
					return;
				}
			}
		}
	}

	// ======================================================================================== damage

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (isRemoved() || isInvulnerableToBase(source)) {
			return false;
		}
		if (source.getEntity() instanceof Player player && player.getAbilities().instabuild) {
			for (int i = 0; i < storage.getContainerSize(); i++) {
				spawnAtLocation(level, storage.removeItemNoUpdate(i));
			}
			discard();
			return true;
		}
		markHurt();
		setBody(body() - amount * 100.0F / kind.bodyStrength());
		level.playSound(null, getX(), getY(), getZ(), DLSounds.VEHICLE_IMPACT.value(), SoundSource.NEUTRAL, 0.8F, 1.1F);
		if (source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE) && body() <= 0.0F) {
			setFlag(FLAG_BURNED, true);
		}
		if (body() <= 0.0F) {
			ejectPassengers();
		}
		return true;
	}

	@Override
	public boolean isPickable() {
		return !isRemoved();
	}

	@Override
	public boolean canBeCollidedWith(@Nullable Entity other) {
		return true;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public float maxUpStep() {
		return 1.0F;
	}

	@Override
	public InterpolationHandler getInterpolation() {
		return interpolation;
	}

	// ======================================================================================== use

	@Override
	public InteractionResult interact(Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (hand == InteractionHand.MAIN_HAND && (held.is(DLVehicles.TOW_ROPE) || (towedBy != null && !player.isSecondaryUseActive()))) {
			if (level() instanceof ServerLevel server) {
				rope(server, player);
			}
			return InteractionResult.SUCCESS;
		}
		if (player.isSecondaryUseActive()) {
			if (kind.storageRows() > 0 && player instanceof ServerPlayer survivor) {
				openStorage(survivor);
			}
			return InteractionResult.SUCCESS;
		}
		if (VehicleParts.isPart(held)) {
			if (!level().isClientSide()) {
				VehicleParts.fit(this, player, held);
			}
			return InteractionResult.SUCCESS;
		}
		if (level().isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (overgrown()) {
			setFlag(FLAG_OVERGROWN, false);
			level().playSound(null, getX(), getY(), getZ(), SoundEvents.GRASS_BREAK, SoundSource.NEUTRAL, 0.9F, 0.9F);
			player.displayClientMessage(Component.translatable("vehicle.deadlands.cleared", Component.translatable(kind.translationKey())), true);
			return InteractionResult.SUCCESS;
		}
		List<Component> needs = needs();
		if (!drivable()) {
			if (player instanceof ServerPlayer survivor) {
				com.deadlands.progression.Tasks.track(survivor, this);
			}
			MutableComponent list = Component.empty();
			for (int i = 0; i < needs.size(); i++) {
				list.append(i == 0 ? Component.empty() : Component.literal(", ")).append(needs.get(i));
			}
			player.displayClientMessage(Component.translatable("vehicle.deadlands.needs", Component.translatable(kind.translationKey()), list), false);
			return InteractionResult.SUCCESS;
		}
		if (fuel() <= 0.0F) {
			player.displayClientMessage(Component.translatable("vehicle.deadlands.no_fuel"), true);
		}
		if (getPassengers().size() < kind.seats()) {
			player.startRiding(this);
			level().playSound(null, getX(), getY(), getZ(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.NEUTRAL, 0.4F, 1.4F);
		}
		return InteractionResult.SUCCESS;
	}

	private void openStorage(ServerPlayer player) {
		if (cargoPending) {
			cargoPending = false;
			net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.LootTable> key = net.minecraft.resources.ResourceKey.create(
				net.minecraft.core.registries.Registries.LOOT_TABLE, com.deadlands.Deadlands.id("vehicles/cargo"));
			net.minecraft.world.level.storage.loot.LootTable table = player.level().getServer().reloadableRegistries().getLootTable(key);
			net.minecraft.world.level.storage.loot.LootParams params = new net.minecraft.world.level.storage.loot.LootParams.Builder(player.level())
				.withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, position())
				.create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
			table.fill(storage, params, random.nextLong());
		}
		int rows = Mth.clamp(kind.storageRows(), 1, 6);
		MenuType<ChestMenu> type = switch (rows) {
			case 1 -> MenuType.GENERIC_9x1;
			case 2 -> MenuType.GENERIC_9x2;
			case 3 -> MenuType.GENERIC_9x3;
			case 4 -> MenuType.GENERIC_9x4;
			case 5 -> MenuType.GENERIC_9x5;
			default -> MenuType.GENERIC_9x6;
		};
		player.openMenu(new SimpleMenuProvider((id, inventory, opener) -> new ChestMenu(type, id, inventory, storage, rows) {
			@Override
			public boolean stillValid(Player user) {
				return VehicleEntity.this.isAlive() && user.distanceToSqr(VehicleEntity.this) < 36.0;
			}
		}, Component.translatable("vehicle.deadlands.storage", Component.translatable(kind.translationKey()))));
		level().playSound(null, getX(), getY(), getZ(), SoundEvents.IRON_DOOR_OPEN, SoundSource.NEUTRAL, 0.5F, 1.3F);
	}

	public SimpleContainer storage() {
		return storage;
	}

	/** Called when a part has been fitted: a sound, experience, and the news if it now runs. */
	public void fitted(Player player) {
		level().playSound(null, getX(), getY(), getZ(), DLSounds.VEHICLE_REPAIR.value(), SoundSource.NEUTRAL, 0.9F, 0.9F + random.nextFloat() * 0.2F);
		setFlag(FLAG_OVERGROWN, false);
		if (player instanceof ServerPlayer survivor) {
			Skills.award(survivor, Skill.ENGINEER, 25);
			if (drivable()) {
				survivor.displayClientMessage(Component.translatable(fuel() > 0.0F ? "vehicle.deadlands.runs" : "vehicle.deadlands.runs_no_fuel",
					Component.translatable(kind.translationKey())), true);
			}
		}
	}

	// ======================================================================================== riding

	@Override
	protected boolean canAddPassenger(Entity entity) {
		return getPassengers().size() < kind.seats() && body() > 0.0F;
	}

	@Override
	public @Nullable LivingEntity getControllingPassenger() {
		return getFirstPassenger() instanceof LivingEntity living ? living : null;
	}

	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
		int index = Math.max(0, getPassengers().indexOf(passenger));
		float[] seat = kind.seat(index);
		return new Vec3(seat[0], seat[1], seat[2]).yRot(-getYRot() * Mth.DEG_TO_RAD);
	}

	@Override
	protected void positionRider(Entity passenger, Entity.MoveFunction move) {
		super.positionRider(passenger, move);
		if (passenger != getControllingPassenger() || !(passenger instanceof Player)) {
			return;
		}
		// The driver faces where the vehicle faces, with room to look around.
		passenger.setYBodyRot(getYRot());
	}

	// ======================================================================================== client view

	/** Wheel rotation in blocks travelled (client only). */
	public float wheelSpin() {
		return wheelSpin;
	}

	/** Front wheel angle, -1 to 1 (client only). */
	public float steer() {
		return steer;
	}

	// ======================================================================================== saving

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putFloat("Fuel", fuel());
		output.putFloat("Engine", engine());
		output.putFloat("Tires", tires());
		output.putFloat("Body", body());
		output.putBoolean("Battery", hasBattery());
		output.putBoolean("Burned", burned());
		output.putInt("Paint", paint());
		output.putBoolean("CargoPending", cargoPending);
		output.putBoolean("Lights", lights());
		output.putBoolean("Overgrown", overgrown());
		output.putBoolean("Towing", towing());
		output.storeNullable("TowedBy", UUIDUtil.CODEC, towedBy);
		output.storeNullable("LightPos", BlockPos.CODEC, lightPos);
		ContainerHelper.saveAllItems(output, storage.getItems());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		setFuel(input.getFloatOr("Fuel", 0.0F));
		setEngine(input.getFloatOr("Engine", 100.0F));
		setTires(input.getFloatOr("Tires", 100.0F));
		setBody(input.getFloatOr("Body", 100.0F));
		setFlag(FLAG_BATTERY, input.getBooleanOr("Battery", true));
		setFlag(FLAG_BURNED, input.getBooleanOr("Burned", false));
		setPaint(input.getIntOr("Paint", 0));
		cargoPending = input.getBooleanOr("CargoPending", false);
		setFlag(FLAG_LIGHTS, input.getBooleanOr("Lights", false));
		setFlag(FLAG_OVERGROWN, input.getBooleanOr("Overgrown", false));
		setFlag(FLAG_TOWING, input.getBooleanOr("Towing", false));
		towedBy = input.read("TowedBy", UUIDUtil.CODEC).orElse(null);
		lightPos = input.read("LightPos", BlockPos.CODEC).orElse(null);
		ContainerHelper.loadAllItems(input, storage.getItems());
	}

	/** Forget half-tied ropes of survivors who leave. */
	public static void init() {
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ROPE_FROM.remove(handler.getPlayer().getUUID()));
	}

	/** The registry gives every kind its own entity type; this finds it. */
	public static EntityType<VehicleEntity> type(VehicleKind kind) {
		return DLVehicles.type(kind);
	}
}
