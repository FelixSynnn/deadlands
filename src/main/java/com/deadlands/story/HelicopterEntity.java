package com.deadlands.story;

import com.deadlands.registry.DLStory;
import com.deadlands.registry.DLVehicles;
import com.deadlands.skill.Skill;
import com.deadlands.skill.Skills;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The helicopter left behind at Site Calder: rotor chewed, avionics dead, tanks drained. Using it
 * lists what it still needs; using one of its parts on it (rotor blade, turbine parts, avionics,
 * aviation fuel) while carrying a wrench fits it. The parts fitted are the world's ({@link StoryState}),
 * so every survivor works on the same machine. Once it is whole, using it asks what to do
 * ({@link Endings}); the choice starts the extraction: the rotor spins up, everyone near climbs in,
 * Harley and those who joined her are brought aboard, and it lifts away.
 *
 * <p>It cannot be damaged (only removed by a creative-mode survivor).
 */
public class HelicopterEntity extends Entity {
	private static final EntityDataAccessor<Byte> DATA_PARTS = SynchedEntityData.defineId(HelicopterEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Integer> DATA_LIFT = SynchedEntityData.defineId(HelicopterEntity.class, EntityDataSerializers.INT);
	public static final int SPIN_UP = 60;
	public static final int LIFT_TICKS = 260;
	public static final int SEATS = 8;

	private String ending = "";
	private boolean playersBoard = true;
	private double startX;
	private double startY;
	private double startZ;
	/** Client: rotor angle in radians. */
	private float rotor;
	private float rotorSpeed;

	public HelicopterEntity(EntityType<? extends HelicopterEntity> type, Level level) {
		super(type, level);
		this.blocksBuilding = true;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_PARTS, (byte) 0);
		builder.define(DATA_LIFT, -1);
	}

	/** Which parts are fitted, as bits in {@link DLStory#helicopterParts()} order (synced for the model). */
	public int parts() {
		return entityData.get(DATA_PARTS);
	}

	public boolean hasPart(int index) {
		return (parts() & (1 << index)) != 0;
	}

	/** Ticks into the extraction, -1 before it. */
	public int lift() {
		return entityData.get(DATA_LIFT);
	}

	public float rotor(float partialTick) {
		return rotor + rotorSpeed * partialTick;
	}

	@Override
	public void tick() {
		super.tick();
		if (level() instanceof ServerLevel server) {
			if (tickCount % 20 == 0) {
				syncParts(server);
			}
			int lift = lift();
			if (lift >= 0) {
				liftTick(server, lift);
			}
		} else {
			float target = lift() >= 0 ? 1.4F : 0.0F;
			rotorSpeed += (target - rotorSpeed) * 0.03F;
			rotor += rotorSpeed;
		}
	}

	private void syncParts(ServerLevel level) {
		StoryState story = StoryState.get(level.getServer());
		int bits = 0;
		List<Item> parts = DLStory.helicopterParts();
		for (int i = 0; i < parts.size(); i++) {
			if (story.heliParts().contains(DLStory.idOf(parts.get(i)))) {
				bits |= 1 << i;
			}
		}
		if (bits != parts()) {
			entityData.set(DATA_PARTS, (byte) bits);
		}
	}

	// ======================================================================================== use

	@Override
	public InteractionResult interact(Player player, InteractionHand hand) {
		if (!(level() instanceof ServerLevel level) || !(player instanceof ServerPlayer survivor) || hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.SUCCESS;
		}
		StoryState story = StoryState.get(level.getServer());
		if (!story.ending().isEmpty() || lift() >= 0) {
			survivor.displayClientMessage(Component.translatable("message.deadlands.heli.gone"), true);
			return InteractionResult.SUCCESS;
		}
		ItemStack held = player.getItemInHand(hand);
		if (DLStory.helicopterParts().contains(held.getItem())) {
			if (!player.getInventory().contains(new ItemStack(DLVehicles.WRENCH))) {
				survivor.displayClientMessage(Component.translatable("vehicle.deadlands.need_wrench"), true);
			} else if (story.fitHeliPart(DLStory.idOf(held.getItem()))) {
				held.consume(1, player);
				level.playSound(null, getX(), getY(), getZ(), com.deadlands.registry.DLSounds.VEHICLE_REPAIR.value(), SoundSource.NEUTRAL, 1.0F, 0.8F);
				Skills.award(survivor, Skill.ENGINEER, 80);
				syncParts(level);
				survivor.displayClientMessage(Component.translatable(story.helicopterReady() ? "message.deadlands.heli.ready" : "message.deadlands.heli.fitted",
					held.getItem().getName()), false);
				if (story.helicopterReady()) {
					Quests.chronicle(survivor, "chronicle.deadlands.story.heli_ready");
				}
			} else {
				survivor.displayClientMessage(Component.translatable("vehicle.deadlands.not_needed"), true);
			}
			return InteractionResult.SUCCESS;
		}
		if (!story.helicopterReady()) {
			MutableComponent list = Component.empty();
			boolean first = true;
			for (Item part : DLStory.helicopterParts()) {
				if (!story.heliParts().contains(DLStory.idOf(part))) {
					list.append(first ? Component.empty() : Component.literal(", ")).append(part.getName());
					first = false;
				}
			}
			survivor.displayClientMessage(Component.translatable("message.deadlands.heli.needs", list), false);
			return InteractionResult.SUCCESS;
		}
		Endings.offer(survivor, this);
		return InteractionResult.SUCCESS;
	}

	// ======================================================================================== extraction

	/** Begin the extraction. {@code playersBoard}: whether survivors nearby climb in (not for staying behind). */
	public void beginExtraction(ServerLevel level, String ending, boolean playersBoard) {
		this.ending = ending;
		this.playersBoard = playersBoard;
		this.startX = getX();
		this.startY = getY();
		this.startZ = getZ();
		entityData.set(DATA_LIFT, 0);
		if (playersBoard) {
			for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(16.0), p -> !p.isSpectator())) {
				if (getPassengers().size() < SEATS) {
					player.startRiding(this, true, true);
				}
			}
		}
		for (Entity companion : Endings.gatherCompanions(level, this)) {
			if (getPassengers().size() < SEATS) {
				companion.startRiding(this, true, true);
			}
		}
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.NEUTRAL, 2.0F, 0.5F);
		com.deadlands.cutscene.Cutscenes.playNear(level, com.deadlands.cutscene.Cutscenes.EXTRACTION, position(), getYRot(), 24.0);
	}

	private void liftTick(ServerLevel level, int lift) {
		entityData.set(DATA_LIFT, lift + 1);
		if (lift % 10 == 0) {
			level.playSound(null, getX(), getY(), getZ(), com.deadlands.registry.DLSounds.HELICOPTER_ROTOR.value(), SoundSource.NEUTRAL, 4.0F, 0.8F + Math.min(0.4F, lift / 400.0F));
		}
		if (lift > SPIN_UP) {
			move(MoverType.SELF, new Vec3(0.0, Math.min(0.25, (lift - SPIN_UP) * 0.004), 0.0));
			setYRot(getYRot() + 0.3F);
		}
		if (lift >= LIFT_TICKS) {
			List<ServerPlayer> aboard = new ArrayList<>();
			for (Entity passenger : List.copyOf(getPassengers())) {
				passenger.stopRiding();
				if (passenger instanceof ServerPlayer player) {
					aboard.add(player);
					player.teleportTo(startX + 3.0, startY + 0.5, startZ + 3.0);
				} else {
					passenger.discard(); // they have gone
				}
			}
			Endings.finish(level, ending, aboard);
			discard();
		}
	}

	// ======================================================================================== the rest

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (source.getEntity() instanceof Player player && player.getAbilities().instabuild && lift() < 0) {
			discard();
			return true;
		}
		return false;
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
	protected boolean canAddPassenger(Entity entity) {
		return getPassengers().size() < SEATS && lift() >= 0;
	}

	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
		int index = Math.max(0, getPassengers().indexOf(passenger));
		double side = index % 2 == 0 ? 0.6 : -0.6;
		double along = 1.0 - (index / 2) * 0.8;
		return new Vec3(side, 0.7, along).yRot(-getYRot() * (float) (Math.PI / 180.0));
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putString("Ending", ending);
		output.putInt("Lift", lift());
		output.putBoolean("PlayersBoard", playersBoard);
		output.putDouble("StartX", startX);
		output.putDouble("StartY", startY);
		output.putDouble("StartZ", startZ);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		ending = input.getStringOr("Ending", "");
		entityData.set(DATA_LIFT, input.getIntOr("Lift", -1));
		playersBoard = input.getBooleanOr("PlayersBoard", true);
		startX = input.getDoubleOr("StartX", getX());
		startY = input.getDoubleOr("StartY", getY());
		startZ = input.getDoubleOr("StartZ", getZ());
	}
}
