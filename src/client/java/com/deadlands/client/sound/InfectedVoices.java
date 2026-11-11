package com.deadlands.client.sound;

import com.deadlands.bridge.ClientBridge;
import com.deadlands.config.ConfigManager;
import com.deadlands.entity.InfectedEntity;
import com.deadlands.entity.InfectedKind;
import com.deadlands.registry.DLSounds;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * What each survivor hears of the infected around them, decided on their own client.
 *
 * <ul>
 *   <li><b>Idle</b> (Minecraft's ambient timer): the kind's groan within 20 blocks, a muffled one
 *   behind walls, a thin distant moan to about 44 blocks. Idle voices share one budget (one every
 *   few seconds at most) and a crowd thins its own voices out, so ten infected do not groan ten
 *   times as often: silence stays part of it.</li>
 *   <li><b>Alert</b>: the moment an infected takes a target (the server's hunting flag), one cry:
 *   a sharp inhale and a snarl, never twice in a row from the same one.</li>
 *   <li><b>Chase</b>: while it hunts, short pursuit sounds (panting snarls, a runner's rasping
 *   breath, a brute's roaring breath) at its own pace, sharing a budget with every other chaser.</li>
 *   <li><b>Breath</b> within a few blocks; the Stalker only ever breathes and hisses.</li>
 * </ul>
 * No voice is a loop, each infected keeps its own pitch with a little variation per call, and no
 * more than six play at once. With {@code audio.infectedVoiceEffects} off, every infected uses its
 * own voice at any distance. Also adds the charred's smoke.
 */
public final class InfectedVoices implements ClientBridge.InfectedHandler {
	private static final int MAX_VOICES = 6;
	private static final double CLEAR_RANGE = 20.0;
	private static final double DISTANT_RANGE = 44.0;
	private static final double CHASE_RANGE = 32.0;
	private static final double BREATH_RANGE = 4.5;
	private static final int BREATH_CHECK_TICKS = 40;

	private final List<SoundInstance> playing = new ArrayList<>();
	private final Int2IntOpenHashMap nextChase = new Int2IntOpenHashMap();
	private final Int2IntOpenHashMap lastAlert = new Int2IntOpenHashMap();
	private final IntOpenHashSet hunting = new IntOpenHashSet();
	private final RandomSource random = RandomSource.create();
	private int clock;
	private int idleWait;
	private int chaseWait;
	private int nearby;
	private int crowdSize;

	@Override
	public void ambient(InfectedEntity entity) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || !entity.isAlive() || !minecraft.gameRenderer.getMainCamera().isInitialized()) {
			return;
		}
		if (entity.hasFlag(InfectedEntity.FLAG_HUNTING) || idleWait > 0 || !room(minecraft.getSoundManager())) {
			return; // hunters use their chase voice; otherwise somebody else just spoke
		}
		// A quarter of idle calls stay silent anyway, more in a crowd.
		float crowd = Math.min(0.7F, crowdSize / 14.0F);
		if (random.nextFloat() < 0.25F + crowd) {
			return;
		}
		Vec3 ear = minecraft.gameRenderer.getMainCamera().position();
		double distance = ear.distanceTo(entity.getEyePosition());
		InfectedKind kind = entity.kind();
		SoundEvent sound = kind.ambientSound().value();
		// A volume above 1 does not play louder, it carries farther (16 blocks per unit): the plain
		// voice must still be heard at CLEAR_RANGE, where the distant one takes over.
		float volume = kind == InfectedKind.BRUTE ? 2.0F : 1.5F;
		float pitch = voicePitch(entity) * (0.95F + random.nextFloat() * 0.1F);
		if (ConfigManager.get().audio.infectedVoiceEffects) {
			if (distance > DISTANT_RANGE) {
				return;
			}
			if (distance > CLEAR_RANGE) {
				sound = DLSounds.INFECTED_DISTANT.value();
				volume = 2.9F; // carries to about 46 blocks
				pitch *= kind == InfectedKind.BRUTE ? 0.8F : 1.0F;
			} else if (occluded(minecraft.level, ear, entity)) {
				sound = DLSounds.INFECTED_MUFFLED.value();
				volume = 1.25F;
			}
		}
		play(minecraft.getSoundManager(), new InfectedVoiceSound(sound, entity, volume * (0.85F + random.nextFloat() * 0.15F), pitch));
		idleWait = 30 + random.nextInt(50);
	}

	@Override
	public void tick(InfectedEntity entity) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null || !entity.isAlive() || minecraft.player == null) {
			return;
		}
		RandomSource own = entity.getRandom();
		if (entity.kind() == InfectedKind.CHARRED && own.nextInt(4) == 0) {
			level.addParticle(ParticleTypes.SMOKE, entity.getRandomX(0.4), entity.getRandomY(), entity.getRandomZ(0.4), 0.0, 0.02, 0.0);
		}
		int id = entity.getId();
		double distance = entity.distanceTo(minecraft.player);
		if (distance < 24.0) {
			nearby++;
		}
		boolean hunts = entity.hasFlag(InfectedEntity.FLAG_HUNTING);
		if (hunts && hunting.add(id)) {
			alert(minecraft, entity, distance);
		} else if (!hunts && hunting.remove(id)) {
			nextChase.remove(id);
		}
		if (hunts && distance < CHASE_RANGE && clock >= nextChase.getOrDefault(id, 0) && chaseWait <= 0 && room(minecraft.getSoundManager())) {
			chase(minecraft, entity, distance);
		}
		if ((entity.tickCount + id) % BREATH_CHECK_TICKS == 0 && distance < BREATH_RANGE && own.nextBoolean() && room(minecraft.getSoundManager())) {
			play(minecraft.getSoundManager(), new InfectedVoiceSound(DLSounds.INFECTED_BREATH.value(), entity, 0.5F, voicePitch(entity)));
		}
	}

	/** Once per client tick, before the infected tick: shared budgets and bookkeeping. */
	public void tickAll(Minecraft minecraft) {
		clock++;
		if (idleWait > 0) {
			idleWait--;
		}
		if (chaseWait > 0) {
			chaseWait--;
		}
		crowdSize = nearby; // counted while the infected ticked
		nearby = 0;
		if (clock % 200 == 0 && minecraft.level != null) {
			ClientLevel level = minecraft.level;
			hunting.removeIf(id -> level.getEntity(id) == null);
			nextChase.keySet().removeIf(id -> level.getEntity(id) == null);
			lastAlert.keySet().removeIf(id -> level.getEntity(id) == null);
		}
	}

	public void reset() {
		playing.clear();
		hunting.clear();
		nextChase.clear();
		lastAlert.clear();
		idleWait = 0;
		chaseWait = 0;
	}

	private void alert(Minecraft minecraft, InfectedEntity entity, double distance) {
		int id = entity.getId();
		InfectedKind kind = entity.kind();
		nextChase.put(id, clock + 30 + random.nextInt(40));
		if (kind == InfectedKind.STALKER || distance > DISTANT_RANGE || clock - lastAlert.getOrDefault(id, -10_000) < 400
			|| !room(minecraft.getSoundManager())) {
			return; // the stalker hunts in silence
		}
		lastAlert.put(id, clock);
		boolean muffled = ConfigManager.get().audio.infectedVoiceEffects
			&& occluded(minecraft.level, minecraft.gameRenderer.getMainCamera().position(), entity);
		SoundEvent sound = DLSounds.infectedAlert(kind.id());
		float volume = (kind == InfectedKind.BRUTE ? 2.4F : 2.0F) * (muffled ? 0.6F : 1.0F);
		play(minecraft.getSoundManager(), new InfectedVoiceSound(sound, entity, volume, voicePitch(entity) * (muffled ? 0.88F : 1.0F)));
		chaseWait = Math.max(chaseWait, 10);
	}

	private void chase(Minecraft minecraft, InfectedEntity entity, double distance) {
		InfectedKind kind = entity.kind();
		int id = entity.getId();
		int pace = switch (kind) {
			case RUNNER -> 30 + random.nextInt(50);
			case STALKER -> 120 + random.nextInt(160);
			case BRUTE -> 60 + random.nextInt(80);
			default -> 50 + random.nextInt(90);
		};
		nextChase.put(id, clock + pace);
		chaseWait = 8 + random.nextInt(10);
		boolean muffled = ConfigManager.get().audio.infectedVoiceEffects
			&& occluded(minecraft.level, minecraft.gameRenderer.getMainCamera().position(), entity);
		float volume = (kind == InfectedKind.STALKER ? 0.8F : kind == InfectedKind.BRUTE ? 2.2F : 1.8F) * (muffled ? 0.55F : 1.0F)
			* (0.85F + random.nextFloat() * 0.15F);
		float pitch = voicePitch(entity) * (0.94F + random.nextFloat() * 0.12F) * (muffled ? 0.88F : 1.0F);
		play(minecraft.getSoundManager(), new InfectedVoiceSound(DLSounds.infectedChase(kind.id()), entity, volume, pitch));
	}

	private boolean room(SoundManager sounds) {
		playing.removeIf(sound -> !sounds.isActive(sound));
		return playing.size() < MAX_VOICES;
	}

	private void play(SoundManager sounds, SoundInstance sound) {
		sounds.play(sound);
		playing.add(sound);
	}

	/** Each infected keeps its own pitch: lower for the big ones, higher for the thin ones. */
	private static float voicePitch(InfectedEntity entity) {
		float own = 0.92F + (entity.getUUID().hashCode() & 15) / 100.0F;
		float byKind = switch (entity.kind()) {
			case BRUTE -> 0.8F;
			case SCREAMER, RUNNER -> 1.08F;
			case BLOATER -> 0.9F;
			default -> 1.0F;
		};
		return own * byKind / Math.max(0.85F, entity.heightScale());
	}

	/** Is there a solid block between the listener and the infected's head? */
	private static boolean occluded(ClientLevel level, Vec3 ear, InfectedEntity entity) {
		HitResult hit = level.clip(new ClipContext(ear, entity.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
			CollisionContext.empty()));
		return hit.getType() != HitResult.Type.MISS;
	}
}
