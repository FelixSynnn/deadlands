package com.deadlands.client.horror;

import com.deadlands.entity.InfectedEntity;
import com.deadlands.registry.DLEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

/**
 * Phase 4 jumpscares: a Stalker only this survivor sees. It is a client-side copy of the real model
 * (never on the server, so it cannot hurt anyone and other players never see it), placed by a scare's
 * {@code apparition} steps: in the flashlight beam, ahead, on the ceiling (hanging upside down), in a
 * doorway, behind a window, behind the survivor, or dropping from where it is. It always faces the
 * survivor and is gone when its time runs out or a scare hides it.
 */
public final class Apparition {
	/** A client-only entity id far from anything the server hands out. */
	public static final int ID = -0x5D1A0001;
	private static InfectedEntity figure;
	private static int left;
	private static boolean upsideDown;
	private static Vec3 direction;
	private static Vec3 at;
	private static int dropping;

	private Apparition() {
	}

	/** Whether the renderer should draw this entity upside down (the ceiling apparition). */
	public static boolean upsideDown(Entity entity) {
		return upsideDown && entity.getId() == ID;
	}

	public static void show(Minecraft minecraft, String where, float distance, int ticks) {
		LocalPlayer player = minecraft.player;
		ClientLevel level = minecraft.level;
		if (player == null || level == null) {
			return;
		}
		Vec3 look = player.getViewVector(1.0F);
		Vec3 flat = new Vec3(look.x, 0.0, look.z);
		flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
		float d = Math.max(0.8F, distance);
		if (where.equals("drop")) {
			dropping = 7;
			left = Math.max(left, 8);
			return;
		}
		upsideDown = false;
		Vec3 eye = player.getEyePosition();
		switch (where) {
			case "beam", "ahead" -> {
				if (direction == null) {
					direction = flat;
				}
				at = ground(level, eye.add(direction.scale(d)));
			}
			case "behind" -> at = ground(level, eye.subtract(flat.scale(d)));
			case "ceiling" -> {
				Vec3 spot = eye.add(flat.scale(1.3));
				int y = BlockPos.containing(spot).getY();
				for (int up = 0; up <= 6; up++) {
					if (!level.getBlockState(BlockPos.containing(spot.x, y + up, spot.z)).isAir()) {
						y += up;
						break;
					}
				}
				at = new Vec3(spot.x, y - 1.9, spot.z);
				upsideDown = true;
			}
			case "doorway" -> at = doorway(level, player, flat);
			case "window" -> at = window(level, eye, flat);
			default -> at = ground(level, eye.add(flat.scale(d)));
		}
		if (figure == null || figure.isRemoved()) {
			figure = (InfectedEntity) DLEntities.STALKER.create(level, EntitySpawnReason.LOAD);
			if (figure == null) {
				return;
			}
			figure.setId(ID);
			figure.setNoGravity(true);
			figure.noPhysics = true;
			figure.setSilent(true);
			place(player);
			level.addEntity(figure);
		}
		left = Math.max(1, ticks);
		place(player);
	}

	public static void hide(Minecraft minecraft) {
		if (figure != null && minecraft.level != null) {
			minecraft.level.removeEntity(ID, Entity.RemovalReason.DISCARDED);
		}
		figure = null;
		direction = null;
		upsideDown = false;
		dropping = 0;
		left = 0;
	}

	public static void tick(Minecraft minecraft) {
		if (figure == null) {
			return;
		}
		if (minecraft.player == null || minecraft.level == null || figure.level() != minecraft.level || --left <= 0) {
			hide(minecraft);
			return;
		}
		if (dropping > 0) {
			dropping--;
			at = at.add(0.0, -0.4, 0.0);
			if (dropping == 0) {
				hide(minecraft);
				return;
			}
		}
		place(minecraft.player);
	}

	private static void place(LocalPlayer player) {
		if (figure == null || at == null) {
			return;
		}
		double dx = player.getX() - at.x;
		double dz = player.getZ() - at.z;
		float yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
		figure.snapTo(at.x, at.y, at.z, yaw, 0.0F);
		figure.setYHeadRot(yaw);
		figure.setYBodyRot(yaw);
		figure.setOldPosAndRot();
	}

	/** Stands the point on the first solid ground below it (within eight blocks). */
	private static Vec3 ground(ClientLevel level, Vec3 point) {
		BlockPos pos = BlockPos.containing(point.x, point.y + 1.0, point.z);
		for (int down = 0; down < 10; down++) {
			BlockPos below = pos.below(down + 1);
			if (!level.getBlockState(below).isAir() && level.getBlockState(below.above()).isAir()) {
				return new Vec3(point.x, below.getY() + 1.0, point.z);
			}
		}
		return point;
	}

	/** The nearest door (or a doorway's air gap) ahead within eight blocks; otherwise five blocks ahead. */
	private static Vec3 doorway(ClientLevel level, LocalPlayer player, Vec3 flat) {
		BlockPos center = player.blockPosition();
		Vec3 best = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(center.offset(-8, -2, -8), center.offset(8, 2, 8))) {
			BlockState state = level.getBlockState(p);
			if (!(state.getBlock() instanceof DoorBlock) || state.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) {
				continue;
			}
			Vec3 spot = Vec3.atBottomCenterOf(p);
			Vec3 to = spot.subtract(player.position());
			double distance = to.length();
			if (distance > 2.5 && distance < bestDistance && new Vec3(to.x, 0.0, to.z).normalize().dot(flat) > 0.2) {
				best = spot;
				bestDistance = distance;
			}
		}
		return best != null ? best : ground(level, player.getEyePosition().add(flat.scale(5.0)));
	}

	/** Just behind the glass straight ahead, its face at the pane. */
	private static Vec3 window(ClientLevel level, Vec3 eye, Vec3 flat) {
		for (double d = 0.8; d <= 2.6; d += 0.3) {
			Vec3 point = eye.add(flat.scale(d));
			BlockState state = level.getBlockState(BlockPos.containing(point));
			if (BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().contains("glass")) {
				Vec3 behind = eye.add(flat.scale(d + 1.0));
				return new Vec3(behind.x, eye.y - 1.65, behind.z);
			}
		}
		return eye.add(flat.scale(2.5)).subtract(0.0, 1.65, 0.0);
	}
}
