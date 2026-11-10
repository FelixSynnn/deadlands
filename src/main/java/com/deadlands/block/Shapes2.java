package com.deadlands.block;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Turning block shapes authored facing north. */
public final class Shapes2 {
	private Shapes2() {
	}

	/** The shape turned from facing north to facing {@code to} (clockwise seen from above). */
	public static VoxelShape rotate(VoxelShape north, Direction to) {
		VoxelShape[] result = {Shapes.empty()};
		int turns = (to.get2DDataValue() - Direction.NORTH.get2DDataValue() + 4) % 4;
		north.forAllBoxes((x1, y1, z1, x2, y2, z2) -> {
			double ax1 = x1, az1 = z1, ax2 = x2, az2 = z2;
			for (int i = 0; i < turns; i++) {
				// 90 degrees clockwise seen from above: (x, z) -> (1 - z, x)
				double nx1 = 1 - az2, nz1 = ax1, nx2 = 1 - az1, nz2 = ax2;
				ax1 = nx1;
				az1 = nz1;
				ax2 = nx2;
				az2 = nz2;
			}
			result[0] = Shapes.or(result[0], Shapes.box(ax1, y1, az1, ax2, y2, az2));
		});
		return result[0];
	}
}
