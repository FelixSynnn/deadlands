package com.deadlands.container;

import com.deadlands.Deadlands;
import com.deadlands.registry.DLSounds;
import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.Block;

/**
 * Every kind of DEADLANDS loot container: size, look, how it opens, its sounds and loot tables.
 *
 * <p>Geometry is in block pixels for a container whose front faces north. The moving part (lid,
 * drawer or doors) is drawn by {@code LootContainerRenderer}; the body is a baked block model
 * (tools/assetgen/gen_containers.py) built from the same numbers.
 */
public enum ContainerKind implements StringRepresentable {
	MILITARY_CRATE(3, ContainerStyle.WOOD, Motion.LID, 0.055F, 0.20F,
		Block.box(1, 0, 2, 15, 12, 14), DLSounds.CRATE_OPEN, DLSounds.CRATE_CLOSE),
	FILING_CABINET(2, ContainerStyle.METAL, Motion.DRAWER, 0.11F, 0.12F,
		Block.box(2, 0, 1, 14, 16, 15), DLSounds.DRAWER_OPEN, DLSounds.DRAWER_CLOSE),
	MEDICAL_CABINET(2, ContainerStyle.MEDICAL, Motion.DOUBLE_DOOR, 0.09F, 0.18F,
		Block.box(1, 0, 3, 15, 16, 15), DLSounds.CABINET_OPEN, DLSounds.CABINET_CLOSE),
	SUPPLY_LOCKER(2, ContainerStyle.METAL, Motion.DOOR, 0.085F, 0.10F,
		Block.box(2, 0, 2, 14, 16, 15), DLSounds.LOCKER_OPEN, DLSounds.LOCKER_CLOSE),
	TOOLBOX(1, ContainerStyle.METAL, Motion.LID, 0.13F, 0.15F,
		Block.box(3, 0, 5, 13, 8, 11), DLSounds.TOOLBOX_OPEN, DLSounds.TOOLBOX_CLOSE),
	// Household and school containers (Milestone 3). They share the opening sounds of the
	// containers built the same way (doors, drawers, a lid) until the audio pass in Milestone 9.
	KITCHEN_CUPBOARD(2, ContainerStyle.WOOD, Motion.DOUBLE_DOOR, 0.10F, 0.10F,
		Block.box(1, 0, 3, 15, 16, 15), DLSounds.CABINET_OPEN, DLSounds.CABINET_CLOSE),
	FRIDGE(3, ContainerStyle.METAL, Motion.DOOR, 0.08F, 0.08F,
		Block.box(2, 0, 2, 14, 16, 15), DLSounds.CABINET_OPEN, DLSounds.CABINET_CLOSE),
	DRESSER(2, ContainerStyle.WOOD, Motion.DRAWER, 0.11F, 0.12F,
		Block.box(1, 0, 1, 15, 14, 14), DLSounds.DRAWER_OPEN, DLSounds.DRAWER_CLOSE),
	SAFE(2, ContainerStyle.METAL, Motion.DOOR, 0.045F, 0.35F,
		Block.box(2, 0, 2, 14, 13, 14), DLSounds.LOCKER_OPEN, DLSounds.LOCKER_CLOSE),
	SCHOOL_DESK(1, ContainerStyle.WOOD, Motion.LID, 0.12F, 0.06F,
		Block.box(1, 0, 2, 15, 12, 14), DLSounds.TOOLBOX_OPEN, DLSounds.TOOLBOX_CLOSE);

	public static final Codec<ContainerKind> CODEC = StringRepresentable.fromEnum(ContainerKind::values);

	/** How the moving part animates. */
	public enum Motion {
		LID, DRAWER, DOOR, DOUBLE_DOOR
	}

	private final String id = name().toLowerCase(Locale.ROOT);
	private final int rows;
	private final ContainerStyle style;
	private final Motion motion;
	private final float openSpeed;
	private final float rareChance;
	private final VoxelShape northShape;
	private final Holder<SoundEvent> openSound;
	private final Holder<SoundEvent> closeSound;

	ContainerKind(int rows, ContainerStyle style, Motion motion, float openSpeed, float rareChance, VoxelShape northShape,
		Holder<SoundEvent> openSound, Holder<SoundEvent> closeSound) {
		this.rows = rows;
		this.style = style;
		this.motion = motion;
		this.openSpeed = openSpeed;
		this.rareChance = rareChance;
		this.northShape = northShape;
		this.openSound = openSound;
		this.closeSound = closeSound;
	}

	public String id() {
		return id;
	}

	@Override
	public String getSerializedName() {
		return id;
	}

	public int rows() {
		return rows;
	}

	public int slots() {
		return rows * 9;
	}

	public ContainerStyle style() {
		return style;
	}

	public Motion motion() {
		return motion;
	}

	/** Openness gained per tick (1.0 = fully open). Heavy crates are slow. */
	public float openSpeed() {
		return openSpeed;
	}

	/** Base chance of an extra roll on the rare table, scaled by the loot rarity setting. */
	public float rareChance() {
		return rareChance;
	}

	public VoxelShape northShape() {
		return northShape;
	}

	public Holder<SoundEvent> openSound() {
		return openSound;
	}

	public Holder<SoundEvent> closeSound() {
		return closeSound;
	}

	public ResourceKey<LootTable> lootTable() {
		return ResourceKey.create(Registries.LOOT_TABLE, Deadlands.id("containers/" + id));
	}

	public ResourceKey<LootTable> rareLootTable() {
		return ResourceKey.create(Registries.LOOT_TABLE, Deadlands.id("containers/" + id + "_rare"));
	}
}
