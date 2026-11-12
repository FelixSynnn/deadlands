package com.deadlands.client.cutscene;

import com.deadlands.npc.NpcCharacter;
import com.deadlands.npc.NpcEntity;
import com.deadlands.registry.DLEntities;
import com.deadlands.registry.DLMarkers;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A cutscene's cast: client-only stand-ins (never on the server, so nobody else sees them and they
 * cannot be hurt or saved) for the story characters and the infected. Each one appears at its start
 * tick, walks from one point to another between its move ticks (standing on the ground there), faces
 * where it walks or a point it is given, and is removed at its end tick or when the scene stops.
 */
final class CutsceneActors {
	/** Client-only entity ids, far from anything the server hands out. */
	private static final int FIRST_ID = -0x5D1B0000;

	private record Live(CutsceneLibrary.ActorDef def, LivingEntity entity) {
	}

	private static final List<Live> LIVE = new ArrayList<>();
	private static final List<CutsceneLibrary.ActorDef> WAITING = new ArrayList<>();
	private static Vec3 anchor = Vec3.ZERO;
	private static float yaw;
	private static int nextId = FIRST_ID;

	private CutsceneActors() {
	}

	static void begin(List<CutsceneLibrary.ActorDef> cast, Vec3 at, float facing) {
		clear();
		WAITING.addAll(cast);
		anchor = at;
		yaw = facing;
	}

	static void tick(int ticks) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null) {
			clear();
			return;
		}
		for (CutsceneLibrary.ActorDef def : List.copyOf(WAITING)) {
			if (ticks >= def.start()) {
				WAITING.remove(def);
				LivingEntity entity = create(level, def);
				if (entity != null) {
					LIVE.add(new Live(def, entity));
					place(level, def, entity, ticks, true);
					level.addEntity(entity);
				}
			}
		}
		for (Live live : List.copyOf(LIVE)) {
			if (ticks >= live.def().end() || live.entity().level() != level) {
				remove(level, live);
			} else {
				place(level, live.def(), live.entity(), ticks, false);
			}
		}
	}

	static void clear() {
		ClientLevel level = Minecraft.getInstance().level;
		for (Live live : List.copyOf(LIVE)) {
			if (level != null) {
				remove(level, live);
			}
		}
		LIVE.clear();
		WAITING.clear();
		nextId = FIRST_ID;
	}

	private static void remove(ClientLevel level, Live live) {
		level.removeEntity(live.entity().getId(), Entity.RemovalReason.DISCARDED);
		LIVE.remove(live);
	}

	private static @Nullable LivingEntity create(ClientLevel level, CutsceneLibrary.ActorDef def) {
		LivingEntity entity;
		NpcCharacter character = NpcCharacter.byId(def.who());
		if (character != null) {
			NpcEntity npc = DLMarkers.SURVIVOR.create(level, EntitySpawnReason.LOAD);
			if (npc == null) {
				return null;
			}
			npc.actAs(character);
			entity = npc;
		} else {
			Identifier id = def.who().contains(":") ? Identifier.tryParse(def.who()) : Identifier.fromNamespaceAndPath("deadlands", def.who());
			EntityType<?> type = id == null ? null : BuiltInRegistries.ENTITY_TYPE.getValue(id);
			Entity made = type == null ? null : type.create(level, EntitySpawnReason.LOAD);
			if (!(made instanceof LivingEntity living)) {
				return null;
			}
			entity = living;
		}
		entity.setId(nextId--);
		entity.setNoGravity(true);
		entity.noPhysics = true;
		entity.setSilent(true);
		entity.setInvulnerable(true);
		def.scale().ifPresent(scale -> {
			if (entity.getAttribute(Attributes.SCALE) != null) {
				entity.getAttribute(Attributes.SCALE).setBaseValue(scale);
			}
		});
		def.item().ifPresent(itemId -> {
			Item item = BuiltInRegistries.ITEM.getValue(itemId);
			entity.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
		});
		return entity;
	}

	/** Stand the actor where it should be at {@code ticks}, on the ground, facing its way. */
	private static void place(ClientLevel level, CutsceneLibrary.ActorDef def, LivingEntity entity, int ticks, boolean first) {
		int moveStart = def.moveStart().orElse(def.start());
		int moveEnd = Math.max(moveStart + 1, def.moveEnd().orElse(def.end()));
		float p = Mth.clamp((ticks - moveStart) / (float) (moveEnd - moveStart), 0.0F, 1.0F);
		Vec3 local = def.from().lerp(def.to(), p);
		Vec3 world = anchor.add(turn(local));
		double ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(world.x), Mth.floor(world.z));
		Vec3 at = new Vec3(world.x, ground, world.z);
		Vec3 old = entity.position();
		boolean walking = !def.from().equals(def.to()) && ticks >= moveStart && ticks < moveEnd;
		Vec3 target = def.face().map(face -> anchor.add(turn(face))).orElse(anchor.add(turn(def.to())));
		Vec3 heading = walking ? anchor.add(turn(def.to())).subtract(at) : target.subtract(at);
		float facing = heading.horizontalDistanceSqr() < 1.0E-4 ? entity.getYRot()
			: (float) (Mth.atan2(heading.z, heading.x) * Mth.RAD_TO_DEG) - 90.0F;
		if (first) {
			entity.snapTo(at.x, at.y, at.z, facing, 0.0F);
			entity.setOldPosAndRot();
		} else {
			entity.setOldPosAndRot();
			entity.setPos(at);
			entity.setYRot(facing);
		}
		entity.setYHeadRot(facing);
		entity.yBodyRot = facing;
		entity.yBodyRotO = facing;
		entity.yHeadRotO = facing;
		float moved = first ? 0.0F : (float) old.subtract(at).horizontalDistance();
		entity.walkAnimation.update(Math.min(moved * 4.0F, 1.0F), 0.4F, 1.0F);
	}

	private static Vec3 turn(Vec3 offset) {
		return offset.yRot(-yaw * Mth.DEG_TO_RAD);
	}
}
