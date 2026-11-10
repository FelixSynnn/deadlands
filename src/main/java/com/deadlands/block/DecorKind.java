package com.deadlands.block;

import com.deadlands.registry.DLTab;
import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Furniture and roadside fixtures: shape (front facing north, in block pixels), how the block is
 * oriented, and what it is made of. Models and textures come from tools/assetgen/gen_decor.py;
 * keep the shapes there in step with the ones here.
 */
public enum DecorKind implements StringRepresentable {
	// Roadside and street fixtures.
	UTILITY_POLE(Placement.POST, Material.WOOD, DLTab.BUILDING_MATERIALS, box(5, 0, 5, 11, 16, 11)),
	UTILITY_POLE_CROSSARM(Placement.AXIS, Material.WOOD, DLTab.BUILDING_MATERIALS, box(5, 0, 5, 11, 16, 11), box(0, 11, 6.5, 16, 13, 9.5)),
	STREET_LAMP(Placement.POST, Material.METAL, DLTab.BUILDING_MATERIALS, box(6.5, 0, 6.5, 9.5, 16, 9.5)),
	STREET_LAMP_HEAD(Placement.LAMP, Material.METAL, DLTab.BUILDING_MATERIALS,
		box(6.5, 0, 6.5, 9.5, 10, 9.5), box(6.5, 8, 2, 9.5, 10, 9.5), box(4.5, 5, 0, 11.5, 8, 6)),
	GUARDRAIL(Placement.FACING, Material.METAL, DLTab.BUILDING_MATERIALS, box(0, 0, 12, 16, 9, 15)),
	CONCRETE_BARRIER(Placement.FACING, Material.STONE, DLTab.BUILDING_MATERIALS,
		box(0, 0, 3, 16, 4, 13), box(0, 4, 5, 16, 10, 11), box(0, 10, 6, 16, 13, 10)),
	// Milestone 3, second half: traffic and road signs, infrastructure, school and library furniture.
	TRAFFIC_LIGHT(Placement.FACING, Material.METAL, DLTab.BUILDING_MATERIALS, box(6.5, 0, 6.5, 9.5, 16, 9.5), box(5, 4, 2.5, 11, 15, 6.5)),
	ROAD_SIGN_WARNING(Placement.FACING, Material.METAL, DLTab.BUILDING_MATERIALS, box(7, 0, 8, 9, 16, 10), box(1, 3, 7, 15, 16, 8)),
	ROAD_SIGN_SPEED(Placement.FACING, Material.METAL, DLTab.BUILDING_MATERIALS, box(7, 0, 8, 9, 16, 10), box(2, 3, 7, 14, 16, 8)),
	ROAD_SIGN_ROUTE(Placement.FACING, Material.METAL, DLTab.BUILDING_MATERIALS, box(7, 0, 8, 9, 16, 10), box(0, 6, 7, 16, 16, 8)),
	TRANSFORMER(Placement.FACING, Material.METAL, DLTab.BUILDING_MATERIALS, box(2, 0, 3, 14, 13, 13)),
	POWER_GENERATOR(Placement.GENERATOR, Material.METAL, DLTab.BUILDING_MATERIALS, box(1, 0, 2, 15, 14, 14)),
	/** A fuel pump. Some still hold fuel (see FuelPumpBlock). */
	FUEL_PUMP(Placement.PUMP, Material.METAL, DLTab.BUILDING_MATERIALS, box(2, 0, 5, 14, 16, 11), box(0, 0, 4, 16, 1, 12)),
	WORKBENCH(Placement.WORKBENCH, Material.WOOD, DLTab.FURNITURE, box(0, 12, 0, 16, 16, 16), box(1, 0, 1, 15, 12, 15)),
	// Furniture.
	TABLE(Placement.FACING, Material.WOOD, DLTab.FURNITURE, box(0, 13, 0, 16, 16, 16),
		box(1, 0, 1, 3, 13, 3), box(13, 0, 1, 15, 13, 3), box(1, 0, 13, 3, 13, 15), box(13, 0, 13, 15, 13, 15)),
	CHAIR(Placement.FACING, Material.WOOD, DLTab.FURNITURE, box(3, 7, 3, 13, 9, 13), box(3, 9, 11, 13, 16, 13),
		box(3, 0, 3, 5, 7, 5), box(11, 0, 3, 13, 7, 5), box(3, 0, 11, 5, 7, 13), box(11, 0, 11, 13, 7, 13)),
	COUNTER(Placement.FACING, Material.WOOD, DLTab.FURNITURE, box(0, 0, 1, 16, 14, 16), box(0, 14, 0, 16, 16, 16)),
	STORE_SHELF(Placement.FACING, Material.METAL, DLTab.FURNITURE, box(0, 0, 13, 16, 16, 16),
		box(0, 1, 2, 16, 2, 13), box(0, 6, 2, 16, 7, 13), box(0, 11, 2, 16, 12, 13), box(0, 0, 2, 1, 16, 13), box(15, 0, 2, 16, 16, 13)),
	BED_FRAME(Placement.FACING, Material.FABRIC, DLTab.FURNITURE, box(0, 0, 0, 16, 9, 16)),
	COUCH(Placement.FACING, Material.FABRIC, DLTab.FURNITURE, box(0, 0, 2, 16, 8, 16), box(0, 8, 12, 16, 15, 16),
		box(0, 8, 2, 2, 11, 12), box(14, 8, 2, 16, 11, 12)),
	DESK(Placement.FACING, Material.WOOD, DLTab.FURNITURE, box(0, 14, 0, 16, 16, 16), box(10, 0, 1, 15, 14, 15), box(1, 0, 1, 3, 14, 15)),
	STOVE(Placement.FACING, Material.METAL, DLTab.FURNITURE, box(1, 0, 1, 15, 15, 15), box(1, 15, 13, 15, 16, 15)),
	SINK(Placement.FACING, Material.CERAMIC, DLTab.FURNITURE, box(0, 0, 1, 16, 14, 16), box(0, 14, 0, 16, 16, 16)),
	TOILET(Placement.FACING, Material.CERAMIC, DLTab.FURNITURE, box(5, 0, 5, 11, 6, 13), box(4, 6, 3, 12, 10, 13), box(4, 6, 12, 12, 16, 16)),
	BOOKSHELF(Placement.FACING, Material.WOOD, DLTab.FURNITURE, box(0, 0, 8, 16, 16, 16)),
	WHITEBOARD(Placement.FACING, Material.METAL, DLTab.FURNITURE, box(0, 3, 15, 16, 14, 16));

	/** How the block turns when placed. */
	public enum Placement {
		/** Faces the player who places it (furniture, rails, barriers). */
		FACING,
		/** Symmetric upright post. */
		POST,
		/** Lies along the horizontal axis the player faces (crossarms). */
		AXIS,
		/** Faces the player and lights up when its town has power (street lamps). */
		LAMP,
		/** Faces the player; can be started with a fuse (power generators). */
		GENERATOR,
		/** Faces the player; opens a crafting grid. */
		WORKBENCH,
		/** Faces the player; holds fuel for fuel cans. */
		PUMP
	}

	/** What it is made of: hardness, sound, colour on maps, and which tool mines it. */
	public enum Material {
		WOOD(2.0F, SoundType.WOOD, MapColor.WOOD, "axe", false),
		METAL(3.0F, SoundType.METAL, MapColor.METAL, "pickaxe", true),
		STONE(2.5F, SoundType.STONE, MapColor.STONE, "pickaxe", true),
		FABRIC(0.8F, SoundType.WOOL, MapColor.COLOR_BROWN, "axe", false),
		CERAMIC(1.5F, SoundType.STONE, MapColor.QUARTZ, "pickaxe", true);

		public final float hardness;
		public final SoundType sound;
		public final MapColor mapColor;
		public final String tool;
		public final boolean needsTool;

		Material(float hardness, SoundType sound, MapColor mapColor, String tool, boolean needsTool) {
			this.hardness = hardness;
			this.sound = sound;
			this.mapColor = mapColor;
			this.tool = tool;
			this.needsTool = needsTool;
		}
	}

	public static final Codec<DecorKind> CODEC = StringRepresentable.fromEnum(DecorKind::values);

	private final Placement placement;
	private final Material material;
	private final DLTab tab;
	private final VoxelShape northShape;

	DecorKind(Placement placement, Material material, DLTab tab, VoxelShape... boxes) {
		this.placement = placement;
		this.material = material;
		this.tab = tab;
		VoxelShape shape = Shapes.empty();
		for (VoxelShape box : boxes) {
			shape = Shapes.or(shape, box);
		}
		this.northShape = shape;
	}

	private static VoxelShape box(double x1, double y1, double z1, double x2, double y2, double z2) {
		return Block.box(x1, y1, z1, x2, y2, z2);
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	@Override
	public String getSerializedName() {
		return id();
	}

	public Placement placement() {
		return placement;
	}

	public Material material() {
		return material;
	}

	public DLTab tab() {
		return tab;
	}

	public VoxelShape northShape() {
		return northShape;
	}
}
