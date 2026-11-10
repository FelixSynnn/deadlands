package com.deadlands.progression;

import com.deadlands.Deadlands;
import com.deadlands.network.JournalPayload;
import com.deadlands.npc.Faction;
import com.deadlands.npc.Reputation;
import com.deadlands.skill.Skill;
import com.deadlands.skill.Skills;
import com.deadlands.vehicle.VehicleEntity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * The journal's QUESTS page: the story's quests ({@link com.deadlands.story.Quests}), the vehicle a
 * survivor is restoring, and how each faction regards them.
 *
 * <p>Looking over a vehicle that does not run makes it the survivor's restoration task (one at a
 * time; looking over another replaces it). The page lists where it stands and what it still needs,
 * and is kept up to date while the vehicle is loaded. Getting it running, with fuel in the tank,
 * finishes the task and earns Engineer experience. The task is saved with the player and kept
 * through death.
 */
public final class Tasks {
	/** The vehicle being restored. */
	public record Tracked(UUID vehicle, String nameKey, BlockPos pos) {
		public static final Codec<Tracked> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.CODEC.fieldOf("vehicle").forGetter(Tracked::vehicle),
			Codec.STRING.fieldOf("name").forGetter(Tracked::nameKey),
			BlockPos.CODEC.fieldOf("pos").forGetter(Tracked::pos)
		).apply(instance, Tracked::new));
	}

	public static final AttachmentType<Tracked> VEHICLE = AttachmentRegistry.create(Deadlands.id("vehicle_task"),
		builder -> builder.persistent(Tracked.CODEC).copyOnDeath());
	public static final int RESTORED_XP = 100;
	private static final int CHECK_TICKS = 40;
	/** What was last sent to each player, so the page is only sent when it changes. */
	private static final Map<UUID, Integer> SENT = new HashMap<>();

	private Tasks() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % CHECK_TICKS == 7) {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					update(player);
				}
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SENT.remove(handler.getPlayer().getUUID()));
	}

	/** Make {@code vehicle} the survivor's restoration task. */
	public static void track(ServerPlayer player, VehicleEntity vehicle) {
		if (vehicle.burned()) {
			return;
		}
		Tracked current = player.getAttached(VEHICLE);
		if (current == null || !current.vehicle().equals(vehicle.getUUID())) {
			player.setAttached(VEHICLE, new Tracked(vehicle.getUUID(), vehicle.kind().translationKey(), vehicle.blockPosition()));
			player.displayClientMessage(Component.translatable("message.deadlands.task.started", Component.translatable(vehicle.kind().translationKey())), false);
		}
		sync(player, false);
	}

	private static void update(ServerPlayer player) {
		Tracked tracked = player.getAttached(VEHICLE);
		if (tracked != null && player.level().getEntity(tracked.vehicle()) instanceof VehicleEntity vehicle) {
			Component name = Component.translatable(tracked.nameKey());
			if (vehicle.burned() || !vehicle.isAlive()) {
				player.removeAttached(VEHICLE);
				player.displayClientMessage(Component.translatable("message.deadlands.task.lost", name), false);
			} else if (vehicle.drivable() && vehicle.fuel() > 0.0F) {
				player.removeAttached(VEHICLE);
				player.displayClientMessage(Component.translatable("message.deadlands.task.done", name), false);
				Skills.award(player, Skill.ENGINEER, RESTORED_XP);
			} else if (vehicle.blockPosition().distManhattan(tracked.pos()) > 4) {
				player.setAttached(VEHICLE, new Tracked(tracked.vehicle(), tracked.nameKey(), vehicle.blockPosition()));
			}
		}
		sync(player, false);
	}

	/** Send the page if it has changed (or regardless, when {@code force} is set: the client asked). */
	public static void sync(ServerPlayer player, boolean force) {
		if (!ServerPlayNetworking.canSend(player, JournalPayload.TYPE)) {
			return;
		}
		List<Component> lines = new ArrayList<>();
		List<Integer> styles = new ArrayList<>();
		for (com.deadlands.story.Quests.Line line : com.deadlands.story.Quests.journal(player)) {
			lines.add(line.text());
			styles.add(line.style());
		}
		Tracked tracked = player.getAttached(VEHICLE);
		if (tracked != null) {
			lines.add(Component.translatable("journal.deadlands.tasks.restore", Component.translatable(tracked.nameKey())));
			styles.add(0);
			lines.add(Component.translatable("journal.deadlands.tasks.where", tracked.pos().getX(), tracked.pos().getZ()));
			styles.add(3);
			Entity entity = player.level().getEntity(tracked.vehicle());
			if (entity instanceof VehicleEntity vehicle) {
				for (Component need : vehicle.needs()) {
					lines.add(Component.translatable("journal.deadlands.tasks.need", need));
					styles.add(1);
				}
			} else {
				lines.add(Component.translatable("journal.deadlands.tasks.far"));
				styles.add(3);
			}
		}
		lines.add(Component.translatable("journal.deadlands.standing.title"));
		styles.add(0);
		for (Faction faction : Faction.values()) {
			int value = Reputation.get(player, faction);
			lines.add(Component.translatable("journal.deadlands.tasks.standing", Component.translatable(faction.translationKey()),
				Component.translatable(Reputation.Standing.of(value).translationKey()), value));
			styles.add(3);
		}
		StringBuilder key = new StringBuilder();
		for (int i = 0; i < lines.size(); i++) {
			key.append(styles.get(i)).append(lines.get(i).getString()).append('\n');
		}
		int hash = key.toString().hashCode();
		Integer last = SENT.put(player.getUUID(), hash);
		if (force || last == null || last != hash) {
			ServerPlayNetworking.send(player, new JournalPayload(lines, styles));
		}
	}
}
