package com.deadlands.client.sound;

import com.deadlands.registry.DLSounds;
import com.deadlands.vehicle.VehicleEntity;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

/**
 * The engine of every vehicle that has a driver: one looping sound per vehicle that follows it,
 * rises in pitch and volume with its speed, idles when it stands, and stops when the driver gets
 * out or the vehicle is gone.
 */
public final class VehicleEngineSounds {
	private static final Map<Integer, Engine> PLAYING = new HashMap<>();

	private VehicleEngineSounds() {
	}

	/** Once a client tick: start an engine for every driven vehicle in sight that has none. */
	public static void tick(Minecraft minecraft) {
		if (minecraft.level == null || minecraft.isPaused()) {
			return;
		}
		PLAYING.values().removeIf(Engine::isStopped);
		for (Entity entity : minecraft.level.entitiesForRendering()) {
			if (entity instanceof VehicleEntity vehicle && vehicle.getControllingPassenger() != null && vehicle.drivable() && vehicle.fuel() > 0.0F
				&& !PLAYING.containsKey(vehicle.getId())) {
				Engine engine = new Engine(vehicle);
				PLAYING.put(vehicle.getId(), engine);
				minecraft.getSoundManager().play(engine);
			}
		}
	}

	public static void reset() {
		PLAYING.clear();
	}

	private static final class Engine extends AbstractTickableSoundInstance {
		private final VehicleEntity vehicle;
		private double lastX;
		private double lastZ;

		Engine(VehicleEntity vehicle) {
			super(DLSounds.VEHICLE_ENGINE.value(), SoundSource.NEUTRAL, RandomSource.create(vehicle.getId()));
			this.vehicle = vehicle;
			this.looping = true;
			this.delay = 0;
			this.volume = 0.5F;
			this.pitch = 0.7F;
			this.lastX = vehicle.getX();
			this.lastZ = vehicle.getZ();
			follow();
		}

		@Override
		public void tick() {
			if (vehicle.isRemoved() || vehicle.getControllingPassenger() == null || !vehicle.drivable() || vehicle.fuel() <= 0.0F) {
				stop();
				return;
			}
			double moved = Math.hypot(vehicle.getX() - lastX, vehicle.getZ() - lastZ);
			lastX = vehicle.getX();
			lastZ = vehicle.getZ();
			float rev = Mth.clamp((float) (moved / vehicle.kind().maxSpeed()), 0.0F, 1.0F);
			pitch += (0.7F + 0.7F * rev - pitch) * 0.2F;
			volume += (0.5F + 1.0F * rev - volume) * 0.2F;
			follow();
		}

		private void follow() {
			this.x = vehicle.getX();
			this.y = vehicle.getY() + 0.5;
			this.z = vehicle.getZ();
		}
	}
}
